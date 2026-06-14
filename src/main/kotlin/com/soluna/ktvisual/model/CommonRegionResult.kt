package com.soluna.ktvisual.model

/**
 * Describes a common visual region found between two images.
 *
 * [sourceRegion] is the matched area in the source image. [targetRegion] is the
 * corresponding area in the target image. [scale] is the scale applied to the
 * smaller/template side during the search.
 */
data class CommonRegionResult(
    val sourceRegion: Region,
    val targetRegion: Region,
    val score: Double,
    val scale: Double
)
