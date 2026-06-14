package com.soluna.ktvisual.api

import com.soluna.ktvisual.model.ColorDetectionResult
import com.soluna.ktvisual.model.ImageDiffResult
import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.Region
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VisualAssertionsTest {

    @Test
    fun `assertVisible returns match and fails for null`() {
        val result = match()

        assertEquals(result, VisualAssertions.assertVisible("target", result))
        assertFailsWith<VisionException> {
            VisualAssertions.assertVisible("target", null)
        }
    }

    @Test
    fun `assertScoreAtLeast validates score`() {
        val result = match(score = 0.92)

        assertEquals(result, VisualAssertions.assertScoreAtLeast(result, 0.90))
        assertFailsWith<VisionException> {
            VisualAssertions.assertScoreAtLeast(result, 0.95)
        }
    }

    @Test
    fun `assertCenterInside validates region`() {
        val result = match(x = 10, y = 10)

        VisualAssertions.assertCenterInside(result, Region(0, 0, 30, 30))
        assertFailsWith<VisionException> {
            VisualAssertions.assertCenterInside(result, Region(100, 100, 30, 30))
        }
    }

    @Test
    fun `assertImagesMatch validates diff result`() {
        VisualAssertions.assertImagesMatch(
            ImageDiffResult(changedPixels = 0, totalPixels = 100, differenceRatio = 0.0, matches = true)
        )

        assertFailsWith<VisionException> {
            VisualAssertions.assertImagesMatch(
                ImageDiffResult(changedPixels = 10, totalPixels = 100, differenceRatio = 0.1, matches = false)
            )
        }
    }

    @Test
    fun `assertColorRatioAtLeast validates color ratio`() {
        val result = ColorDetectionResult(matchingPixels = 8, totalPixels = 10, ratio = 0.8)

        VisualAssertions.assertColorRatioAtLeast(result, 0.7)
        assertFailsWith<VisionException> {
            VisualAssertions.assertColorRatioAtLeast(result, 0.9)
        }
    }

    private fun match(x: Int = 0, y: Int = 0, score: Double = 0.9): MatchResult {
        return MatchResult(
            targetName = "target",
            bounds = Region(x = x, y = y, width = 10, height = 10),
            score = score,
            scale = 1.0
        )
    }
}
