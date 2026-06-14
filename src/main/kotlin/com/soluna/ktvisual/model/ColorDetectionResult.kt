package com.soluna.ktvisual.model

/**
 * Summary of color detection in a whole image or ROI.
 */
data class ColorDetectionResult(
    val matchingPixels: Int,
    val totalPixels: Int,
    val ratio: Double
)
