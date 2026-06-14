package com.soluna.ktvisual.cv

import com.soluna.ktvisual.OpenCvRuntime
import com.soluna.ktvisual.model.MatchOptions
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.UiTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.nio.file.Files
import java.nio.file.Path

class TemplateLocatorTest {

    private val locator = TemplateLocator()

    init {
        OpenCvRuntime.ensureLoaded()
    }

    @Test
    fun `find returns match bounds and score`() {
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 80, height = 60)
        paste(template, screen, x = 23, y = 17)

        try {
            val result = locator.find(
                screen = screen,
                template = template,
                targetName = "target",
                options = MatchOptions(threshold = 0.99)
            )

            assertNotNull(result)
            assertEquals("target", result.targetName)
            assertEquals(23, result.bounds.x)
            assertEquals(17, result.bounds.y)
            assertEquals(12, result.bounds.width)
            assertEquals(10, result.bounds.height)
            assertTrue(result.score >= 0.99)
            assertEquals(1.0, result.scale)
            assertTrue(result.elapsedMillis > 0)
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find accepts encoded screenshot and template bytes`() {
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 80, height = 60)
        paste(template, screen, x = 23, y = 17)

        try {
            val result = locator.find(
                screen = encodePng(screen),
                template = encodePng(template),
                targetName = "target",
                options = MatchOptions(threshold = 0.99)
            )

            assertNotNull(result)
            assertEquals(23, result.bounds.x)
            assertEquals(17, result.bounds.y)
            assertTrue(result.score >= 0.99)
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find returns null when score is below threshold`() {
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 80, height = 60)

        try {
            val result = locator.find(
                screen = screen,
                template = template,
                targetName = "target",
                options = MatchOptions(threshold = 0.99)
            )

            assertNull(result)
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find applies roi offset to returned bounds`() {
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 100, height = 80)
        paste(template, screen, x = 42, y = 31)

        try {
            val result = locator.find(
                screen = screen,
                template = template,
                targetName = "target",
                options = MatchOptions(
                    threshold = 0.99,
                    roi = Region(x = 30, y = 20, width = 50, height = 40)
                )
            )

            assertNotNull(result)
            assertEquals(42, result.bounds.x)
            assertEquals(31, result.bounds.y)
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find supports multi scale matching`() {
        val template = patternedTemplate(width = 12, height = 10)
        val scaled = TemplateTestImages.resize(template, scale = 1.5)
        val screen = blankScreen(width = 100, height = 80)
        paste(scaled, screen, x = 37, y = 29)

        try {
            val result = locator.find(
                screen = screen,
                template = template,
                targetName = "target",
                options = MatchOptions(
                    threshold = 0.99,
                    scales = listOf(1.0, 1.5)
                )
            )

            assertNotNull(result)
            assertEquals(37, result.bounds.x)
            assertEquals(29, result.bounds.y)
            assertEquals(18, result.bounds.width)
            assertEquals(15, result.bounds.height)
            assertEquals(1.5, result.scale)
        } finally {
            screen.release()
            scaled.release()
            template.release()
        }
    }

    @Test
    fun `find supports edge based matching`() {
        val template = edgeTemplate(width = 20, height = 18)
        val screen = blankScreen(width = 80, height = 60)
        paste(template, screen, x = 28, y = 19)

        try {
            val result = locator.find(
                screen = screen,
                template = template,
                targetName = "target",
                options = MatchOptions(
                    threshold = 0.80,
                    method = Imgproc.TM_CCORR_NORMED,
                    edgeDetection = true
                )
            )

            assertNotNull(result)
            assertEquals(28, result.bounds.x)
            assertEquals(19, result.bounds.y)
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find matches similar scaled back arrow by edges`() {
        val template = backArrowTemplate(size = 32, inset = 8, thickness = 3)
        val actualIcon = backArrowTemplate(size = 32, inset = 7, thickness = 4)
        val scaledActualIcon = TemplateTestImages.resize(actualIcon, scale = 1.25)
        val screen = whiteScreen(width = 120, height = 90)
        paste(scaledActualIcon, screen, x = 39, y = 24)

        try {
            writeBackArrowArtifacts(
                template = template,
                actualIcon = actualIcon,
                scaledActualIcon = scaledActualIcon,
                screen = screen
            )

            val result = locator.find(
                screen = screen,
                template = template,
                targetName = "backArrow",
                options = MatchOptions(
                    threshold = 0.45,
                    scales = listOf(1.0, 1.25),
                    method = Imgproc.TM_CCORR_NORMED,
                    edgeDetection = true
                )
            )

            assertNotNull(result)
            writeBackArrowMatchReport(
                x = result.bounds.x,
                y = result.bounds.y,
                width = result.bounds.width,
                height = result.bounds.height,
                centerX = result.center.x,
                centerY = result.center.y,
                score = result.score,
                scale = result.scale,
                elapsedMillis = result.elapsedMillis,
                expectedX = 39,
                expectedY = 24
            )
            assertTrue(kotlin.math.abs(result.bounds.x - 39) <= 4)
            assertTrue(kotlin.math.abs(result.bounds.y - 24) <= 4)
            assertEquals(40, result.bounds.width)
            assertEquals(40, result.bounds.height)
            assertEquals(1.25, result.scale)
        } finally {
            screen.release()
            scaledActualIcon.release()
            actualIcon.release()
            template.release()
        }
    }

    @Test
    fun `findAll returns multiple non-overlapping matches`() {
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 100, height = 80)
        paste(template, screen, x = 12, y = 14)
        paste(template, screen, x = 61, y = 43)

        try {
            val results = locator.findAll(
                screen = screen,
                template = template,
                targetName = "target",
                options = MatchOptions(threshold = 0.99)
            )

            assertEquals(2, results.size)
            assertTrue(results.any { it.bounds.x == 12 && it.bounds.y == 14 })
            assertTrue(results.any { it.bounds.x == 61 && it.bounds.y == 43 })
            assertTrue(results.all { it.elapsedMillis > 0 })
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `findAll respects max matches`() {
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 120, height = 90)
        paste(template, screen, x = 12, y = 14)
        paste(template, screen, x = 50, y = 30)
        paste(template, screen, x = 88, y = 56)

        try {
            val results = locator.findAll(
                screen = screen,
                template = template,
                targetName = "target",
                options = MatchOptions(
                    threshold = 0.99,
                    maxMatches = 2
                )
            )

            assertEquals(2, results.size)
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find loads file backed target through cache`() {
        val directory = Files.createTempDirectory("kt-visual-locator-test")
        val templatePath = directory.resolve("template.png")
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 80, height = 60)
        paste(template, screen, x = 24, y = 18)

        try {
            assertTrue(Imgcodecs.imwrite(templatePath.toString(), template))

            TemplateLocator().use { locator ->
                val result = locator.find(
                    screen = screen,
                    target = UiTarget(
                        name = "fileTarget",
                        imagePath = templatePath,
                        options = MatchOptions(threshold = 0.99)
                    )
                )

                assertNotNull(result)
                assertEquals("fileTarget", result.targetName)
                assertEquals(24, result.bounds.x)
                assertEquals(18, result.bounds.y)
            }
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find uses alternate target template when primary template does not match`() {
        val directory = Files.createTempDirectory("kt-visual-locator-test")
        val primaryPath = directory.resolve("primary.png")
        val alternatePath = directory.resolve("alternate.png")
        val primaryTemplate = patternedTemplate(width = 12, height = 10)
        val alternateTemplate = alternatePatternedTemplate(width = 15, height = 13)
        val screen = blankScreen(width = 90, height = 70)
        paste(alternateTemplate, screen, x = 33, y = 22)

        try {
            assertTrue(Imgcodecs.imwrite(primaryPath.toString(), primaryTemplate))
            assertTrue(Imgcodecs.imwrite(alternatePath.toString(), alternateTemplate))

            TemplateLocator().use { locator ->
                val result = locator.find(
                    screen = screen,
                    target = UiTarget(
                        name = "multiTarget",
                        imagePath = primaryPath,
                        alternateImagePaths = listOf(alternatePath),
                        options = MatchOptions(threshold = 0.99)
                    )
                )

                assertNotNull(result)
                assertEquals(33, result.bounds.x)
                assertEquals(22, result.bounds.y)
                assertEquals(15, result.bounds.width)
                assertEquals(13, result.bounds.height)
            }
        } finally {
            screen.release()
            primaryTemplate.release()
            alternateTemplate.release()
        }
    }

    @Test
    fun `findAll combines matches from primary and alternate templates`() {
        val directory = Files.createTempDirectory("kt-visual-locator-test")
        val primaryPath = directory.resolve("primary.png")
        val alternatePath = directory.resolve("alternate.png")
        val primaryTemplate = patternedTemplate(width = 12, height = 10)
        val alternateTemplate = alternatePatternedTemplate(width = 15, height = 13)
        val screen = blankScreen(width = 120, height = 90)
        paste(primaryTemplate, screen, x = 18, y = 16)
        paste(alternateTemplate, screen, x = 72, y = 49)

        try {
            assertTrue(Imgcodecs.imwrite(primaryPath.toString(), primaryTemplate))
            assertTrue(Imgcodecs.imwrite(alternatePath.toString(), alternateTemplate))

            TemplateLocator().use { locator ->
                val results = locator.findAll(
                    screen = screen,
                    target = UiTarget(
                        name = "multiTarget",
                        imagePath = primaryPath,
                        alternateImagePaths = listOf(alternatePath),
                        options = MatchOptions(threshold = 0.99)
                    )
                )

                assertEquals(2, results.size)
                assertTrue(results.any { it.bounds.x == 18 && it.bounds.y == 16 })
                assertTrue(results.any { it.bounds.x == 72 && it.bounds.y == 49 })
            }
        } finally {
            screen.release()
            primaryTemplate.release()
            alternateTemplate.release()
        }
    }

    @Test
    fun `find writes matched debug image when debug is enabled`() {
        val debugDirectory = Files.createTempDirectory("kt-visual-debug-test")
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 80, height = 60)
        paste(template, screen, x = 23, y = 17)

        try {
            val result = locator.find(
                screen = screen,
                template = template,
                targetName = "target.matched",
                options = MatchOptions(
                    threshold = 0.99,
                    debug = true,
                    debugDirectory = debugDirectory
                )
            )

            assertNotNull(result)

            val files = pngFiles(debugDirectory)
            assertEquals(1, files.size)
            assertTrue(files.single().fileName.toString().contains("target.matched"))
            assertTrue(Files.size(files.single()) > 0)
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find writes failed debug image when debug is enabled and target is not found`() {
        val debugDirectory = Files.createTempDirectory("kt-visual-debug-test")
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 80, height = 60)

        try {
            val result = locator.find(
                screen = screen,
                template = template,
                targetName = "target.failed",
                options = MatchOptions(
                    threshold = 0.99,
                    debug = true,
                    debugDirectory = debugDirectory
                )
            )

            assertNull(result)

            val files = pngFiles(debugDirectory)
            assertEquals(1, files.size)
            assertTrue(files.single().fileName.toString().contains("target.failed"))
            assertTrue(files.single().fileName.toString().contains("failed"))
            assertTrue(Files.size(files.single()) > 0)
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find rejects roi outside screen`() {
        val template = patternedTemplate(width = 12, height = 10)
        val screen = blankScreen(width = 80, height = 60)

        try {
            assertFailsWith<IllegalArgumentException> {
                locator.find(
                    screen = screen,
                    template = template,
                    targetName = "target",
                    options = MatchOptions(
                        roi = Region(x = 70, y = 20, width = 20, height = 20)
                    )
                )
            }
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `find returns null when template is larger than screen`() {
        val template = patternedTemplate(width = 40, height = 40)
        val screen = blankScreen(width = 20, height = 20)

        try {
            val result = locator.find(
                screen = screen,
                template = template,
                targetName = "target",
                options = MatchOptions()
            )

            assertNull(result)
        } finally {
            screen.release()
            template.release()
        }
    }

    @Test
    fun `match options rejects invalid values`() {
        assertFailsWith<IllegalArgumentException> {
            MatchOptions(threshold = -0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            MatchOptions(threshold = 1.1)
        }
        assertFailsWith<IllegalArgumentException> {
            MatchOptions(scales = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            MatchOptions(scales = listOf(1.0, 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            MatchOptions(method = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            MatchOptions(maxMatches = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            MatchOptions(overlapThreshold = -0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            MatchOptions(overlapThreshold = 1.1)
        }
        assertFailsWith<IllegalArgumentException> {
            UiTarget(
                name = "invalid",
                imagePath = Path.of("template.png"),
                alternateImagePaths = listOf(Path.of("template.png"))
            )
        }
    }

    private fun blankScreen(width: Int, height: Int): Mat {
        return Mat.zeros(height, width, CvType.CV_8UC3)
    }

    private fun whiteScreen(width: Int, height: Int): Mat {
        return Mat(height, width, CvType.CV_8UC3, Scalar(255.0, 255.0, 255.0))
    }

    private fun patternedTemplate(width: Int, height: Int): Mat {
        return TemplateTestImages.patterned(width, height)
    }

    private fun alternatePatternedTemplate(width: Int, height: Int): Mat {
        val template = Mat(height, width, CvType.CV_8UC3)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val blue = (190 + x * y + y * 17) % 255
                val green = (40 + x * x + y * 9) % 255
                val red = (210 + y * y + x * 4) % 255
                template.put(y, x, blue.toDouble(), green.toDouble(), red.toDouble())
            }
        }

        return template
    }

    private fun edgeTemplate(width: Int, height: Int): Mat {
        val template = Mat.zeros(height, width, CvType.CV_8UC3)
        Imgproc.rectangle(
            template,
            Point(4.0, 4.0),
            Point((width - 5).toDouble(), (height - 5).toDouble()),
            Scalar(255.0, 255.0, 255.0),
            2
        )
        return template
    }

    private fun backArrowTemplate(size: Int, inset: Int, thickness: Int): Mat {
        val template = Mat(size, size, CvType.CV_8UC3, Scalar(255.0, 255.0, 255.0))
        val midY = size / 2.0
        val left = inset.toDouble()
        val right = (size - inset).toDouble()
        Imgproc.line(template, Point(right, midY), Point(left, midY), Scalar(0.0, 0.0, 0.0), thickness)
        Imgproc.line(template, Point(left, midY), Point(size * 0.45, inset.toDouble()), Scalar(0.0, 0.0, 0.0), thickness)
        Imgproc.line(template, Point(left, midY), Point(size * 0.45, (size - inset).toDouble()), Scalar(0.0, 0.0, 0.0), thickness)
        return template
    }

    private fun writeBackArrowArtifacts(
        template: Mat,
        actualIcon: Mat,
        scaledActualIcon: Mat,
        screen: Mat
    ) {
        val directory = Path.of("build", "test-artifacts", "back-arrow")
        Files.createDirectories(directory)
        assertTrue(Imgcodecs.imwrite(directory.resolve("template-arrow.png").toString(), template))
        assertTrue(Imgcodecs.imwrite(directory.resolve("similar-arrow.png").toString(), actualIcon))
        assertTrue(Imgcodecs.imwrite(directory.resolve("similar-arrow-scale-1.25.png").toString(), scaledActualIcon))
        assertTrue(Imgcodecs.imwrite(directory.resolve("screen-with-scaled-arrow.png").toString(), screen))
    }

    private fun writeBackArrowMatchReport(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        score: Double,
        scale: Double,
        elapsedMillis: Long,
        expectedX: Int,
        expectedY: Int
    ) {
        val directory = Path.of("build", "test-artifacts", "back-arrow")
        Files.createDirectories(directory)
        val report = """
            {
              "targetName": "backArrow",
              "bounds": {
                "x": $x,
                "y": $y,
                "width": $width,
                "height": $height
              },
              "center": {
                "x": $centerX,
                "y": $centerY
              },
              "score": $score,
              "scale": $scale,
              "elapsedMillis": $elapsedMillis,
              "expected": {
                "x": $expectedX,
                "y": $expectedY
              },
              "delta": {
                "x": ${x - expectedX},
                "y": ${y - expectedY}
              }
            }
        """.trimIndent()

        Files.writeString(directory.resolve("match-result.json"), report)
    }

    private fun paste(source: Mat, destination: Mat, x: Int, y: Int) {
        val roi = destination.submat(Rect(x, y, source.cols(), source.rows()))
        try {
            source.copyTo(roi)
        } finally {
            roi.release()
        }
    }

    private fun pngFiles(directory: Path): List<Path> {
        return Files.list(directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".png") }.toList()
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
