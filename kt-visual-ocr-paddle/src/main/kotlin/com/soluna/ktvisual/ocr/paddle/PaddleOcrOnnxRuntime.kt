package com.soluna.ktvisual.ocr.paddle

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.soluna.ktvisual.cv.MatConverters
import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.Region
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Preferred in-JVM PaddleOCR runtime based on ONNX Runtime.
 *
 * This runtime executes converted PaddleOCR detection and recognition ONNX
 * models directly inside the JVM. It avoids requiring automation projects to
 * install Python, PaddlePaddle, or native Paddle inference packages.
 *
 * Expected model layout per [ResolvedPaddleOcrModel]:
 * - detector directory contains [modelFileName], usually `model.onnx` for
 *   converted local models or `inference.onnx` for official PaddleOCR ONNX
 *   packages;
 * - recognizer directory contains [modelFileName];
 * - dictionary path contains either one character per line or a PaddleOCR
 *   `inference.yml` file with `PostProcess.character_dict`.
 *
 * The implementation targets the common PaddleOCR DB detector + CTC recognizer
 * pipeline used by PP-OCR models. Model variants with different output
 * semantics should use a custom [PaddleOcrRuntime].
 */
class PaddleOcrOnnxRuntime(
    private val modelFileName: String = "inference.onnx",
    private val detInputLimit: Int = 1536,
    private val detThreshold: Float = 0.30f,
    private val minBoxArea: Int = 16,
    private val recImageHeight: Int = 48,
    private val recImageWidth: Int = 320,
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
) : PaddleOcrRuntime {

    private val sessions = mutableMapOf<Path, OrtSession>()
    private val dictionaries = mutableMapOf<Path, List<String>>()

    init {
        require(modelFileName.isNotBlank()) { "modelFileName must not be blank." }
        require(detInputLimit >= 32) { "detInputLimit must be >= 32." }
        require(detThreshold in 0.0f..1.0f) { "detThreshold must be between 0.0 and 1.0." }
        require(minBoxArea > 0) { "minBoxArea must be > 0." }
        require(recImageHeight > 0) { "recImageHeight must be > 0." }
        require(recImageWidth > 0) { "recImageWidth must be > 0." }
    }

    override fun recognize(request: PaddleOcrRequest): List<OcrText> {
        val source = MatConverters.fromBufferedImage(request.image)
        return try {
            val boxes = textBoxesForRecognition(source, request.model.detectorDirectory.resolve(modelFileName))
            if (boxes.isEmpty()) return emptyList()

            val recSession = session(request.model.recognizerDirectory.resolve(modelFileName))
            val dictionary = dictionary(request.model.dictionaryPath)
            boxes.mapNotNull { box ->
                recognizeBox(source, box, recSession, dictionary)
            }.sortedWith(compareBy<OcrText> { it.bounds.y }.thenBy { it.bounds.x })
        } finally {
            source.release()
        }
    }

    override fun close() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        dictionaries.clear()
    }

    private fun detectTextBoxes(source: Mat, modelPath: Path): List<Region> {
        val session = session(modelPath)
        val input = detectionInput(source)
        val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.data), input.shape)
        try {
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val probability = firstFloat2D(result.get(0).value)
                return probabilityToRegions(probability, source.cols(), source.rows())
            }
        } finally {
            tensor.close()
        }
    }

    private fun textBoxesForRecognition(source: Mat, modelPath: Path): List<Region> {
        val detected = detectTextBoxes(source, modelPath).toMutableList()
        if (source.cols() <= roiLineFallbackMaxWidth && source.rows() <= roiLineFallbackMaxHeight) {
            detected += Region(0, 0, source.cols(), source.rows())
        }
        return detected.distinct()
    }

    private fun recognizeBox(
        source: Mat,
        box: Region,
        session: OrtSession,
        dictionary: List<String>
    ): OcrText? {
        val safeBox = clamp(expandForRecognition(box), source.cols(), source.rows())
        val crop = Mat(source, Rect(safeBox.x, safeBox.y, safeBox.width, safeBox.height))
        val input = recognitionInput(crop)
        val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.data), input.shape)
        return try {
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val logits = firstFloat2D(result.get(0).value)
                val decoded = ctcDecode(logits, dictionary)
                if (decoded.text.isBlank()) {
                    null
                } else {
                    OcrText(decoded.text, safeBox, decoded.confidence)
                }
            }
        } finally {
            tensor.close()
            crop.release()
        }
    }

    private fun detectionInput(source: Mat): TensorInput {
        val scale = if (max(source.cols(), source.rows()) > detInputLimit) {
            detInputLimit.toDouble() / max(source.cols(), source.rows()).toDouble()
        } else {
            1.0
        }
        val width = roundToMultipleOf32((source.cols() * scale).toInt().coerceAtLeast(32))
        val height = roundToMultipleOf32((source.rows() * scale).toInt().coerceAtLeast(32))
        val resized = Mat()
        try {
            Imgproc.resize(source, resized, Size(width.toDouble(), height.toDouble()))
            return chwInput(resized, width, height, DetNormalize)
        } finally {
            resized.release()
        }
    }

    private fun recognitionInput(crop: Mat): TensorInput {
        val scale = min(
            recImageWidth.toDouble() / crop.cols().toDouble(),
            recImageHeight.toDouble() / crop.rows().toDouble()
        )
        val width = ceil(crop.cols() * scale).toInt().coerceIn(1, recImageWidth)
        val resized = Mat()
        val canvas = Mat.zeros(recImageHeight, recImageWidth, CvType.CV_8UC3)
        return try {
            Imgproc.resize(crop, resized, Size(width.toDouble(), recImageHeight.toDouble()))
            val target = Mat(canvas, Rect(0, 0, width, recImageHeight))
            try {
                resized.copyTo(target)
            } finally {
                target.release()
            }
            chwInput(canvas, recImageWidth, recImageHeight, RecNormalize)
        } finally {
            resized.release()
            canvas.release()
        }
    }

    private fun chwInput(image: Mat, width: Int, height: Int, normalize: Normalize): TensorInput {
        val data = FloatArray(3 * width * height)
        val channelSize = width * height
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = image.get(y, x)
                val blue = pixel[0].toFloat()
                val green = pixel[1].toFloat()
                val red = pixel[2].toFloat()
                val index = y * width + x
                data[index] = normalize.red(red)
                data[channelSize + index] = normalize.green(green)
                data[channelSize * 2 + index] = normalize.blue(blue)
            }
        }
        return TensorInput(data, longArrayOf(1, 3, height.toLong(), width.toLong()))
    }

    private fun probabilityToRegions(probability: Array<FloatArray>, imageWidth: Int, imageHeight: Int): List<Region> {
        val rows = probability.size
        if (rows == 0) return emptyList()
        val cols = probability.first().size
        val mask = Mat(rows, cols, CvType.CV_8UC1)
        val contours = mutableListOf<MatOfPoint>()
        return try {
            val bytes = ByteArray(rows * cols)
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    bytes[y * cols + x] = if (probability[y][x] >= detThreshold) 255.toByte() else 0.toByte()
                }
            }
            mask.put(0, 0, bytes)
            Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val scaleX = imageWidth.toDouble() / cols.toDouble()
            val scaleY = imageHeight.toDouble() / rows.toDouble()
            contours
                .map { Imgproc.boundingRect(it) }
                .filter { it.width * it.height >= minBoxArea }
                .map {
                    Region(
                        x = (it.x * scaleX).toInt().coerceIn(0, imageWidth - 1),
                        y = (it.y * scaleY).toInt().coerceIn(0, imageHeight - 1),
                        width = ceil(it.width * scaleX).toInt().coerceAtLeast(1),
                        height = ceil(it.height * scaleY).toInt().coerceAtLeast(1)
                    )
                }
                .map { clamp(it, imageWidth, imageHeight) }
                .sortedWith(compareBy<Region> { it.y }.thenBy { it.x })
        } finally {
            mask.release()
            contours.forEach { it.release() }
        }
    }

    private fun ctcDecode(logits: Array<FloatArray>, dictionary: List<String>): DecodedText {
        val builder = StringBuilder()
        val confidences = mutableListOf<Double>()
        var previous = -1
        for (step in logits) {
            var bestIndex = 0
            var bestScore = Float.NEGATIVE_INFINITY
            for (i in step.indices) {
                if (step[i] > bestScore) {
                    bestIndex = i
                    bestScore = step[i]
                }
            }
            if (bestIndex != 0 && bestIndex != previous) {
                val char = dictionary.getOrNull(bestIndex - 1)
                if (char != null) {
                    builder.append(char)
                    confidences += bestScore.toDouble()
                }
            }
            previous = bestIndex
        }
        val confidence = if (confidences.isEmpty()) null else confidences.average().coerceIn(0.0, 1.0)
        return DecodedText(builder.toString(), confidence)
    }

    private fun session(modelPath: Path): OrtSession {
        require(Files.exists(modelPath)) { "ONNX model not found: $modelPath" }
        return sessions.getOrPut(modelPath.toAbsolutePath().normalize()) {
            environment.createSession(modelPath.toAbsolutePath().toString(), OrtSession.SessionOptions())
        }
    }

    private fun dictionary(path: Path): List<String> {
        require(Files.exists(path)) { "OCR dictionary not found: $path" }
        return dictionaries.getOrPut(path.toAbsolutePath().normalize()) {
            PaddleOcrDictionaryReader.read(path)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun firstFloat2D(value: Any?): Array<FloatArray> {
        require(value != null) { "ONNX output is null." }
        if (value is Array<*> && value.isNotEmpty() && value[0] is FloatArray) {
            return value as Array<FloatArray>
        }
        if (value is Array<*> && value.isNotEmpty()) {
            return firstFloat2D(value[0])
        }
        throw IllegalStateException("Unsupported ONNX output shape: ${value::class.qualifiedName}")
    }

    private fun clamp(region: Region, width: Int, height: Int): Region {
        val x = region.x.coerceIn(0, width - 1)
        val y = region.y.coerceIn(0, height - 1)
        val right = (region.x + region.width).coerceIn(x + 1, width)
        val bottom = (region.y + region.height).coerceIn(y + 1, height)
        return Region(x, y, right - x, bottom - y)
    }

    private fun expandForRecognition(region: Region): Region {
        val horizontal = max(2, ceil(region.width * 0.10).toInt())
        val vertical = max(2, ceil(region.height * 0.20).toInt())
        return Region(
            x = region.x - horizontal,
            y = region.y - vertical,
            width = region.width + horizontal * 2,
            height = region.height + vertical * 2
        )
    }

    private fun roundToMultipleOf32(value: Int): Int {
        return ((value + 31) / 32 * 32).coerceAtLeast(32)
    }

    private data class TensorInput(
        val data: FloatArray,
        val shape: LongArray
    )

    private data class DecodedText(
        val text: String,
        val confidence: Double?
    )

    private companion object {
        private const val roiLineFallbackMaxWidth = 800
        private const val roiLineFallbackMaxHeight = 180
    }

    private interface Normalize {
        fun red(value: Float): Float
        fun green(value: Float): Float
        fun blue(value: Float): Float
    }

    private object DetNormalize : Normalize {
        override fun red(value: Float): Float = ((value / 255.0f) - 0.485f) / 0.229f
        override fun green(value: Float): Float = ((value / 255.0f) - 0.456f) / 0.224f
        override fun blue(value: Float): Float = ((value / 255.0f) - 0.406f) / 0.225f
    }

    private object RecNormalize : Normalize {
        override fun red(value: Float): Float = ((value / 255.0f) - 0.5f) / 0.5f
        override fun green(value: Float): Float = ((value / 255.0f) - 0.5f) / 0.5f
        override fun blue(value: Float): Float = ((value / 255.0f) - 0.5f) / 0.5f
    }
}

internal object PaddleOcrDictionaryReader {

    fun read(path: Path): List<String> {
        val lines = Files.readAllLines(path)
        return if (path.fileName.toString().endsWith(".yml") || path.fileName.toString().endsWith(".yaml")) {
            fromInferenceYaml(lines)
        } else {
            lines.filter { it.isNotEmpty() }
        }
    }

    private fun fromInferenceYaml(lines: List<String>): List<String> {
        val chars = mutableListOf<String>()
        var inDictionary = false
        for (line in lines) {
            val trimmed = line.trim()
            if (!inDictionary) {
                inDictionary = trimmed == "character_dict:"
                continue
            }
            val listMarker = line.indexOf("- ")
            if (listMarker >= 0 && line.substring(0, listMarker).isBlank()) {
                chars += yamlScalar(line.substring(listMarker + 2))
                continue
            }
            if (trimmed.isNotEmpty()) {
                break
            }
            if (trimmed.isEmpty()) {
                continue
            }
        }
        require(chars.isNotEmpty()) { "PaddleOCR inference.yml does not contain PostProcess.character_dict." }
        return chars
    }

    private fun yamlScalar(value: String): String {
        if (value.length >= 2 && value.first() == '\'' && value.last() == '\'') {
            return value.substring(1, value.lastIndex).replace("''", "'")
        }
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return value.substring(1, value.lastIndex)
        }
        return value
    }
}
