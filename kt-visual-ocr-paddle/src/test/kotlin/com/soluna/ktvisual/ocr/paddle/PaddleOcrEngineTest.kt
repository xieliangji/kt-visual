package com.soluna.ktvisual.ocr.paddle

import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.Region
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaddleOcrEngineTest {

    @Test
    fun `recognize routes selected languages and restores roi coordinates`() {
        val requests = mutableListOf<PaddleOcrRequest>()
        val runtime = PaddleOcrRuntime { request ->
            requests += request
            listOf(
                OcrText(
                    text = request.languages.joinToString("+") { it.isoCode },
                    bounds = Region(3, 4, 20, 10),
                    confidence = 0.98
                )
            )
        }
        val engine = PaddleOcrEngine.multilingual13(
            runtime = runtime,
            resourceResolver = fakeResolver()
        )

        val result = engine.recognize(
            image = BufferedImage(200, 120, BufferedImage.TYPE_3BYTE_BGR),
            languages = setOf(OcrLanguage.ENGLISH, OcrLanguage.FRENCH, OcrLanguage.THAI),
            roi = Region(10, 20, 100, 50)
        )

        assertEquals(listOf("ppocrv6-cjk-en", "ppocrv5-latin", "ppocrv5-thai"), requests.map { it.model.spec.id })
        assertEquals(3, result.size)
        assertEquals(13, result.first().bounds.x)
        assertEquals(24, result.first().bounds.y)
    }

    @Test
    fun `default engine fails clearly when runtime is unavailable`() {
        val engine = PaddleOcrEngine.multilingual13(resourceResolver = fakeResolver())

        assertFailsWith<PaddleOcrRuntimeUnavailableException> {
            engine.recognize(
                image = BufferedImage(20, 20, BufferedImage.TYPE_3BYTE_BGR),
                languages = setOf(OcrLanguage.ENGLISH)
            )
        }
    }

    private fun fakeResolver(): PaddleOcrResourceResolver {
        val root = Files.createTempDirectory("kt-visual-paddle-ocr-test")
        return PaddleOcrResourceResolver { spec ->
            val detector = Files.createDirectories(root.resolve(spec.id).resolve("det"))
            val recognizer = Files.createDirectories(root.resolve(spec.id).resolve("rec"))
            val dictionary = root.resolve(spec.id).resolve("dict.txt")
            Files.writeString(dictionary, "test")
            ResolvedPaddleOcrModel(spec, detector, recognizer, dictionary)
        }
    }
}
