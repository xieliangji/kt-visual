package com.soluna.ktvisual.model

data class OcrText(
    val text: String,
    val bounds: Region,
    val confidence: Double? = null
) {
    init {
        require(confidence == null || confidence in 0.0..1.0) {
            "confidence must be null or between 0.0 and 1.0."
        }
    }
}
