package com.soluna.ktvisual.cv

import com.soluna.ktvisual.OpenCvRuntime
import com.soluna.ktvisual.model.ChangedRegionsResult
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.RepeatedRegionOrientation
import com.soluna.ktvisual.model.RepeatedRegionsResult
import com.soluna.ktvisual.model.ScreenQualityResult
import com.soluna.ktvisual.model.TextBlock
import com.soluna.ktvisual.model.ThemeDetectionResult
import com.soluna.ktvisual.model.VisualTheme
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Finds changed visual areas between a baseline and current screenshot.
 */
object ChangedRegionDetector {

    /**
     * Returns merged bounding boxes for pixels whose grayscale difference is
     * greater than [pixelThreshold].
     */
    fun findChangedRegions(
        expected: Mat,
        actual: Mat,
        pixelThreshold: Double = 8.0,
        minRegionArea: Int = 16,
        mergeGap: Int = 2
    ): ChangedRegionsResult {
        OpenCvRuntime.ensureLoaded()
        require(!expected.empty()) { "expected Mat is empty." }
        require(!actual.empty()) { "actual Mat is empty." }
        require(expected.rows() == actual.rows() && expected.cols() == actual.cols()) {
            "Images must have the same size."
        }
        require(pixelThreshold in 0.0..255.0) { "pixelThreshold must be between 0.0 and 255.0." }
        require(minRegionArea > 0) { "minRegionArea must be > 0." }
        require(mergeGap >= 0) { "mergeGap must be >= 0." }

        val expectedGray = toGray(expected)
        val actualGray = toGray(actual)
        val diff = Mat()
        val mask = Mat()
        val mergedMask = Mat()
        val kernel = if (mergeGap > 0) {
            Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size((mergeGap * 2 + 1).toDouble(), (mergeGap * 2 + 1).toDouble())
            )
        } else {
            Mat()
        }
        val contours = mutableListOf<MatOfPoint>()

        try {
            Core.absdiff(expectedGray, actualGray, diff)
            Imgproc.threshold(diff, mask, pixelThreshold, 255.0, Imgproc.THRESH_BINARY)
            if (mergeGap > 0) {
                Imgproc.dilate(mask, mergedMask, kernel)
            } else {
                mask.copyTo(mergedMask)
            }

            Imgproc.findContours(
                mergedMask,
                contours,
                Mat(),
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val regions = contours
                .map { Imgproc.boundingRect(it) }
                .filter { it.width * it.height >= minRegionArea }
                .map { Region(it.x, it.y, it.width, it.height) }
                .sortedWith(compareBy<Region> { it.y }.thenBy { it.x })

            val changedPixels = Core.countNonZero(mask)
            val totalPixels = expected.rows() * expected.cols()
            return ChangedRegionsResult(
                regions = regions,
                changedPixels = changedPixels,
                totalPixels = totalPixels,
                differenceRatio = changedPixels.toDouble() / totalPixels.toDouble()
            )
        } finally {
            expectedGray.release()
            actualGray.release()
            diff.release()
            mask.release()
            mergedMask.release()
            kernel.release()
            contours.forEach { it.release() }
        }
    }
}

/**
 * Computes quality signals for screenshots captured during automation.
 */
object ScreenQualityDetector {

