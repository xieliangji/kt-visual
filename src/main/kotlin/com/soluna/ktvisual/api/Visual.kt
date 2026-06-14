package com.soluna.ktvisual.api

import com.soluna.ktvisual.cv.ChangedRegionDetector
import com.soluna.ktvisual.cv.ColorDetector
import com.soluna.ktvisual.cv.ImageDiffer
import com.soluna.ktvisual.cv.MatConverters
import com.soluna.ktvisual.cv.OcclusionDetector
import com.soluna.ktvisual.cv.RepeatedRegionDetector
import com.soluna.ktvisual.cv.ScreenQualityDetector
import com.soluna.ktvisual.cv.TemplateLocator
import com.soluna.ktvisual.cv.TextRegionDetector
import com.soluna.ktvisual.cv.ThemeDetector
import com.soluna.ktvisual.model.ChangedRegionsResult
import com.soluna.ktvisual.model.ColorDetectionResult
import com.soluna.ktvisual.model.CommonRegionResult
import com.soluna.ktvisual.model.ImageDiffResult
import com.soluna.ktvisual.model.MatchOptions
import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.NamedColor
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.RepeatedRegionsResult
import com.soluna.ktvisual.model.RgbColor
import com.soluna.ktvisual.model.ScreenQualityResult
import com.soluna.ktvisual.model.TextBlock
import com.soluna.ktvisual.model.ThemeDetectionResult
import com.soluna.ktvisual.model.UiTarget
import com.soluna.ktvisual.model.VisualStabilityResult
import com.soluna.ktvisual.utils.RetryWaiter
import org.opencv.core.Mat
import org.opencv.core.Rect
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.time.Duration

/**
 * Primary facade for visual automation analysis.
 *
 * Automation projects should start here instead of depending directly on the
 * lower-level `cv` package. Methods accept common screenshot forms such as
 * encoded PNG/JPEG [ByteArray], [Path], [BufferedImage], and [Mat]. When this
 * facade loads or decodes an image internally, it releases the temporary OpenCV
 * objects before returning.
 */
object Visual {

    /**
     * Compares two same-sized images and returns aggregate pixel differences.
     */
    fun compare(expected: ByteArray, actual: ByteArray, pixelThreshold: Double = 0.0, maxDifferenceRatio: Double = 0.0): ImageDiffResult {
        return ImageDiffer.compare(expected, actual, pixelThreshold, maxDifferenceRatio)
    }

    /**
     * Compares two same-sized image files and returns aggregate pixel differences.
     */
    fun compare(expected: Path, actual: Path, pixelThreshold: Double = 0.0, maxDifferenceRatio: Double = 0.0): ImageDiffResult {
        return ImageDiffer.compare(expected, actual, pixelThreshold, maxDifferenceRatio)
    }

    /**
     * Compares two same-sized [Mat] images. The caller keeps ownership of both inputs.
     */
    fun compare(expected: Mat, actual: Mat, pixelThreshold: Double = 0.0, maxDifferenceRatio: Double = 0.0): ImageDiffResult {
        return ImageDiffer.compare(expected, actual, pixelThreshold, maxDifferenceRatio)
    }

    /**
     * Compares two images after resizing [actual] to [expected]'s dimensions.
     */
    fun compareResized(expected: ByteArray, actual: ByteArray, pixelThreshold: Double = 0.0, maxDifferenceRatio: Double = 0.0): ImageDiffResult {
        return ImageDiffer.compareResized(expected, actual, pixelThreshold, maxDifferenceRatio)
    }

    /**
     * Compares two image files after resizing [actual] to [expected]'s dimensions.
     */
    fun compareResized(expected: Path, actual: Path, pixelThreshold: Double = 0.0, maxDifferenceRatio: Double = 0.0): ImageDiffResult {
        return ImageDiffer.compareResized(expected, actual, pixelThreshold, maxDifferenceRatio)
    }

    /**
     * Finds the strongest shared visual region between two images.
     */
    fun findCommonRegion(
        source: ByteArray,
        target: ByteArray,
        threshold: Double = 0.88,
        scales: List<Double> = listOf(1.0)
    ): CommonRegionResult? {
        return ImageDiffer.findCommonRegion(source, target, threshold, scales)
    }

