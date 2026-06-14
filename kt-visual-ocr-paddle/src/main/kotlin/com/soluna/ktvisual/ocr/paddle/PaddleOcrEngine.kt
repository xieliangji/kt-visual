package com.soluna.ktvisual.ocr.paddle

import com.soluna.ktvisual.api.OcrEngine
import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.Region
import java.awt.Graphics2D
import java.awt.image.BufferedImage

/**
 * Paddle OCR implementation of the core [OcrEngine] interface.
 *
 * The engine is optimized for UI automation usage: callers choose languages,
 * the engine routes them to a small set of model groups, and results are
 * returned as screenshot-coordinate [OcrText] values that can drive
 * `UiVision.clickText` and related actions.
 */
class PaddleOcrEngine(
    private val languages: Set<OcrLanguage>,
    private val runtime: PaddleOcrRuntime = UnavailablePaddleOcrRuntime,
    private val resourceResolver: PaddleOcrResourceResolver = PaddleOcrResourceManager()
) : OcrEngine, AutoCloseable {

    init {
        require(languages.isNotEmpty()) { "languages must not be empty." }
    }

    /**
     * Recognizes text using the engine's configured language set.
     */
    override fun recognize(image: BufferedImage, roi: Region?): List<OcrText> {
        return recognize(image, languages, roi)
    }

    /**
     * Recognizes text using a per-call language subset.
     */
    fun recognize(
        image: BufferedImage,
        languages: Set<OcrLanguage>,
        roi: Region? = null
    ): List<OcrText> {
        require(languages.isNotEmpty()) { "languages must not be empty." }
        require(this.languages.containsAll(languages)) {
            "Requested languages $languages are not enabled for this engine."
        }

        val cropped = if (roi == null) image else crop(image, roi)
        return try {
            PaddleOcrModelCatalog.route(languages)
                .flatMap { spec ->
                    val routedLanguages = spec.supportedLanguages.intersect(languages)
                    if (routedLanguages.isEmpty()) {
                        emptyList()
                    } else {
                        runtime.recognize(
                            PaddleOcrRequest(
                                image = cropped,
                                model = resourceResolver.resolve(spec),
                                languages = routedLanguages
                            )
                        )
                    }
                }
                .map { text -> if (roi == null) text else text.copy(bounds = text.bounds.offset(roi.x, roi.y)) }
                .sortedWith(compareBy<OcrText> { it.bounds.y }.thenBy { it.bounds.x })
        } finally {
            if (roi != null) {
                cropped.flush()
            }
        }
    }

    override fun close() {
        runtime.close()
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

    companion object {
        /**
         * Creates an engine for the 13-language profile requested by app UI automation.
         */
        fun multilingual13(
            runtime: PaddleOcrRuntime = UnavailablePaddleOcrRuntime,
            resourceResolver: PaddleOcrResourceResolver = PaddleOcrResourceManager()
        ): PaddleOcrEngine {
            return PaddleOcrEngine(OcrLanguage.MULTILINGUAL_13, runtime, resourceResolver)
        }
    }
}
