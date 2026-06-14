package com.soluna.ktvisual.cv

import com.soluna.ktvisual.model.ColorDetectionResult
import com.soluna.ktvisual.model.HsvColorRange
import com.soluna.ktvisual.model.NamedColor
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.RgbColor
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import kotlin.math.abs

/**
 * Detects exact RGB colors and broad semantic colors in screenshots.
 *
 * Use [NamedColor] and `containsGreen`/`containsRed` style helpers for ordinary
 * UI state checks. Use [RgbColor] with tolerance only when a precise color value
 * matters.
 */
object ColorDetector {

    val commonColors: Set<NamedColor> = NamedColor.entries.toSet()

    fun contains(
        image: Mat,
        color: NamedColor,
        minRatio: Double = 0.01,
        roi: Region? = null
    ): Boolean {
        require(minRatio in 0.0..1.0) { "minRatio must be between 0.0 and 1.0." }
        return detect(image, color, roi).ratio >= minRatio
    }

    /**
     * Loads an image file and checks whether [color] occupies at least [minRatio].
     */
    fun contains(image: Path, color: NamedColor, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return withImage(image) { contains(it, color, minRatio, roi) }
    }

    /**
     * Loads an image file and checks whether [color] occupies at least [minRatio].
     */
    fun contains(image: File, color: NamedColor, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image.toPath(), color, minRatio, roi)
    }

    /**
     * Converts an in-memory image and checks whether [color] occupies at least [minRatio].
     */
    fun contains(image: BufferedImage, color: NamedColor, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return withImage(image) { contains(it, color, minRatio, roi) }
    }

    /**
     * Decodes encoded image bytes and checks whether [color] occupies at least [minRatio].
     */
    fun contains(image: ByteArray, color: NamedColor, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return withImage(image) { contains(it, color, minRatio, roi) }
    }

    fun containsRed(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.RED, minRatio, roi)
    }

    fun containsOrange(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.ORANGE, minRatio, roi)
    }

    fun containsYellow(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.YELLOW, minRatio, roi)
    }

    fun containsGreen(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.GREEN, minRatio, roi)
    }

    fun containsCyan(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.CYAN, minRatio, roi)
    }

    fun containsBlue(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.BLUE, minRatio, roi)
    }

    fun containsPurple(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.PURPLE, minRatio, roi)
    }

    fun containsPink(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.PINK, minRatio, roi)
    }

    fun containsWhite(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.WHITE, minRatio, roi)
    }

    fun containsBlack(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.BLACK, minRatio, roi)
    }

    fun containsGray(image: Mat, minRatio: Double = 0.01, roi: Region? = null): Boolean {
        return contains(image, NamedColor.GRAY, minRatio, roi)
    }

    fun containsRed(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.RED, minRatio, roi)

    fun containsRed(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.RED, minRatio, roi)

    fun containsOrange(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.ORANGE, minRatio, roi)

    fun containsOrange(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.ORANGE, minRatio, roi)

    fun containsYellow(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.YELLOW, minRatio, roi)

    fun containsYellow(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.YELLOW, minRatio, roi)

    fun containsGreen(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.GREEN, minRatio, roi)

    fun containsGreen(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.GREEN, minRatio, roi)

    fun containsCyan(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.CYAN, minRatio, roi)

    fun containsCyan(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.CYAN, minRatio, roi)

    fun containsBlue(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.BLUE, minRatio, roi)

    fun containsBlue(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.BLUE, minRatio, roi)

    fun containsPurple(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.PURPLE, minRatio, roi)

    fun containsPurple(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.PURPLE, minRatio, roi)

    fun containsPink(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.PINK, minRatio, roi)

    fun containsPink(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.PINK, minRatio, roi)

    fun containsWhite(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.WHITE, minRatio, roi)

    fun containsWhite(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.WHITE, minRatio, roi)

    fun containsBlack(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.BLACK, minRatio, roi)

    fun containsBlack(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.BLACK, minRatio, roi)

    fun containsGray(image: Path, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.GRAY, minRatio, roi)

    fun containsGray(image: ByteArray, minRatio: Double = 0.01, roi: Region? = null): Boolean =
        contains(image, NamedColor.GRAY, minRatio, roi)

    fun detect(
        image: Mat,
        color: NamedColor,
        roi: Region? = null
    ): ColorDetectionResult {
        return detect(image, color.ranges, roi)
    }

    fun detect(image: Path, color: NamedColor, roi: Region? = null): ColorDetectionResult {
        return withImage(image) { detect(it, color, roi) }
    }

    fun detect(image: File, color: NamedColor, roi: Region? = null): ColorDetectionResult {
        return detect(image.toPath(), color, roi)
    }

    fun detect(image: BufferedImage, color: NamedColor, roi: Region? = null): ColorDetectionResult {
        return withImage(image) { detect(it, color, roi) }
    }

    fun detect(image: ByteArray, color: NamedColor, roi: Region? = null): ColorDetectionResult {
        return withImage(image) { detect(it, color, roi) }
    }

    fun detect(
        image: Mat,
        range: HsvColorRange,
        roi: Region? = null
    ): ColorDetectionResult {
        return detect(image, listOf(range), roi)
    }

    fun detect(image: Path, range: HsvColorRange, roi: Region? = null): ColorDetectionResult {
        return withImage(image) { detect(it, range, roi) }
    }

    fun detect(image: File, range: HsvColorRange, roi: Region? = null): ColorDetectionResult {
        return detect(image.toPath(), range, roi)
    }

    fun detect(image: BufferedImage, range: HsvColorRange, roi: Region? = null): ColorDetectionResult {
        return withImage(image) { detect(it, range, roi) }
    }

    fun detect(image: ByteArray, range: HsvColorRange, roi: Region? = null): ColorDetectionResult {
        return withImage(image) { detect(it, range, roi) }
    }

    fun detect(
        image: Mat,
        ranges: List<HsvColorRange>,
        roi: Region? = null
    ): ColorDetectionResult {
        require(!image.empty()) { "image Mat is empty." }
        require(image.channels() >= 3) { "image Mat must have at least 3 channels." }
        require(ranges.isNotEmpty()) { "ranges must not be empty." }

        val region = roi?.also { validateRoi(image, it) }
        val searchImage = if (region == null) {
            image
        } else {
            Mat(image, Rect(region.x, region.y, region.width, region.height))
        }

        val hsv = Mat()
        try {
            Imgproc.cvtColor(searchImage, hsv, Imgproc.COLOR_BGR2HSV)

            var matchingPixels = 0
            val totalPixels = hsv.rows() * hsv.cols()

            for (y in 0 until hsv.rows()) {
                for (x in 0 until hsv.cols()) {
                    val pixel = hsv.get(y, x)
                    val hue = pixel[0].toInt()
                    val saturation = pixel[1].toInt()
                    val value = pixel[2].toInt()

                    if (ranges.any { it.contains(hue, saturation, value) }) {
                        matchingPixels += 1
                    }
                }
            }

            return ColorDetectionResult(
                matchingPixels = matchingPixels,
                totalPixels = totalPixels,
                ratio = matchingPixels.toDouble() / totalPixels.toDouble()
            )
        } finally {
            hsv.release()
            if (region != null) {
                searchImage.release()
            }
        }
    }

    fun detect(image: Path, ranges: List<HsvColorRange>, roi: Region? = null): ColorDetectionResult {
        return withImage(image) { detect(it, ranges, roi) }
    }

    fun detect(image: File, ranges: List<HsvColorRange>, roi: Region? = null): ColorDetectionResult {
        return detect(image.toPath(), ranges, roi)
    }

    fun detect(image: BufferedImage, ranges: List<HsvColorRange>, roi: Region? = null): ColorDetectionResult {
        return withImage(image) { detect(it, ranges, roi) }
    }

    fun detect(image: ByteArray, ranges: List<HsvColorRange>, roi: Region? = null): ColorDetectionResult {
        return withImage(image) { detect(it, ranges, roi) }
    }

    fun detect(
        image: Mat,
        color: RgbColor,
        tolerance: Int = 0,
        roi: Region? = null
    ): ColorDetectionResult {
        require(!image.empty()) { "image Mat is empty." }
        require(image.channels() >= 3) { "image Mat must have at least 3 channels." }
        require(tolerance in 0..255) { "tolerance must be between 0 and 255." }

        val region = roi?.also { validateRoi(image, it) }
        val searchImage = if (region == null) {
            image
        } else {
            Mat(image, Rect(region.x, region.y, region.width, region.height))
        }

        try {
            var matchingPixels = 0
            val totalPixels = searchImage.rows() * searchImage.cols()

            for (y in 0 until searchImage.rows()) {
                for (x in 0 until searchImage.cols()) {
                    val pixel = searchImage.get(y, x)
                    val blue = pixel[0].toInt()
                    val green = pixel[1].toInt()
                    val red = pixel[2].toInt()

                    if (
                        abs(red - color.red) <= tolerance &&
                        abs(green - color.green) <= tolerance &&
                        abs(blue - color.blue) <= tolerance
                    ) {
                        matchingPixels += 1
                    }
                }
            }

            return ColorDetectionResult(
                matchingPixels = matchingPixels,
                totalPixels = totalPixels,
                ratio = matchingPixels.toDouble() / totalPixels.toDouble()
            )
        } finally {
            if (region != null) {
                searchImage.release()
            }
        }
    }

    fun detect(
        image: Path,
        color: RgbColor,
        tolerance: Int = 0,
        roi: Region? = null
    ): ColorDetectionResult {
        return withImage(image) { detect(it, color, tolerance, roi) }
    }

    fun detect(
        image: File,
        color: RgbColor,
        tolerance: Int = 0,
        roi: Region? = null
    ): ColorDetectionResult {
        return detect(image.toPath(), color, tolerance, roi)
    }

    fun detect(
        image: BufferedImage,
        color: RgbColor,
        tolerance: Int = 0,
        roi: Region? = null
    ): ColorDetectionResult {
        return withImage(image) { detect(it, color, tolerance, roi) }
    }

    fun detect(
        image: ByteArray,
        color: RgbColor,
        tolerance: Int = 0,
        roi: Region? = null
    ): ColorDetectionResult {
        return withImage(image) { detect(it, color, tolerance, roi) }
    }

    private fun validateRoi(image: Mat, roi: Region) {
        require(roi.x >= 0) { "roi.x must be >= 0" }
        require(roi.y >= 0) { "roi.y must be >= 0" }
        require(roi.x + roi.width <= image.cols()) { "roi exceeds image width." }
        require(roi.y + roi.height <= image.rows()) { "roi exceeds image height." }
    }

    private fun HsvColorRange.contains(hue: Int, saturation: Int, value: Int): Boolean {
        return hue in minHue..maxHue &&
            saturation in minSaturation..maxSaturation &&
            value in minValue..maxValue
    }

    private inline fun <T> withImage(path: Path, block: (Mat) -> T): T {
        val image = MatConverters.fromPath(path)
        return try {
            block(image)
        } finally {
            image.release()
        }
    }

    private inline fun <T> withImage(image: BufferedImage, block: (Mat) -> T): T {
        val mat = MatConverters.fromBufferedImage(image)
        return try {
            block(mat)
        } finally {
            mat.release()
        }
    }

    private inline fun <T> withImage(bytes: ByteArray, block: (Mat) -> T): T {
        val image = MatConverters.fromBytes(bytes)
        return try {
            block(image)
        } finally {
            image.release()
        }
    }
}