    /**
     * Compares an already discovered common region between two encoded images.
     */
    fun compareCommonRegion(
        source: ByteArray,
        target: ByteArray,
        commonRegion: CommonRegionResult,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return ImageDiffer.compareCommonRegion(source, target, commonRegion, pixelThreshold, maxDifferenceRatio)
    }

    /**
     * Finds changed bounding boxes between two same-sized encoded images.
     */
    fun findChangedRegions(
        expected: ByteArray,
        actual: ByteArray,
        pixelThreshold: Double = 8.0,
        minRegionArea: Int = 16,
        mergeGap: Int = 2
    ): ChangedRegionsResult {
        return withImages(expected, actual) { expectedMat, actualMat ->
            ChangedRegionDetector.findChangedRegions(expectedMat, actualMat, pixelThreshold, minRegionArea, mergeGap)
        }
    }

    /**
     * Finds changed bounding boxes between two same-sized image files.
     */
    fun findChangedRegions(
        expected: Path,
        actual: Path,
        pixelThreshold: Double = 8.0,
        minRegionArea: Int = 16,
        mergeGap: Int = 2
    ): ChangedRegionsResult {
        return withImages(expected, actual) { expectedMat, actualMat ->
            ChangedRegionDetector.findChangedRegions(expectedMat, actualMat, pixelThreshold, minRegionArea, mergeGap)
        }
    }

    /**
     * Finds the best template match in encoded screenshot bytes.
     */
    fun findTemplate(
        screen: ByteArray,
        template: ByteArray,
        targetName: String,
        options: MatchOptions = MatchOptions()
    ): MatchResult? {
        return TemplateLocator().use { it.find(screen, template, targetName, options) }
    }

    /**
     * Finds the best target match in encoded screenshot bytes.
     */
    fun findTarget(screen: ByteArray, target: UiTarget): MatchResult? {
        return TemplateLocator().use { it.find(screen, target) }
    }

    /**
     * Finds all non-overlapping target matches in encoded screenshot bytes.
     */
    fun findAllTargets(screen: ByteArray, target: UiTarget): List<MatchResult> {
        return TemplateLocator().use { it.findAll(screen, target) }
    }

    /**
     * Detects a broad named color in encoded screenshot bytes.
     */
    fun containsColor(image: ByteArray, color: NamedColor, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return ColorDetector.contains(image, color, minRatio, roi)
    }

    /**
     * Detects whether a screenshot contains semantic green in [roi].
     */
    fun containsGreen(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return ColorDetector.containsGreen(image, minRatio, roi)
    }

    /**
     * Detects broad named color coverage in encoded screenshot bytes.
     */
    fun detectColor(image: ByteArray, color: NamedColor, roi: Region? = null): ColorDetectionResult {
        return ColorDetector.detect(image, color, roi)
    }

    /**
     * Detects exact RGB color coverage in encoded screenshot bytes.
     */
    fun detectColor(image: ByteArray, color: RgbColor, tolerance: Int = 0, roi: Region? = null): ColorDetectionResult {
        return ColorDetector.detect(image, color, tolerance, roi)
    }

    /**
     * Analyzes whether a screenshot is blank, dark, bright, or blurred.
     */
    fun analyzeQuality(image: ByteArray, roi: Region? = null): ScreenQualityResult {
        return withImage(image) { ScreenQualityDetector.analyze(it, roi) }
    }

    /**
     * Analyzes whether an image file is blank, dark, bright, or blurred.
     */
    fun analyzeQuality(image: Path, roi: Region? = null): ScreenQualityResult {
        return withImage(image) { ScreenQualityDetector.analyze(it, roi) }
    }

    /**
     * Classifies a screenshot as light, dark, or mixed.
     */
    fun detectTheme(image: ByteArray, roi: Region? = null): ThemeDetectionResult {
        return withImage(image) { ThemeDetector.detect(it, roi) }
    }