    /**
     * Analyzes whether [image] is blank, too dark, too bright, or blurred.
     */
    fun analyze(
        image: Mat,
        roi: Region? = null,
        blankStdDevThreshold: Double = 3.0,
        darkPixelThreshold: Int = 32,
        brightPixelThreshold: Int = 224,
        dominantPixelRatio: Double = 0.95,
        blurVarianceThreshold: Double = 30.0
    ): ScreenQualityResult {
        OpenCvRuntime.ensureLoaded()
        require(!image.empty()) { "image Mat is empty." }
        require(blankStdDevThreshold >= 0.0) { "blankStdDevThreshold must be >= 0.0." }
        require(darkPixelThreshold in 0..255) { "darkPixelThreshold must be between 0 and 255." }
        require(brightPixelThreshold in 0..255) { "brightPixelThreshold must be between 0 and 255." }
        require(dominantPixelRatio in 0.0..1.0) { "dominantPixelRatio must be between 0.0 and 1.0." }

        val target = crop(image, roi)
        val gray = toGray(target)
        val mean = MatOfDouble()
        val stdDev = MatOfDouble()
        val darkMask = Mat()
        val brightMask = Mat()
        val laplacian = Mat()
        val lapMean = MatOfDouble()
        val lapStdDev = MatOfDouble()

        try {
            Core.meanStdDev(gray, mean, stdDev)
            Imgproc.threshold(gray, darkMask, darkPixelThreshold.toDouble(), 255.0, Imgproc.THRESH_BINARY_INV)
            Imgproc.threshold(gray, brightMask, brightPixelThreshold.toDouble(), 255.0, Imgproc.THRESH_BINARY)
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, lapMean, lapStdDev)

            val totalPixels = gray.rows() * gray.cols()
            val averageBrightness = mean.toArray()[0]
            val brightnessStdDev = stdDev.toArray()[0]
            val darkRatio = Core.countNonZero(darkMask).toDouble() / totalPixels.toDouble()
            val brightRatio = Core.countNonZero(brightMask).toDouble() / totalPixels.toDouble()
            val lapStd = lapStdDev.toArray()[0]
            val blurVariance = lapStd * lapStd

            return ScreenQualityResult(
                averageBrightness = averageBrightness,
                brightnessStdDev = brightnessStdDev,
                darkPixelRatio = darkRatio,
                brightPixelRatio = brightRatio,
                blurVariance = blurVariance,
                isBlank = brightnessStdDev <= blankStdDevThreshold,
                isMostlyDark = darkRatio >= dominantPixelRatio,
                isMostlyBright = brightRatio >= dominantPixelRatio,
                isBlurred = blurVariance <= blurVarianceThreshold
            )
        } finally {
            if (roi != null) target.release()
            gray.release()
            mean.release()
            stdDev.release()
            darkMask.release()
            brightMask.release()
            laplacian.release()
            lapMean.release()
            lapStdDev.release()
        }
    }
}

/**
 * Coarse visual theme classifier.
 */
object ThemeDetector {

    /**
     * Classifies [image] as light, dark, or mixed using brightness ratios.
     */
    fun detect(
        image: Mat,
        roi: Region? = null,
        darkRatioThreshold: Double = 0.62,
        lightRatioThreshold: Double = 0.62
    ): ThemeDetectionResult {
        val quality = ScreenQualityDetector.analyze(image, roi)
        val theme = when {
            quality.darkPixelRatio >= darkRatioThreshold -> VisualTheme.DARK
            quality.brightPixelRatio >= lightRatioThreshold -> VisualTheme.LIGHT
            else -> VisualTheme.MIXED
        }
        return ThemeDetectionResult(
            theme = theme,
            averageBrightness = quality.averageBrightness,
            darkPixelRatio = quality.darkPixelRatio,
            brightPixelRatio = quality.brightPixelRatio
        )
    }
}

/**
 * Locates text-like regions without performing OCR.
 */
object TextRegionDetector {

    /**
     * Returns blocks with dense small edges, suitable for deciding where OCR or
     * layout assertions should run.
     */
    fun findTextBlocks(
        image: Mat,
        roi: Region? = null,
        minWidth: Int = 12,
        minHeight: Int = 6,
        maxHeight: Int = 80,
        minDensity: Double = 0.04
    ): List<TextBlock> {
        OpenCvRuntime.ensureLoaded()
        require(!image.empty()) { "image Mat is empty." }
        require(minWidth > 0) { "minWidth must be > 0." }
        require(minHeight > 0) { "minHeight must be > 0." }
        require(maxHeight >= minHeight) { "maxHeight must be >= minHeight." }
        require(minDensity in 0.0..1.0) { "minDensity must be between 0.0 and 1.0." }

        val target = crop(image, roi)
        val gray = toGray(target)
        val edges = Mat()
        val grouped = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 3.0))
        val contours = mutableListOf<MatOfPoint>()

        try {
            Imgproc.Canny(gray, edges, 60.0, 160.0)
            Imgproc.dilate(edges, grouped, kernel)
            Imgproc.findContours(grouped, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val offsetX = roi?.x ?: 0
            val offsetY = roi?.y ?: 0
            return contours
                .map { Imgproc.boundingRect(it) }
                .filter { it.width >= minWidth && it.height in minHeight..maxHeight }
                .mapNotNull { rect ->
                    val edgeRoi = Mat(edges, rect)
                    try {
                        val density = Core.countNonZero(edgeRoi).toDouble() / (rect.width * rect.height).toDouble()
                        if (density >= minDensity) {
                            TextBlock(
                                bounds = Region(offsetX + rect.x, offsetY + rect.y, rect.width, rect.height),
                                density = density
                            )
                        } else {
                            null
                        }
                    } finally {
                        edgeRoi.release()
                    }
                }
                .sortedWith(compareBy<TextBlock> { it.bounds.y }.thenBy { it.bounds.x })
        } finally {
            if (roi != null) target.release()
            gray.release()
            edges.release()
            grouped.release()
            kernel.release()
            contours.forEach { it.release() }
        }
    }
}

