package com.soluna.ktvisual.model

/**
 * Inclusive HSV color range using OpenCV's hue scale.
 *
 * OpenCV stores hue in `0..179`, while saturation and value use `0..255`.
 */
data class HsvColorRange(
    val minHue: Int,
    val maxHue: Int,
    val minSaturation: Int = 40,
    val maxSaturation: Int = 255,
    val minValue: Int = 40,
    val maxValue: Int = 255
) {
    init {
        require(minHue in 0..179) { "minHue must be between 0 and 179." }
        require(maxHue in 0..179) { "maxHue must be between 0 and 179." }
        require(minHue <= maxHue) { "minHue must be <= maxHue." }
        require(minSaturation in 0..255) { "minSaturation must be between 0 and 255." }
        require(maxSaturation in 0..255) { "maxSaturation must be between 0 and 255." }
        require(minSaturation <= maxSaturation) { "minSaturation must be <= maxSaturation." }
        require(minValue in 0..255) { "minValue must be between 0 and 255." }
        require(maxValue in 0..255) { "maxValue must be between 0 and 255." }
        require(minValue <= maxValue) { "minValue must be <= maxValue." }
    }
}
