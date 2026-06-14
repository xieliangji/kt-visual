package com.soluna.ktvisual.ocr.paddle

import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.Region
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Paddle OCR runtime backed by an external PaddleOCR command.
 *
 * This runtime is intended as the first practical integration path for desktop
 * and CI environments where the official PaddleOCR CLI or an internal wrapper
 * script is installed. The command must print one OCR result per line in JSONL:
 *
 * ```json
 * {"text":"Login","confidence":0.98,"bounds":{"x":10,"y":20,"width":80,"height":24}}
 * ```
 *
 * The process-based boundary keeps the core library independent from Python and
 * native Paddle installation details while still allowing teams to use the
 * official PaddleOCR runtime immediately.
 */
class PaddleOcrCliRuntime(
    private val command: List<String>,
    private val timeout: Duration = Duration.ofSeconds(30),
    private val workingDirectory: Path? = null
) : PaddleOcrRuntime {

    init {
        require(command.isNotEmpty()) { "command must not be empty." }
        require(!timeout.isNegative && !timeout.isZero) { "timeout must be > 0." }
    }

    override fun recognize(request: PaddleOcrRequest): List<OcrText> {
        val imagePath = Files.createTempFile("kt-visual-paddle-ocr-", ".png")
        try {
            require(ImageIO.write(request.image, "png", imagePath.toFile())) {
                "Failed to write temporary OCR image: $imagePath"
            }

            val process = ProcessBuilder(buildCommand(request, imagePath))
                .apply {
                    workingDirectory?.let { directory(it.toFile()) }
                    redirectErrorStream(true)
                }
                .start()

            val completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
            val output = process.inputStream.bufferedReader().readText()
            if (!completed) {
                process.destroyForcibly()
                throw PaddleOcrProcessException("Paddle OCR command timed out after ${timeout.toMillis()}ms.")
            }
            if (process.exitValue() != 0) {
                throw PaddleOcrProcessException(
                    "Paddle OCR command failed with exit=${process.exitValue()}.\n$output"
                )
            }

            return output
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("{") && it.endsWith("}") }
                .map { parseJsonLine(it) }
                .toList()
        } finally {
            Files.deleteIfExists(imagePath)
        }
    }

    private fun buildCommand(request: PaddleOcrRequest, imagePath: Path): List<String> {
        return command +
            listOf(
                "--image",
                imagePath.toAbsolutePath().toString(),
                "--model",
                request.model.spec.id,
                "--det",
                request.model.detectorDirectory.toAbsolutePath().toString(),
                "--rec",
                request.model.recognizerDirectory.toAbsolutePath().toString(),
                "--dict",
                request.model.dictionaryPath.toAbsolutePath().toString(),
                "--languages",
                request.languages.joinToString(",") { it.isoCode }
            )
    }

    private fun parseJsonLine(line: String): OcrText {
        val text = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(line)
            ?.groupValues
            ?.get(1)
            ?.replace("\\\"", "\"")
            ?: throw PaddleOcrProcessException("OCR JSONL result is missing text: $line")
        val confidence = Regex("\"confidence\"\\s*:\\s*([0-9.]+)").find(line)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
        val x = intField(line, "x")
        val y = intField(line, "y")
        val width = intField(line, "width")
        val height = intField(line, "height")
        return OcrText(
            text = text,
            bounds = Region(x, y, width, height),
            confidence = confidence
        )
    }

    private fun intField(line: String, name: String): Int {
        return Regex("\"$name\"\\s*:\\s*(-?\\d+)").find(line)
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?: throw PaddleOcrProcessException("OCR JSONL result is missing $name: $line")
    }
}

class PaddleOcrProcessException(message: String) : IllegalStateException(message)
