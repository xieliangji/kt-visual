package com.soluna.ktvisual.api

import com.soluna.ktvisual.OpenCvRuntime
import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.OcrTextMatchMode
import com.soluna.ktvisual.model.OcrTextMatchOptions
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.VisualTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

class VisualTest {

    init {
        OpenCvRuntime.ensureLoaded()
    }

    @Test
    fun `findChangedRegions accepts encoded image bytes`() {
        val expected = Mat.zeros(24, 32, CvType.CV_8UC3)
        val actual = expected.clone()

        try {
            Imgproc.rectangle(actual, Point(10.0, 8.0), Point(16.0, 14.0), Scalar(255.0, 255.0, 255.0), -1)

            val result = Visual.findChangedRegions(
                expected = encodePng(expected),
                actual = encodePng(actual),
                pixelThreshold = 8.0,
                minRegionArea = 4
            )

            assertTrue(result.changedPixels > 0)
            assertTrue(result.regions.isNotEmpty())
        } finally {
            expected.release()
            actual.release()
        }
    }

    @Test
    fun `analyzeQuality and detectTheme accept encoded image bytes`() {
        val dark = Mat.zeros(20, 20, CvType.CV_8UC3)

        try {
            val quality = Visual.analyzeQuality(encodePng(dark))
            val theme = Visual.detectTheme(encodePng(dark))

            assertTrue(quality.isBlank)
            assertTrue(quality.isMostlyDark)
            assertEquals(VisualTheme.DARK, theme.theme)
        } finally {
            dark.release()
        }
    }

    @Test
    fun `findTextBlocks accepts encoded image bytes`() {
        val image = Mat(60, 160, CvType.CV_8UC3, Scalar(255.0, 255.0, 255.0))

        try {
            Imgproc.putText(
                image,
                "Login",
                Point(12.0, 36.0),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.8,
                Scalar(0.0, 0.0, 0.0),
                2
            )

            val blocks = Visual.findTextBlocks(encodePng(image))

            assertTrue(blocks.isNotEmpty())
        } finally {
            image.release()
        }
    }

    @Test
    fun `findText accepts encoded image bytes and OCR engine`() {
        val image = Mat.zeros(16, 16, CvType.CV_8UC3)
        val engine = object : OcrEngine {
            override fun recognize(image: BufferedImage, roi: Region?): List<OcrText> {
                return listOf(
                    OcrText("Cancel", Region(1, 1, 20, 10), confidence = 0.99),
                    OcrText("Continue", Region(1, 20, 35, 10), confidence = 0.95)
                )
            }
        }

        try {
            val result = Visual.findText(
                image = encodePng(image),
                engine = engine,
                query = "continue",
                options = OcrTextMatchOptions(
                    mode = OcrTextMatchMode.EXACT,
                    ignoreCase = true,
                    minConfidence = 0.90
                )
            )

            assertEquals("Continue", result?.text)
        } finally {
            image.release()
        }
    }

    @Test
    fun `waitStable reports stable repeated screenshots`() {
        val image = BufferedImage(12, 12, BufferedImage.TYPE_3BYTE_BGR)
        val source = object : ScreenSource {
            override fun capture(): BufferedImage = image
        }

        val result = Visual.waitStable(source, samples = 2)

        assertTrue(result.stable)
        assertEquals(2, result.samples)
    }

    @Test
    fun `layout assertions fail with clear exceptions`() {
        assertFailsWith<VisionException> {
            VisualAssertions.assertNoOverlap(
                Region(0, 0, 10, 10),
                Region(5, 5, 10, 10)
            )
        }

        val blank = Mat.zeros(20, 20, CvType.CV_8UC3)
        try {
            assertFailsWith<VisionException> {
                VisualAssertions.assertUsableScreenshot(Visual.analyzeQuality(encodePng(blank)))
            }
        } finally {
            blank.release()
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