    /**
     * Locates text-like regions without running OCR.
     */
    fun findTextBlocks(image: ByteArray, roi: Region? = null): List<TextBlock> {
        return withImage(image) { TextRegionDetector.findTextBlocks(it, roi) }
    }

    /**
     * Finds row-like repeated visual regions, typically list rows or stacked cards.
     */
    fun findRepeatedRows(image: ByteArray, roi: Region? = null, minHeight: Int = 20): RepeatedRegionsResult {
        return withImage(image) { RepeatedRegionDetector.findRows(it, roi, minHeight) }
    }

    /**
     * Finds column-like repeated visual regions, typically tabs or grid cells.
     */
    fun findRepeatedColumns(image: ByteArray, roi: Region? = null, minWidth: Int = 20): RepeatedRegionsResult {
        return withImage(image) { RepeatedRegionDetector.findColumns(it, roi, minWidth) }
    }

    /**
     * Heuristically checks whether [target] is likely covered by a modal mask or blocking overlay.
     */
    fun isLikelyCovered(image: ByteArray, target: Region): Boolean {
        return withImage(image) { OcclusionDetector.isLikelyCovered(it, target) }
    }

    /**
     * Waits until consecutive screenshots become visually stable.
     *
     * [samples] is the number of consecutive low-difference comparisons
     * required. [roi] limits comparison to a region such as a content area while
     * ignoring status bars, clocks, or ads.
     */
    fun waitStable(
        screenSource: ScreenSource,
        timeout: Duration = Duration.ofSeconds(5),
        interval: Duration = Duration.ofMillis(250),
        samples: Int = 3,
        roi: Region? = null,
        pixelThreshold: Double = 5.0,
        maxDifferenceRatio: Double = 0.001
    ): VisualStabilityResult {
        require(samples > 0) { "samples must be > 0." }
        var previous: Mat? = null
        var stableSamples = 0
        var lastRatio = 1.0

        val stable = RetryWaiter.waitUntil(timeout, interval) {
            val current = MatConverters.fromBufferedImage(screenSource.capture())
            try {
                val previousMat = previous
                if (previousMat == null) {
                    previous = current.clone()
                    return@waitUntil false
                }

                val previousCompare = cropForCompare(previousMat, roi)
                val currentCompare = cropForCompare(current, roi)
                try {
                    val diff = ImageDiffer.compare(previousCompare, currentCompare, pixelThreshold, maxDifferenceRatio)
                    lastRatio = diff.differenceRatio
                    stableSamples = if (diff.matches) stableSamples + 1 else 0
                    previousMat.release()
                    previous = current.clone()
                    stableSamples >= samples
                } finally {
                    if (roi != null) {
                        previousCompare.release()
                        currentCompare.release()
                    }
                }
            } finally {
                current.release()
            }
        }

        previous?.release()
        return VisualStabilityResult(stable = stable, samples = stableSamples, lastDifferenceRatio = lastRatio)
    }

    private inline fun <T> withImage(bytes: ByteArray, block: (Mat) -> T): T {
        val image = MatConverters.fromBytes(bytes)
        return try {
            block(image)
        } finally {
            image.release()
        }
    }

    private inline fun <T> withImage(path: Path, block: (Mat) -> T): T {
        val image = MatConverters.fromPath(path)
        return try {
            block(image)
        } finally {
            image.release()
        }
    }

    private inline fun <T> withImages(first: ByteArray, second: ByteArray, block: (Mat, Mat) -> T): T {
        val firstMat = MatConverters.fromBytes(first)
        val secondMat = MatConverters.fromBytes(second)
        return try {
            block(firstMat, secondMat)
        } finally {
            firstMat.release()
            secondMat.release()
        }
    }

    private inline fun <T> withImages(first: Path, second: Path, block: (Mat, Mat) -> T): T {
        val firstMat = MatConverters.fromPath(first)
        val secondMat = MatConverters.fromPath(second)
        return try {
            block(firstMat, secondMat)
        } finally {
            firstMat.release()
            secondMat.release()
        }
    }

    private fun cropForCompare(image: Mat, roi: Region?): Mat {
        if (roi == null) return image
        return Mat(image, Rect(roi.x, roi.y, roi.width, roi.height))
    }
}
