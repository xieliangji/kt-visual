package com.soluna.ktvisual.model

/**
 * Text matching mode used when selecting OCR results.
 */
enum class OcrTextMatchMode {
    /**
     * The OCR text must equal the query after optional normalization.
     */
    EXACT,

    /**
     * The OCR text must contain the query after optional normalization.
     */
    CONTAINS,

    /**
     * The query is interpreted as a Kotlin regular expression.
     */
    REGEX
}

/**
 * Options used when selecting OCR text for assertions or actions.
 *
 * [roi] limits OCR and matching to a screen region. [minConfidence] filters out
 * low-confidence OCR results when the OCR engine provides confidence scores.
 */
data class OcrTextMatchOptions(
    val mode: OcrTextMatchMode = OcrTextMatchMode.CONTAINS,
    val ignoreCase: Boolean = true,
    val normalizeWhitespace: Boolean = true,
    val minConfidence: Double? = null,
    val roi: Region? = null
) {
    init {
        require(minConfidence == null || minConfidence in 0.0..1.0) {
            "minConfidence must be null or between 0.0 and 1.0."
        }
    }
}