/**
 * Finds repeated row-like or column-like visual groups.
 */
object RepeatedRegionDetector {

    /**
     * Finds repeated horizontal regions, commonly list rows or stacked cards.
     */
    fun findRows(
        image: Mat,
        roi: Region? = null,
        minHeight: Int = 20,
        activityRatio: Double = 0.03
    ): RepeatedRegionsResult {
        val regions = activeBands(image, roi, RepeatedRegionOrientation.ROWS, minHeight, activityRatio)
        return RepeatedRegionsResult(regions, RepeatedRegionOrientation.ROWS)
    }

    /**
     * Finds repeated vertical regions, commonly grid columns or tab items.
     */
    fun findColumns(
        image: Mat,
        roi: Region? = null,
        minWidth: Int = 20,
        activityRatio: Double = 0.03
    ): RepeatedRegionsResult {
        val regions = activeBands(image, roi, RepeatedRegionOrientation.COLUMNS, minWidth, activityRatio)
        return RepeatedRegionsResult(regions, RepeatedRegionOrientation.COLUMNS)
    }

    private fun activeBands(
        image: Mat,
        roi: Region?,
        orientation: RepeatedRegionOrientation,
        minSize: Int,
        activityRatio: Double
    ): List<Region> {
        OpenCvRuntime.ensureLoaded()
        require(!image.empty()) { "image Mat is empty." }
        require(minSize > 0) { "minSize must be > 0." }
        require(activityRatio in 0.0..1.0) { "activityRatio must be between 0.0 and 1.0." }

        val target = crop(image, roi)
        val gray = toGray(target)
        val edges = Mat()

        try {
            Imgproc.Canny(gray, edges, 60.0, 160.0)
            val offsetX = roi?.x ?: 0
            val offsetY = roi?.y ?: 0
            val bands = mutableListOf<Region>()
            val length = if (orientation == RepeatedRegionOrientation.ROWS) edges.rows() else edges.cols()
            val span = if (orientation == RepeatedRegionOrientation.ROWS) edges.cols() else edges.rows()
            var start: Int? = null

            for (i in 0 until length) {
                val line = if (orientation == RepeatedRegionOrientation.ROWS) {
                    edges.row(i)
                } else {
                    edges.col(i)
                }
                val active = try {
                    Core.countNonZero(line).toDouble() / span.toDouble() >= activityRatio
                } finally {
                    line.release()
                }

                if (active && start == null) {
                    start = i
                } else if (!active && start != null) {
                    addBand(bands, start, i, minSize, target, offsetX, offsetY, orientation)
                    start = null
                }
            }
            if (start != null) {
                addBand(bands, start, length, minSize, target, offsetX, offsetY, orientation)
            }
            return bands
        } finally {
            if (roi != null) target.release()
            gray.release()
            edges.release()
        }
    }

    private fun addBand(
        bands: MutableList<Region>,
        start: Int,
        end: Int,
        minSize: Int,
        image: Mat,
        offsetX: Int,
        offsetY: Int,
        orientation: RepeatedRegionOrientation
    ) {
        val size = end - start
        if (size < minSize) return
        bands += if (orientation == RepeatedRegionOrientation.ROWS) {
            Region(offsetX, offsetY + start, image.cols(), size)
        } else {
            Region(offsetX + start, offsetY, size, image.rows())
        }
    }
}

/**
 * Detects simple visual occlusion signals inside a target region.
 */
object OcclusionDetector {

    /**
     * Returns true when [target] is dominated by dark or bright overlay pixels.
     *
     * This is a heuristic for common modal masks, keyboard overlays, or blocking
     * panels. It does not prove semantic occlusion without a baseline image.
     */
    fun isLikelyCovered(
        image: Mat,
        target: Region,
        darkPixelRatio: Double = 0.80,
        brightPixelRatio: Double = 0.98
    ): Boolean {
        val quality = ScreenQualityDetector.analyze(image, roi = target)
        return quality.darkPixelRatio >= darkPixelRatio || quality.brightPixelRatio >= brightPixelRatio
    }
}

private fun toGray(input: Mat): Mat {
    if (input.channels() == 1) {
        return input.clone()
    }

    val gray = Mat()
    Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY)
    return gray
}

private fun crop(image: Mat, roi: Region?): Mat {
    if (roi == null) return image
    require(roi.x >= 0) { "roi.x must be >= 0." }
    require(roi.y >= 0) { "roi.y must be >= 0." }
    require(roi.x + roi.width <= image.cols()) { "roi exceeds image width." }
    require(roi.y + roi.height <= image.rows()) { "roi exceeds image height." }
    return Mat(image, Rect(roi.x, roi.y, roi.width, roi.height))
}
