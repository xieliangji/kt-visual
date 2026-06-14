package com.soluna.ktvisual.model

/**
 * Exact RGB color value.
 *
 * OpenCV images in this project are usually stored internally as BGR Mats, but
 * this API uses RGB order because it is what automation users normally expect.
 */
data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int
) {
    init {
        require(red in 0..255) { "red must be between 0 and 255." }
        require(green in 0..255) { "green must be between 0 and 255." }
        require(blue in 0..255) { "blue must be between 0 and 255." }
    }
}
