package com.soluna.ktvisual.cv

import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.NamedColor
import com.soluna.ktvisual.model.RgbColor
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs

class ColorDetectorTest {

    @Test
    fun `detect returns color ratio inside roi`() {
        val image = Mat.zeros(4, 4, CvType.CV_8UC3)

        try {
            image.put(1, 1, 0.0, 0.0, 255.0)
            image.put(1, 2, 0.0, 0.0, 255.0)

            val result = ColorDetector.detect(
                image = image,
                color = RgbColor(red = 255, green = 0, blue = 0),
                tolerance = 0,
                roi = Region(x = 1, y = 1, width = 2, height = 2)
            )

            assertEquals(2, result.matchingPixels)
            assertEquals(4, result.totalPixels)
            assertEquals(0.5, result.ratio)
        } finally {
            image.release()
        }
    }

    @Test
    fun `detect named green matches a broad green range`() {
        val image = Mat.zeros(4, 4, CvType.CV_8UC3)

        try {
            image.put(0, 0, 0.0, 255.0, 0.0)
            image.put(0, 1, 0.0, 180.0, 40.0)
            image.put(1, 0, 70.0, 220.0, 70.0)
            image.put(1, 1, 255.0, 0.0, 0.0)

            val result = ColorDetector.detect(
                image = image,
                color = NamedColor.GREEN,
                roi = Region(x = 0, y = 0, width = 2, height = 2)
            )

            assertEquals(3, result.matchingPixels)
            assertEquals(4, result.totalPixels)
            assertEquals(0.75, result.ratio)
        } finally {
            image.release()
        }
    }

    @Test
    fun `detect named red supports hue wrap around`() {
        val image = Mat.zeros(2, 2, CvType.CV_8UC3)

        try {
            image.put(0, 0, 0.0, 0.0, 255.0)
            image.put(0, 1, 30.0, 0.0, 255.0)
            image.put(1, 0, 255.0, 0.0, 0.0)

            val result = ColorDetector.detect(
                image = image,
                color = NamedColor.RED
            )

            assertEquals(2, result.matchingPixels)
            assertEquals(4, result.totalPixels)
        } finally {
            image.release()
        }
    }

    @Test
    fun `common colors include everyday named colors`() {
        assertTrue(ColorDetector.commonColors.contains(NamedColor.RED))
        assertTrue(ColorDetector.commonColors.contains(NamedColor.GREEN))
        assertTrue(ColorDetector.commonColors.contains(NamedColor.BLUE))
        assertTrue(ColorDetector.commonColors.contains(NamedColor.WHITE))
        assertTrue(ColorDetector.commonColors.contains(NamedColor.BLACK))
        assertTrue(ColorDetector.commonColors.contains(NamedColor.GRAY))
    }

    @Test
    fun `containsGreen applies minimum ratio`() {
        val image = Mat.zeros(4, 4, CvType.CV_8UC3)

        try {
            image.put(0, 0, 0.0, 255.0, 0.0)
            image.put(0, 1, 0.0, 200.0, 50.0)

            assertTrue(ColorDetector.containsGreen(image, minRatio = 0.10))
            assertFalse(ColorDetector.containsGreen(image, minRatio = 0.20))
        } finally {
            image.release()
        }
    }

    @Test
    fun `containsGreen accepts image path`() {
        val image = Mat.zeros(4, 4, CvType.CV_8UC3)

        try {
            image.put(0, 0, 0.0, 255.0, 0.0)
            image.put(0, 1, 0.0, 200.0, 50.0)
            val path = Files.createTempFile("kt-visual-green", ".png")
            require(Imgcodecs.imwrite(path.toString(), image))

            assertTrue(ColorDetector.containsGreen(path, minRatio = 0.10))
        } finally {
            image.release()
        }
    }

    @Test
    fun `containsGreen accepts encoded image bytes`() {
        val image = Mat.zeros(4, 4, CvType.CV_8UC3)

        try {
            image.put(0, 0, 0.0, 255.0, 0.0)
            image.put(0, 1, 0.0, 200.0, 50.0)

            assertTrue(ColorDetector.containsGreen(encodePng(image), minRatio = 0.10))
        } finally {
            image.release()
        }
    }

    @Test
    fun `contains rejects invalid minimum ratio`() {
        val image = Mat.zeros(1, 1, CvType.CV_8UC3)

        try {
            assertFailsWith<IllegalArgumentException> {
                ColorDetector.containsGreen(image, minRatio = -0.1)
            }
            assertFailsWith<IllegalArgumentException> {
                ColorDetector.containsGreen(image, minRatio = 1.1)
            }
        } finally {
            image.release()
        }
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
