package com.soluna.ktvisual.ocr.multimodal

import com.soluna.ktvisual.api.OcrEngine
import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.Region
import java.awt.image.BufferedImage
import java.time.Duration
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
}
