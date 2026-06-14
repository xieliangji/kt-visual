package com.soluna.ktvisual.api

import com.soluna.ktvisual.model.ColorDetectionResult
import com.soluna.ktvisual.model.ImageDiffResult
import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.ScreenQualityResult

/**
 * Assertion helpers for visual automation tests.
 *
 * The methods throw [VisionException] instead of depending on a specific test
 * framework. This keeps the library usable from JUnit, Kotlin test, TestNG,
 * custom runners, and production diagnostics.
 */
object VisualAssertions {

    /**
     * Returns [result] when it is non-null, otherwise fails with a descriptive
     * "not visible" error.
     */
    fun assertVisible(targetName: String, result: MatchResult?): MatchResult {
        return result ?: throw VisionException("Expected target to be visible: $targetName")
    }

    /**
     * Fails when [result] is non-null.
     */
    fun assertNotVisible(targetName: String, result: MatchResult?) {
        if (result != null) {
            throw VisionException(
                "Expected target to be invisible: $targetName, but found bounds=${result.bounds}, score=${result.score}"
            )
        }
    }

    /**
     * Fails when the match score is below [minScore].
     */
    fun assertScoreAtLeast(result: MatchResult, minScore: Double): MatchResult {
        require(minScore in 0.0..1.0) { "minScore must be between 0.0 and 1.0." }
        if (result.score < minScore) {
            throw VisionException(
                "Expected score >= $minScore for ${result.targetName}, actual=${result.score}"
            )
        }
        return result
    }

    /**
     * Fails when [result]'s center point is outside [region].
     */
    fun assertCenterInside(result: MatchResult, region: Region): MatchResult {
        val center = result.center
        val inside = center.x in region.x until region.x + region.width &&
            center.y in region.y until region.y + region.height
        if (!inside) {
            throw VisionException(
                "Expected ${result.targetName} center=$center to be inside region=$region"
            )
        }
        return result
    }

    /**
     * Fails when [diff] exceeds its configured difference threshold.
     */
    fun assertImagesMatch(diff: ImageDiffResult): ImageDiffResult {
        if (!diff.matches) {
            throw VisionException(
                "Expected images to match, changedPixels=${diff.changedPixels}, ratio=${diff.differenceRatio}"
            )
        }
        return diff
    }

    /**
     * Fails when the detected color ratio is below [minRatio].
     */
    fun assertColorRatioAtLeast(
        result: ColorDetectionResult,
        minRatio: Double
    ): ColorDetectionResult {
        require(minRatio in 0.0..1.0) { "minRatio must be between 0.0 and 1.0." }
        if (result.ratio < minRatio) {
            throw VisionException(
                "Expected color ratio >= $minRatio, actual=${result.ratio}, matchingPixels=${result.matchingPixels}/${result.totalPixels}"
            )
        }
        return result
    }

    /**
     * Fails when [actual] overlaps [blocked].
     */
    fun assertNoOverlap(actual: Region, blocked: Region, label: String = "region") {
        if (overlapArea(actual, blocked) > 0) {
            throw VisionException("Expected $label not to overlap blocked=$blocked, actual=$actual")
        }
    }

    /**
     * Fails when [actual] is not fully inside [container].
     */
    fun assertInside(actual: Region, container: Region, label: String = "region") {
        val inside = actual.x >= container.x &&
            actual.y >= container.y &&
            actual.x + actual.width <= container.x + container.width &&
            actual.y + actual.height <= container.y + container.height
        if (!inside) {
            throw VisionException("Expected $label=$actual to be inside container=$container")
        }
    }

    /**
     * Fails when two regions are not horizontally aligned by center Y.
     */
    fun assertAlignedCenterY(
        first: Region,
        second: Region,
        tolerancePx: Int = 1,
        label: String = "regions"
    ) {
        require(tolerancePx >= 0) { "tolerancePx must be >= 0." }
        val delta = kotlin.math.abs(first.center.y - second.center.y)
        if (delta > tolerancePx) {
            throw VisionException("Expected $label centerY to align within $tolerancePx px, actual delta=$delta")
        }
    }

    /**
     * Fails when two regions are not vertically aligned by center X.
     */
    fun assertAlignedCenterX(
        first: Region,
        second: Region,
        tolerancePx: Int = 1,
        label: String = "regions"
    ) {
        require(tolerancePx >= 0) { "tolerancePx must be >= 0." }
        val delta = kotlin.math.abs(first.center.x - second.center.x)
        if (delta > tolerancePx) {
            throw VisionException("Expected $label centerX to align within $tolerancePx px, actual delta=$delta")
        }
    }

    /**
     * Fails when [right] is not visually to the right of [left].
     */
    fun assertLeftToRight(left: Region, right: Region, minGap: Int = 0) {
        require(minGap >= 0) { "minGap must be >= 0." }
        if (right.x < left.x + left.width + minGap) {
            throw VisionException("Expected right=$right to be at least $minGap px right of left=$left")
        }
    }

    /**
     * Fails when [quality] indicates a blank, mostly dark, mostly bright, or
     * blurred screenshot.
     */
    fun assertUsableScreenshot(quality: ScreenQualityResult): ScreenQualityResult {
        if (quality.isBlank || quality.isMostlyDark || quality.isMostlyBright || quality.isBlurred) {
            throw VisionException("Expected usable screenshot, quality=$quality")
        }
        return quality
    }

    private fun overlapArea(first: Region, second: Region): Int {
        val left = maxOf(first.x, second.x)
        val top = maxOf(first.y, second.y)
        val right = minOf(first.x + first.width, second.x + second.width)
        val bottom = minOf(first.y + first.height, second.y + second.height)
        return (right - left).coerceAtLeast(0) * (bottom - top).coerceAtLeast(0)
    }
}
