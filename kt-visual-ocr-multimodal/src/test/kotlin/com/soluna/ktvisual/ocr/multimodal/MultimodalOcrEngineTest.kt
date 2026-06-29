package com.soluna.ktvisual.ocr.multimodal

import com.soluna.ktvisual.api.OcrEngine
import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.Region
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MultimodalOcrEngineTest {

    @Test
    fun `default prompt constrains model to visible text only`() {
        val prompt = MultimodalOcrOptions.DEFAULT_PROMPT

        assertTrue(prompt.contains("directly visible"))
        assertTrue(prompt.contains("Do not infer"))
        assertTrue(prompt.contains("guess"))
        assertTrue(prompt.contains("translate"))
        assertTrue(prompt.contains("complete text"))
        assertTrue(prompt.contains("omit it instead of guessing"))
        assertTrue(prompt.contains("Return only JSON"))
        assertTrue(prompt.contains("{\"texts\":[]}"))
    }

    @Test
    fun `recognize parses structured json and restores roi coordinates`() {
        val requests = mutableListOf<MultimodalOcrRequest>()
        val client = MultimodalOcrClient { request ->
            requests += request
            """
            ```json
            {
              "texts": [
                {
                  "text": "Login",
                  "confidence": 0.97,
                  "bounds": {"x": 0.10, "y": 0.20, "width": 0.50, "height": 0.30}
                },
                {
                  "text": "Skip",
                  "confidence": 0.25,
                  "bounds": {"x": 0.20, "y": 0.80, "width": 0.30, "height": 0.10}
                }
              ]
            }
            ```
            """.trimIndent()
        }
        val engine = MultimodalOcrEngine(
            client = client,
            options = MultimodalOcrOptions(minConfidence = 0.90)
        )

        val result = engine.recognize(
            image = BufferedImage(200, 100, BufferedImage.TYPE_3BYTE_BGR),
            roi = Region(20, 10, 100, 50)
        )

        assertEquals(1, result.size)
        assertEquals("Login", result.single().text)
        assertEquals(Region(30, 20, 50, 15), result.single().bounds)
        assertEquals(100, requests.single().width)
        assertEquals(50, requests.single().height)
        assertTrue(requests.single().dataUrl().startsWith("data:image/png;base64,"))
    }

    @Test
    fun `recognize supports bbox corners`() {
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient {
                """{"items":[{"text":"OK","score":0.8,"bbox":[0.25,0.25,0.75,0.5]}]}"""
            }
        )

        val result = engine.recognize(BufferedImage(80, 40, BufferedImage.TYPE_3BYTE_BGR))

        assertEquals(OcrText("OK", Region(20, 10, 40, 10), 0.8), result.single())
    }

    @Test
    fun `recognize prefers fenced json over earlier explanatory json`() {
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient {
                """
                Example only: {"texts":[{"text":"Wrong","bounds":{"x":0,"y":0,"width":1,"height":1}}]}

                ```json
                {"texts":[{"text":"Right","bounds":{"x":0,"y":0,"width":1,"height":1}}]}
                ```
                """.trimIndent()
            }
        )

        val result = engine.recognize(BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR))

        assertEquals("Right", result.single().text)
    }

    @Test
    fun `recognize can require confidence when min confidence is configured`() {
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient {
                """
                {
                  "texts": [
                    {"text":"NoScore","bounds":{"x":0,"y":0,"width":1,"height":1}},
                    {"text":"Scored","confidence":0.95,"bounds":{"x":0,"y":0,"width":1,"height":1}}
                  ]
                }
                """.trimIndent()
            },
            options = MultimodalOcrOptions(minConfidence = 0.90, requireConfidence = true)
        )

        val result = engine.recognize(BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR))

        assertEquals(listOf("Scored"), result.map { it.text })
    }

    @Test
    fun `locate text in image part sends only part image and returns full coordinates`() {
        val requests = mutableListOf<MultimodalOcrRequest>()
        val partImage = patternedImage(60, 30)
        val fullImage = BufferedImage(200, 120, BufferedImage.TYPE_3BYTE_BGR)
        fullImage.createGraphics().use { graphics ->
            graphics.color = Color(240, 240, 240)
            graphics.fillRect(0, 0, fullImage.width, fullImage.height)
            graphics.drawImage(partImage, 70, 40, null)
        }
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient { request ->
                requests += request
                """
                {
                  "matches": [
                    {
                      "text": "语言与地区",
                      "confidence": 0.91,
                      "bounds": {"x": 0.20, "y": 0.30, "width": 0.50, "height": 0.20}
                    }
                  ]
                }
                """.trimIndent()
            }
        )

        val result = engine.locateTextInImagePart(
            partImage = pngBytes(partImage),
            fullImage = pngBytes(fullImage),
            target = "language and region settings"
        )

        assertEquals("language and region settings", result?.target)
        assertEquals("语言与地区", result?.text)
        assertEquals(Region(82, 49, 30, 6), result?.bounds)
        assertEquals(0.91, result?.confidence)
        assertEquals(60, requests.single().width)
        assertEquals(30, requests.single().height)

        val prompt = requests.single().prompt
        assertTrue(prompt.contains("match by semantic meaning"))
        assertTrue(prompt.contains("different language"))
        assertTrue(prompt.contains("wraps across multiple lines"))
        assertTrue(prompt.contains("contains the most matching text"))
        assertTrue(prompt.contains("do not translate the returned text"))
        assertTrue(prompt.contains("language and region settings"))
    }

    @Test
    fun `locate text in image part respects confidence filtering`() {
        val partImage = patternedImage(50, 40)
        val fullImage = BufferedImage(120, 100, BufferedImage.TYPE_3BYTE_BGR)
        fullImage.createGraphics().use { graphics ->
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, fullImage.width, fullImage.height)
            graphics.drawImage(partImage, 15, 25, null)
        }
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient {
                """
                {
                  "matches": [
                    {"text":"Low","confidence":0.40,"bounds":{"x":0,"y":0,"width":0.2,"height":0.2}},
                    {"text":"First","confidence":0.95,"bounds":{"x":0.1,"y":0.1,"width":0.2,"height":0.2}},
                    {"text":"Second","confidence":0.96,"bounds":{"x":0.4,"y":0.1,"width":0.2,"height":0.2}}
                  ]
                }
                """.trimIndent()
            }
        )

        val result = engine.locateTextInImagePart(
            partImage = pngBytes(partImage),
            fullImage = pngBytes(fullImage),
            target = "confirm button",
            locationOptions = MultimodalTextLocationOptions(minConfidence = 0.90)
        )

        assertEquals("First", result?.text)
        assertEquals(Region(20, 29, 10, 8), result?.bounds)
    }

    @Test
    fun `locate text in image part can require confidence`() {
        val partImage = patternedImage(50, 40)
        val fullImage = BufferedImage(120, 100, BufferedImage.TYPE_3BYTE_BGR)
        fullImage.createGraphics().use { graphics ->
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, fullImage.width, fullImage.height)
            graphics.drawImage(partImage, 15, 25, null)
        }
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient {
                """
                {
                  "matches": [
                    {"text":"NoScore","bounds":{"x":0.10,"y":0.20,"width":0.30,"height":0.40}}
                  ]
                }
                """.trimIndent()
            }
        )

        val result = engine.locateTextInImagePart(
            partImage = pngBytes(partImage),
            fullImage = pngBytes(fullImage),
            target = "wifi settings",
            locationOptions = MultimodalTextLocationOptions(
                minConfidence = 0.9,
                requireConfidence = true
            )
        )

        assertEquals(null, result)
    }

    @Test
    fun `locate text in encoded image part returns null when part is not found`() {
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient {
                error("Model should not be called when part image cannot be found in full image.")
            }
        )

        val result = engine.locateTextInImagePart(
            partImage = pngBytes(patternedImage(20, 20)),
            fullImage = pngBytes(BufferedImage(80, 80, BufferedImage.TYPE_3BYTE_BGR)),
            target = "anything"
        )

        assertEquals(null, result)
    }

    @Test
    fun `locate text in image part accepts Chinese semantic target for foreign visible text`() {
        val requests = mutableListOf<MultimodalOcrRequest>()
        val partImage = patternedImage(60, 30)
        val fullImage = BufferedImage(200, 120, BufferedImage.TYPE_3BYTE_BGR)
        fullImage.createGraphics().use { graphics ->
            graphics.color = Color(240, 240, 240)
            graphics.fillRect(0, 0, fullImage.width, fullImage.height)
            graphics.drawImage(partImage, 70, 40, null)
        }
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient { request ->
                requests += request
                """{"matches":[{"text":"Politique de confidentialité","confidence":0.99,"bounds":{"x":0.1,"y":0.2,"width":0.5,"height":0.4}}]}"""
            }
        )

        val result = engine.locateTextInImagePart(
            partImage = pngBytes(partImage),
            fullImage = pngBytes(fullImage),
            target = "用户协议、隐私政策、个人信息收集清单的同意文本"
        )

        assertEquals("Politique de confidentialité", result?.text)
        assertTrue(requests.single().prompt.contains("用户协议、隐私政策、个人信息收集清单的同意文本"))
        assertTrue(requests.single().prompt.contains("different language"))
    }

    @Test
    fun `recognize retries malformed model response`() {
        var attempts = 0
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient {
                attempts += 1
                if (attempts == 1) {
                    "The image contains Settings."
                } else {
                    """{"texts":[{"text":"Settings","bounds":{"x":0,"y":0,"width":1,"height":1}}]}"""
                }
            },
            options = MultimodalOcrOptions(
                retry = MultimodalOcrRetryOptions(
                    maxAttempts = 2,
                    initialDelay = Duration.ZERO
                )
            )
        )

        val result = engine.recognize(BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR))

        assertEquals(2, attempts)
        assertEquals("Settings", result.single().text)
    }

    @Test
    fun `recognize can retry empty result before fallback`() {
        var attempts = 0
        val fallback = RecordingFallback(listOf(OcrText("Fallback", Region(1, 2, 3, 4))))
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient {
                attempts += 1
                if (attempts == 1) {
                    """{"texts":[]}"""
                } else {
                    """{"texts":[{"text":"Retried","bounds":{"x":0,"y":0,"width":1,"height":1}}]}"""
                }
            },
            options = MultimodalOcrOptions(
                retry = MultimodalOcrRetryOptions(
                    maxAttempts = 2,
                    initialDelay = Duration.ZERO,
                    retryOnEmptyResult = true
                )
            ),
            fallback = fallback
        )

        val result = engine.recognize(BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR))

        assertEquals(2, attempts)
        assertEquals("Retried", result.single().text)
    }

    @Test
    fun `recognize falls back when model returns no text`() {
        val fallback = RecordingFallback(listOf(OcrText("Fallback", Region(1, 2, 3, 4), confidence = 0.7)))
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient { """{"texts":[]}""" },
            fallback = fallback
        )

        val result = engine.recognize(BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR), Region(2, 3, 10, 10))

        assertEquals("Fallback", result.single().text)
        assertEquals(Region(2, 3, 10, 10), fallback.roi)
    }

    @Test
    fun `recognize falls back on client error when configured`() {
        val fallback = RecordingFallback(listOf(OcrText("Safe", Region(1, 1, 6, 6))))
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient { throw MultimodalOcrException("boom") },
            fallback = fallback
        )

        val result = engine.recognize(BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR))

        assertEquals("Safe", result.single().text)
    }

    @Test
    fun `recognize propagates client error when fallback is disabled`() {
        val engine = MultimodalOcrEngine(
            client = MultimodalOcrClient { throw MultimodalOcrException("boom") },
            options = MultimodalOcrOptions(useFallbackOnClientError = false),
            fallback = RecordingFallback(emptyList())
        )

        assertFailsWith<MultimodalOcrException> {
            engine.recognize(BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR))
        }
    }

    private class RecordingFallback(private val results: List<OcrText>) : OcrEngine {
        var roi: Region? = null

        override fun recognize(image: BufferedImage, roi: Region?): List<OcrText> {
            this.roi = roi
            return results
        }
    }

    private fun pngBytes(image: BufferedImage): ByteArray {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    private fun patternedImage(width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        image.createGraphics().use { graphics ->
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.color = Color.BLACK
            graphics.fillRect(3, 4, width / 2, height / 3)
            graphics.color = Color(30, 120, 220)
            graphics.fillOval(width / 2, height / 3, width / 3, height / 2)
        }
        return image
    }

    private inline fun <T> java.awt.Graphics2D.use(block: (java.awt.Graphics2D) -> T): T {
        return try {
            block(this)
        } finally {
            dispose()
        }
    }
}
