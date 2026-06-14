package com.soluna.ktvisual.api

import com.soluna.ktvisual.model.MatchOptions
import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.OcrTextMatchMode
import com.soluna.ktvisual.model.OcrTextMatchOptions
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.UiTarget
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiVisionTest {

    @Test
    fun `click applies offset from matched center`() {
        val fixture = Fixture()
        val input = RecordingInput()
        val vision = UiVision(
            screenSource = SequenceScreenSource(listOf(fixture.screen)),
            input = input
        )

        vision.click(
            target = fixture.target,
            timeout = Duration.ofSeconds(1),
            interval = Duration.ofMillis(1),
            offsetX = 3,
            offsetY = -2
        )

        assertEquals(fixture.templateX + fixture.templateWidth / 2 + 3, input.clicks.single().first)
        assertEquals(fixture.templateY + fixture.templateHeight / 2 - 2, input.clicks.single().second)
    }

    @Test
    fun `doubleClick delegates to input double click with offset`() {
        val fixture = Fixture()
        val input = RecordingInput()
        val vision = UiVision(
            screenSource = SequenceScreenSource(listOf(fixture.screen)),
            input = input
        )

        vision.doubleClick(
            target = fixture.target,
            timeout = Duration.ofSeconds(1),
            interval = Duration.ofMillis(1),
            offsetX = -1,
            offsetY = 4
        )

        assertEquals(fixture.templateX + fixture.templateWidth / 2 - 1, input.doubleClicks.single().first)
        assertEquals(fixture.templateY + fixture.templateHeight / 2 + 4, input.doubleClicks.single().second)
        assertEquals(0, input.clicks.size)
    }

    @Test
    fun `waitGone returns true after target disappears`() {
        val fixture = Fixture()
        val vision = UiVision(
            screenSource = SequenceScreenSource(listOf(fixture.screen, fixture.blankScreen))
        )

        val gone = vision.waitGone(
            target = fixture.target,
            timeout = Duration.ofSeconds(1),
            interval = Duration.ofMillis(1)
        )

        assertTrue(gone)
    }

    @Test
    fun `assertNotVisible fails when target stays visible`() {
        val fixture = Fixture()
        val vision = UiVision(
            screenSource = SequenceScreenSource(listOf(fixture.screen))
        )

        assertFailsWith<VisionException> {
            vision.assertNotVisible(
                target = fixture.target,
                timeout = Duration.ofMillis(30),
                interval = Duration.ofMillis(1)
            )
        }
    }

    @Test
    fun `waitStable returns after repeated stable matches`() {
        val fixture = Fixture()
        val vision = UiVision(
            screenSource = SequenceScreenSource(listOf(fixture.screen))
        )

        val result = vision.waitStable(
            target = fixture.target,
            timeout = Duration.ofSeconds(3),
            interval = Duration.ofMillis(1),
            samples = 2,
            tolerancePx = 0
        )

        assertEquals(fixture.templateX, result.bounds.x)
        assertEquals(fixture.templateY, result.bounds.y)
        assertTrue(result.elapsedMillis > 0)
    }

    @Test
    fun `clickText clicks center of matched OCR result`() {
        val input = RecordingInput()
        val ocr = FakeOcrEngine(
            listOf(
                OcrText(
                    text = "登录",
                    bounds = Region(20, 30, 40, 18),
                    confidence = 0.97
                )
            )
        )
        val vision = UiVision(
            screenSource = SequenceScreenSource(listOf(blankImage(100, 80))),
            input = input,
            ocrEngine = ocr
        )

        val result = vision.clickText("登录")

        assertEquals("登录", result.text)
        assertEquals(40, input.clicks.single().first)
        assertEquals(39, input.clicks.single().second)
    }

    @Test
    fun `findText supports exact mode and confidence filtering`() {
        val ocr = FakeOcrEngine(
            listOf(
                OcrText("登录失败", Region(10, 10, 50, 16), confidence = 0.99),
                OcrText("登录", Region(10, 30, 30, 16), confidence = 0.60),
                OcrText("登录", Region(10, 50, 30, 16), confidence = 0.96)
            )
        )
        val vision = UiVision(
            screenSource = SequenceScreenSource(listOf(blankImage(100, 80))),
            ocrEngine = ocr
        )

        val result = vision.findText(
            query = "登录",
            options = OcrTextMatchOptions(
                mode = OcrTextMatchMode.EXACT,
                minConfidence = 0.90
            )
        )

        assertEquals(50, result?.bounds?.y)
    }

    private class Fixture {
        val templateWidth = 12
        val templateHeight = 10
        val templateX = 23
        val templateY = 17
        val template = patternedImage(templateWidth, templateHeight)
        val screen = blankImage(80, 60).apply {
            paste(template, templateX, templateY)
        }
        val blankScreen = blankImage(80, 60)
        val templatePath: Path = Files.createTempFile("kt-visual-template", ".png")
        val target: UiTarget

        init {
            ImageIO.write(template, "png", templatePath.toFile())
            target = UiTarget(
                name = "fixture.target",
                imagePath = templatePath,
                options = MatchOptions(threshold = 0.99)
            )
        }
    }

    private class SequenceScreenSource(
        private val images: List<BufferedImage>
    ) : ScreenSource {

        private var index = 0

        override fun capture(): BufferedImage {
            val image = images[index.coerceAtMost(images.lastIndex)]
            if (index < images.lastIndex) {
                index += 1
            }
            return image
        }
    }

    private class RecordingInput : UiInput {
        val clicks = mutableListOf<Pair<Int, Int>>()
        val doubleClicks = mutableListOf<Pair<Int, Int>>()

        override fun click(x: Int, y: Int) {
            clicks += x to y
        }

        override fun doubleClick(x: Int, y: Int) {
            doubleClicks += x to y
        }

        override fun type(text: String) = Unit
    }

    private class FakeOcrEngine(
        private val results: List<OcrText>
    ) : OcrEngine {
        override fun recognize(image: BufferedImage, roi: Region?): List<OcrText> {
            return if (roi == null) {
                results
            } else {
                results.filter { text ->
                    text.bounds.center.x in roi.x until roi.x + roi.width &&
                        text.bounds.center.y in roi.y until roi.y + roi.height
                }
            }
        }
    }

    private companion object {
        fun blankImage(width: Int, height: Int): BufferedImage {
            return BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        }

        fun patternedImage(width: Int, height: Int): BufferedImage {
            val image = blankImage(width, height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val red = (140 + x * 13 + y * 2) % 255
                    val green = (80 + x * 5 + y * 11) % 255
                    val blue = (30 + x * 7 + y * 3) % 255
                    image.setRGB(x, y, (red shl 16) or (green shl 8) or blue)
                }
            }
            return image
        }

        fun BufferedImage.paste(source: BufferedImage, x: Int, y: Int) {
            val graphics = createGraphics()
            try {
                graphics.drawImage(source, x, y, null)
            } finally {
                graphics.dispose()
            }
        }
    }
}
