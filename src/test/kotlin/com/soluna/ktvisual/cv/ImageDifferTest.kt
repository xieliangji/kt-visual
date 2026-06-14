package com.soluna.ktvisual.cv

import com.soluna.ktvisual.OpenCvRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.nio.file.Files

class ImageDifferTest {

    init {
        OpenCvRuntime.ensureLoaded()
    }

    @Test
    fun `compare reports no difference for identical images`() {
        val expected = Mat.zeros(8, 8, CvType.CV_8UC3)
        val actual = expected.clone()

        try {
            val result = ImageDiffer.compare(expected, actual)

            assertEquals(0, result.changedPixels)
            assertEquals(64, result.totalPixels)
            assertEquals(0.0, result.differenceRatio)
            assertTrue(result.matches)
        } finally {
            expected.release()
            actual.release()
        }
    }

    @Test
    fun `compare reports changed pixels`() {
        val expected = Mat.zeros(8, 8, CvType.CV_8UC3)
        val actual = expected.clone()
        actual.put(3, 4, 255.0, 255.0, 255.0)

        try {
            val result = ImageDiffer.compare(expected, actual)

            assertEquals(1, result.changedPixels)
            assertFalse(result.matches)
        } finally {
            expected.release()
            actual.release()
        }
    }

    @Test
    fun `compare accepts image paths`() {
        val expected = patternedImage(width = 8, height = 8)
        val actual = expected.clone()

        try {
            val expectedPath = writePng(expected, "expected")
            val actualPath = writePng(actual, "actual")

            val result = ImageDiffer.compare(expectedPath, actualPath)

            assertTrue(result.matches)
            assertEquals(0, result.changedPixels)
        } finally {
            expected.release()
            actual.release()
        }
    }

    @Test
    fun `compare accepts encoded image bytes`() {
        val expected = patternedImage(width = 8, height = 8)
        val actual = expected.clone()

        try {
            val result = ImageDiffer.compare(
                expected = encodePng(expected),
                actual = encodePng(actual)
            )

            assertTrue(result.matches)
            assertEquals(0, result.changedPixels)
        } finally {
            expected.release()
            actual.release()
        }
    }

    @Test
    fun `compareResized matches same visual content with different sizes`() {
        val expected = patternedImage(width = 12, height = 10)
        val actual = resize(expected, width = 24, height = 20, interpolation = Imgproc.INTER_NEAREST)

        try {
            val result = ImageDiffer.compareResized(
                expected = expected,
                actual = actual,
                pixelThreshold = 2.0
            )

            assertEquals(0, result.changedPixels)
            assertTrue(result.matches)
        } finally {
            expected.release()
            actual.release()
        }
    }

    @Test
    fun `findCommonRegion locates shared content inside larger image`() {
        val common = patternedImage(width = 12, height = 10)
        val larger = Mat.zeros(50, 60, CvType.CV_8UC3)
        paste(common, larger, x = 31, y = 22)

        try {
            val result = ImageDiffer.findCommonRegion(
                source = common,
                target = larger,
                threshold = 0.99
            )

            assertNotNull(result)
            assertEquals(0, result.sourceRegion.x)
            assertEquals(0, result.sourceRegion.y)
            assertEquals(31, result.targetRegion.x)
            assertEquals(22, result.targetRegion.y)
            assertEquals(12, result.targetRegion.width)
            assertEquals(10, result.targetRegion.height)
            assertTrue(result.score >= 0.99)
        } finally {
            common.release()
            larger.release()
        }
    }

    @Test
    fun `findCommonRegion supports scaled shared content`() {
        val common = patternedImage(width = 12, height = 10)
        val scaledCommon = resize(common, width = 18, height = 15, interpolation = Imgproc.INTER_AREA)
        val larger = Mat.zeros(60, 80, CvType.CV_8UC3)
        paste(scaledCommon, larger, x = 37, y = 29)

        try {
            val result = ImageDiffer.findCommonRegion(
                source = common,
                target = larger,
                threshold = 0.99,
                scales = listOf(1.0, 1.5)
            )

            assertNotNull(result)
            assertEquals(37, result.targetRegion.x)
            assertEquals(29, result.targetRegion.y)
            assertEquals(18, result.targetRegion.width)
            assertEquals(15, result.targetRegion.height)
            assertEquals(1.5, result.scale)
        } finally {
            common.release()
            scaledCommon.release()
            larger.release()
        }
    }

    @Test
    fun `compareCommonRegion compares shared region after resizing`() {
        val common = patternedImage(width = 12, height = 10)
        val scaledCommon = resize(common, width = 18, height = 15, interpolation = Imgproc.INTER_AREA)
        val larger = Mat.zeros(60, 80, CvType.CV_8UC3)
        paste(scaledCommon, larger, x = 37, y = 29)

        try {
            val region = ImageDiffer.findCommonRegion(
                source = common,
                target = larger,
                threshold = 0.99,
                scales = listOf(1.5)
            )

            assertNotNull(region)

            val diff = ImageDiffer.compareCommonRegion(
                source = common,
                target = larger,
                commonRegion = region,
                pixelThreshold = 12.0,
                maxDifferenceRatio = 0.25
            )

            assertTrue(diff.matches)
        } finally {
            common.release()
            scaledCommon.release()
            larger.release()
        }
    }

    private fun patternedImage(width: Int, height: Int): Mat {
        val image = Mat(height, width, CvType.CV_8UC3)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val blue = (30 + x * 7 + y * 3) % 255
                val green = (80 + x * 5 + y * 11) % 255
                val red = (140 + x * 13 + y * 2) % 255
                image.put(y, x, blue.toDouble(), green.toDouble(), red.toDouble())
            }
        }

        return image
    }

    private fun resize(
        input: Mat,
        width: Int,
        height: Int,
        interpolation: Int = Imgproc.INTER_AREA
    ): Mat {
        val resized = Mat()
        Imgproc.resize(input, resized, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, interpolation)
        return resized
    }

    private fun paste(source: Mat, destination: Mat, x: Int, y: Int) {
        val roi = destination.submat(Rect(x, y, source.cols(), source.rows()))
        try {
            source.copyTo(roi)
        } finally {
            roi.release()
        }
    }

    private fun writePng(image: Mat, name: String) =
        Files.createTempFile("kt-visual-$name", ".png").also { path ->
            require(Imgcodecs.imwrite(path.toString(), image))
        }

    private fun encodePng(image: Mat): ByteArray {
        val encoded = MatOfByte()
        return try {
            require(Imgcodecs.imencode(".png", image, encoded))
            encoded.toArray()
        } finally {
            encoded.release()
        }
    }
}
