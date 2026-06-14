package com.soluna.ktvisual.ocr.multimodal

import com.soluna.ktvisual.api.OcrEngine
import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.Region
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * OCR engine backed by a multimodal model.
 *
 * The model client can point to any cloud or private gateway. It only needs to
 * return text containing the JSON schema described by [MultimodalOcrOptions.prompt].
 * Transient client failures and malformed model responses are retried according
 * to [MultimodalOcrOptions.retry] before optional fallback is used.
 */
class MultimodalOcrEngine(
    private val client: MultimodalOcrClient,
    private val options: MultimodalOcrOptions = MultimodalOcrOptions(),
    private val fallback: OcrEngine? = null
) : OcrEngine {

    override fun recognize(image: BufferedImage, roi: Region?): List<OcrText> {
        val activeImage = if (roi == null) image else crop(image, roi)
        return try {
            val results = tryRecognizeWithClient(activeImage, roi)
            if (results.isEmpty() && options.useFallbackWhenEmpty) {
                fallback?.recognize(image, roi) ?: results
            } else {
                results
            }
        } catch (error: RuntimeException) {
            if (options.useFallbackOnClientError) {
                fallback?.recognize(image, roi) ?: throw error
            } else {
                throw error
            }
        } finally {
            if (roi != null) {
                activeImage.flush()
            }
        }
    }

    private fun tryRecognizeWithClient(image: BufferedImage, roi: Region?): List<OcrText> {
        var attempt = 1
        var delay = options.retry.initialDelay
        var lastError: RuntimeException? = null

        while (attempt <= options.retry.maxAttempts) {
            try {
                val result = recognizeOnce(image, roi)
                if (result.isNotEmpty() || !options.retry.retryOnEmptyResult || attempt == options.retry.maxAttempts) {
                    return result
                }
                lastError = MultimodalOcrException("Multimodal OCR returned no text.")
            } catch (error: RuntimeException) {
                lastError = error
                if (attempt == options.retry.maxAttempts) {
                    throw error
                }
            }

            sleepBeforeRetry(delay)
            delay = nextDelay(delay, options.retry)
            attempt += 1
        }

        throw lastError ?: MultimodalOcrException("Multimodal OCR failed.")
    }

    private fun recognizeOnce(image: BufferedImage, roi: Region?): List<OcrText> {
        val response = client.complete(
            MultimodalOcrRequest(
                imageBytes = encode(image, options.mimeType),
                width = image.width,
                height = image.height,
                mimeType = options.mimeType,
                prompt = options.prompt
            )
        )

        return MultimodalOcrJsonParser.parse(response, image.width, image.height)
            .asSequence()
            .filter { text ->
                val confidence = text.confidence
                when {
                    options.requireConfidence && confidence == null -> false
                    options.minConfidence == null || confidence == null -> true
                    else -> confidence >= options.minConfidence
                }
            }
            .map { text -> if (roi == null) text else text.copy(bounds = text.bounds.offset(roi.x, roi.y)) }
            .sortedWith(compareBy<OcrText> { it.bounds.y }.thenBy { it.bounds.x })
            .toList()
    }

    private fun sleepBeforeRetry(delay: Duration) {
        if (delay.isZero) return
        try {
            Thread.sleep(delay.toMillis())
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw MultimodalOcrException("Interrupted while waiting to retry multimodal OCR.", error)
        }
    }

    private fun nextDelay(current: Duration, retry: MultimodalOcrRetryOptions): Duration {
        if (current.isZero || retry.maxDelay.isZero) return Duration.ZERO
        val nextMillis = (current.toMillis() * retry.backoffMultiplier).roundToLong()
        return Duration.ofMillis(min(nextMillis, retry.maxDelay.toMillis()))
    }

    private fun encode(image: BufferedImage, mimeType: String): ByteArray {
        val format = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            else -> throw MultimodalOcrException("Unsupported OCR image mime type: $mimeType")
        }
        val output = ByteArrayOutputStream()
        if (!ImageIO.write(image, format, output)) {
            throw MultimodalOcrException("Failed to encode OCR image as $format.")
        }
        return output.toByteArray()
    }

    private fun crop(image: BufferedImage, roi: Region): BufferedImage {
        require(roi.x >= 0) { "roi.x must be >= 0." }
        require(roi.y >= 0) { "roi.y must be >= 0." }
        require(roi.x + roi.width <= image.width) { "roi exceeds image width." }
        require(roi.y + roi.height <= image.height) { "roi exceeds image height." }

        val output = BufferedImage(roi.width, roi.height, BufferedImage.TYPE_3BYTE_BGR)
        val graphics: Graphics2D = output.createGraphics()
        try {
            graphics.drawImage(
                image,
                0,
                0,
                roi.width,
                roi.height,
                roi.x,
                roi.y,
                roi.x + roi.width,
                roi.y + roi.height,
                null
            )
        } finally {
            graphics.dispose()
        }
        return output
    }
}

class MultimodalOcrException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
