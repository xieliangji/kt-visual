package com.soluna.ktvisual.model

/**
 * Pixel-difference summary produced by image comparison.
 *
 * [matches] is derived from the threshold supplied to the comparison function.
 */
data class ImageDiffResult(
    val changedPixels: Int,
    val totalPixels: Int,
    val differenceRatio: Double,
    val matches: Boolean
)
