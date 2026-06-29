package com.soluna.ktvisual.ocr.multimodal

import com.soluna.ktvisual.model.Point2
import com.soluna.ktvisual.model.Region

/**
 * A text region selected by a multimodal model for a semantic target.
 *
 * [target] is the caller-provided semantic description, for example
 * `"the settings entry for language"` or `"the visible text that means back"`.
 * [text] is the exact visible text returned by the model. [bounds] and
 * [center] are restored to the full screenshot coordinate system by
 * [MultimodalOcrEngine.locateTextInImagePart].
 */
data class MultimodalTextLocation(
    val target: String,
    val text: String,
    val bounds: Region,
    val confidence: Double? = null
) {
    val center: Point2
        get() = bounds.center
}

/**
 * Options for semantic text location through
 * [MultimodalOcrEngine.locateTextInImagePart].
 *
 * Callers provide an element/crop screenshot plus the current full screenshot.
 * The library locates the crop inside the full screenshot locally, sends only
 * the crop to the multimodal model, and restores the model bbox to full
 * screenshot coordinates. [partMatchThreshold] controls the local exact-scale
 * crop/full-image match. Scaling between the crop and full screenshot is not
 * supported by this API.
 */
data class MultimodalTextLocationOptions(
    val minConfidence: Double? = null,
    val requireConfidence: Boolean = false,
    val partMatchThreshold: Double = 0.98,
    val partMatchGrayscale: Boolean = false
) {
    init {
        require(minConfidence == null || minConfidence in 0.0..1.0) {
            "minConfidence must be null or between 0.0 and 1.0."
        }
        require(partMatchThreshold in 0.0..1.0) { "partMatchThreshold must be between 0.0 and 1.0." }
    }
}
