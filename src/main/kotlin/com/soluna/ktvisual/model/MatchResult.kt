package com.soluna.ktvisual.model

/**
 * Result of a visual match.
 *
 * [bounds] and [center] are in screenshot coordinates. [score] is normalized so
 * larger is better for all supported OpenCV template methods. [elapsedMillis]
 * is the total lookup time for the public find call that produced this result.
 */
data class MatchResult(
    val targetName: String,
    val bounds: Region,
    val score: Double,
    val scale: Double,
    val elapsedMillis: Long = 0
) {
    val center: Point2
        get() = bounds.center
}
