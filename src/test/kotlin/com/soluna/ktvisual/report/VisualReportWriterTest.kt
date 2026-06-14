package com.soluna.ktvisual.report

import com.soluna.ktvisual.OpenCvRuntime
import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.Region
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs

class VisualReportWriterTest {

    init {
        OpenCvRuntime.ensureLoaded()
    }

    @Test
    fun `writes screenshot match and diff artifacts`() {
        val outputDirectory = Files.createTempDirectory("kt-visual-report-test")
        val writer = VisualReportWriter(outputDirectory)
        val expected = Mat.zeros(20, 30, CvType.CV_8UC3)
        val actual = expected.clone()
        actual.put(5, 5, 255.0, 255.0, 255.0)

        try {
            val screenshot = writer.saveScreenshot("case 1", expected)
            val matches = writer.saveMatches(
                "case 1",
                expected,
                listOf(
                    MatchResult(
                        targetName = "button",
                        bounds = Region(3, 4, 10, 8),
                        score = 0.95,
                        scale = 1.0
                    )
                )
            )
            val heatmap = writer.saveDiffHeatmap("case 1", expected, actual)

            assertTrue(Files.size(screenshot.path) > 0)
            assertTrue(Files.size(matches.path) > 0)
            assertTrue(Files.size(heatmap.path) > 0)
        } finally {
            expected.release()
            actual.release()
        }
    }

    @Test
    fun `writes artifacts from encoded image bytes`() {
        val outputDirectory = Files.createTempDirectory("kt-visual-report-bytes-test")
        val writer = VisualReportWriter(outputDirectory)
        val expected = Mat.zeros(20, 30, CvType.CV_8UC3)
        val actual = expected.clone()
        actual.put(5, 5, 255.0, 255.0, 255.0)

        try {
            val screenshot = writer.saveScreenshot("case bytes", encodePng(expected))
            val heatmap = writer.saveDiffHeatmap(
                "case bytes",
                encodePng(expected),
                encodePng(actual)
            )

            assertTrue(Files.size(screenshot.path) > 0)
            assertTrue(Files.size(heatmap.path) > 0)
        } finally {
            expected.release()
            actual.release()
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
