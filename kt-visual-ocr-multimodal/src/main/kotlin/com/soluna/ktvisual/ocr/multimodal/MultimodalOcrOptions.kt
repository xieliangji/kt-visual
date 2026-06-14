package com.soluna.ktvisual.ocr.multimodal

import java.time.Duration

/**
 * Runtime options for [MultimodalOcrEngine].
 *
 * [minConfidence] filters low-confidence model output when confidence is present.
 * Set [requireConfidence] to `true` when the caller wants to reject text entries
 * that omit confidence entirely. [retry] handles transient client failures and
 * malformed model responses before fallback behavior is evaluated.
 */
data class MultimodalOcrOptions(
    val prompt: String = DEFAULT_PROMPT,
    val minConfidence: Double? = null,
    val requireConfidence: Boolean = false,
    val useFallbackWhenEmpty: Boolean = true,
    val useFallbackOnClientError: Boolean = true,
    val mimeType: String = "image/png",
    val retry: MultimodalOcrRetryOptions = MultimodalOcrRetryOptions()
) {
    init {
        require(prompt.isNotBlank()) { "prompt must not be blank." }
        require(minConfidence == null || minConfidence in 0.0..1.0) {
            "minConfidence must be null or between 0.0 and 1.0."
        }
        require(mimeType.isNotBlank()) { "mimeType must not be blank." }
    }

    companion object {
        const val DEFAULT_PROMPT: String =
            "Extract only text that is directly visible in the image. Do not infer, guess, translate, normalize, " +
                "summarize, or complete text from context, icons, layout, app knowledge, or partially hidden content. " +
                "If text is clipped, blurred, too small, obscured, or uncertain, omit it instead of guessing. " +
                "Keep the exact visible characters and original language. Return only JSON with this shape: " +
                "{\"texts\":[{\"text\":\"Login\",\"confidence\":0.98," +
                "\"bounds\":{\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.05}}]}. " +
                "Bounds must tightly cover only the visible text pixels, normalized to the provided image, " +
                "using top-left x/y plus width/height. If no text is clearly visible, return {\"texts\":[]}."
    }
}

/**
 * Retry policy for multimodal OCR calls.
 *
 * Retries cover client exceptions and parse failures, including network
 * instability, gateway errors, empty SDK responses, and model output that does
 * not contain the requested JSON schema. Empty OCR results are only retried
 * when [retryOnEmptyResult] is enabled because an empty screen region can be a
 * valid result.
 */
data class MultimodalOcrRetryOptions(
    val maxAttempts: Int = 1,
    val initialDelay: Duration = Duration.ofMillis(250),
    val maxDelay: Duration = Duration.ofSeconds(2),
    val backoffMultiplier: Double = 2.0,
    val retryOnEmptyResult: Boolean = false
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1." }
        require(!initialDelay.isNegative) { "initialDelay must not be negative." }
        require(!maxDelay.isNegative) { "maxDelay must not be negative." }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0." }
    }
}
