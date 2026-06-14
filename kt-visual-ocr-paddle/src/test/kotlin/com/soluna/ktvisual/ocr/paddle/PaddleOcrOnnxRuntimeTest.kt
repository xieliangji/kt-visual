package com.soluna.ktvisual.ocr.paddle

import com.soluna.ktvisual.model.OcrText
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PaddleOcrOnnxRuntimeTest {

    @Test
    fun `recognize fails clearly when detector model is missing`() {
        val root = Files.createTempDirectory("kt-visual-onnx-runtime-test")
        val detector = Files.createDirectories(root.resolve("det"))
        val recognizer = Files.createDirectories(root.resolve("rec"))
        Files.writeString(root.resolve("dict.txt"), "A\nB\n")
        Files.writeString(recognizer.resolve("model.onnx"), "not a real onnx model")

        val error = assertFailsWith<IllegalArgumentException> {
            PaddleOcrOnnxRuntime().recognize(
                PaddleOcrRequest(
                    image = BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR),
                    model = ResolvedPaddleOcrModel(
                        spec = PaddleOcrModelCatalog.cjkAndEnglish,
                        detectorDirectory = detector,
                        recognizerDirectory = recognizer,
                        dictionaryPath = root.resolve("dict.txt")
                    ),
                    languages = setOf(OcrLanguage.ENGLISH)
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("ONNX model not found"))
    }

    @Test
    fun `recognize reads official PaddleOCR inference yaml dictionary`() {
        val root = Files.createTempDirectory("kt-visual-onnx-yaml-test")
        val yaml = root.resolve("inference.yml")
        Files.writeString(
            yaml,
            """
            PostProcess:
              name: CTCLabelDecode
              character_dict:
              - A
              - ''''
              - 　
              - 登录
              - 通
            """.trimIndent()
        )

        assertEquals(listOf("A", "'", "　", "登录", "通"), PaddleOcrDictionaryReader.read(yaml))
    }

    @Test
    fun `recognize official ppocrv6 medium onnx model`() {
        if (System.getenv("KT_VISUAL_RUN_REAL_OCR") != "true") {
            return
        }

        val model = ensureOfficialModel("medium")
        val runtime = PaddleOcrOnnxRuntime(modelFileName = "inference.onnx")
        runtime.use {
            val results = it.recognize(
                PaddleOcrRequest(
                    image = sampleUiImage(),
                    model = model,
                    languages = setOf(OcrLanguage.SIMPLIFIED_CHINESE, OcrLanguage.ENGLISH)
                )
            )

            assertRecognized(results, "Login")
        }
    }

    private fun ensureOfficialModel(tier: String): ResolvedPaddleOcrModel {
        val root = Path.of("build", "real-ocr-models", "ppocrv6-$tier").toAbsolutePath().normalize()
        val detector = Files.createDirectories(root.resolve("det"))
        val recognizer = Files.createDirectories(root.resolve("rec"))
        val dictionary = root.resolve("inference.yml")
        downloadIfMissing(
            "https://huggingface.co/PaddlePaddle/PP-OCRv6_${tier}_det_onnx/resolve/main/inference.onnx",
            detector.resolve("inference.onnx")
        )
        downloadIfMissing(
            "https://huggingface.co/PaddlePaddle/PP-OCRv6_${tier}_rec_onnx/resolve/main/inference.onnx",
            recognizer.resolve("inference.onnx")
        )
        downloadIfMissing(
            "https://huggingface.co/PaddlePaddle/PP-OCRv6_${tier}_rec_onnx/raw/main/inference.yml",
            dictionary
        )
        return ResolvedPaddleOcrModel(
            spec = PaddleOcrModelCatalog.cjkAndEnglish,
            detectorDirectory = detector,
            recognizerDirectory = recognizer,
            dictionaryPath = dictionary
        )
    }

    private fun downloadIfMissing(url: String, target: Path) {
        if (Files.exists(target) && Files.size(target) > 0) return

        Files.createDirectories(target.parent)
        URI.create(url).toURL().openStream().use { input ->
            Files.newOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun sampleUiImage(): BufferedImage {
        val image = BufferedImage(420, 180, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.color = Color(32, 38, 46)
            graphics.font = Font("Arial", Font.BOLD, 40)
            graphics.drawString("Login", 48, 72)
            graphics.font = Font("Arial", Font.PLAIN, 32)
            graphics.drawString("OK", 52, 132)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(image, "png", Files.createDirectories(Path.of("build", "test-images")).resolve("ppocrv6-ui.png").toFile())
        return image
    }

    private fun assertRecognized(results: List<OcrText>, text: String) {
        val recognized = results.joinToString(" ") { it.text }
        assertTrue(
            recognized.contains(text, ignoreCase = true),
            "Expected OCR results to contain '$text', actual results: $results"
        )
        assertTrue(results.any { it.bounds.width > 0 && it.bounds.height > 0 })
    }
}
