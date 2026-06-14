package com.soluna.ktvisual.ocr.paddle

import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PaddleOcrResourceManagerTest {

    @Test
    fun `resolve copies packaged model resources into cache`() {
        val classpathRoot = Files.createTempDirectory("kt-visual-paddle-classpath")
        val cache = Files.createTempDirectory("kt-visual-paddle-cache")
        Files.createDirectories(classpathRoot.resolve("models/paddleocr/ppocrv6-det/det"))
        Files.createDirectories(classpathRoot.resolve("models/paddleocr/ppocrv6-cjk-en/rec"))
        Files.writeString(
            classpathRoot.resolve("models/paddleocr/ppocrv6-det/det/inference.onnx"),
            "detector"
        )
        Files.writeString(
            classpathRoot.resolve("models/paddleocr/ppocrv6-cjk-en/rec/inference.onnx"),
            "recognizer"
        )
        Files.writeString(
            classpathRoot.resolve("models/paddleocr/ppocrv6-cjk-en/inference.yml"),
            "dictionary"
        )

        val classLoader = URLClassLoader(arrayOf(classpathRoot.toUri().toURL()), null)
        val manager = PaddleOcrResourceManager(
            cacheDirectory = cache,
            classLoader = classLoader,
            downloadMissingResources = false
        )

        val resolved = manager.resolve(PaddleOcrModelCatalog.cjkAndEnglish)

        assertEquals("detector", Files.readString(resolved.detectorDirectory.resolve("inference.onnx")))
        assertEquals("recognizer", Files.readString(resolved.recognizerDirectory.resolve("inference.onnx")))
        assertEquals("dictionary", Files.readString(resolved.dictionaryPath))
    }

    @Test
    fun `resolve fails clearly when resources are missing and downloads are disabled`() {
        val manager = PaddleOcrResourceManager(
            cacheDirectory = Files.createTempDirectory("kt-visual-paddle-cache"),
            classLoader = URLClassLoader(emptyArray(), null),
            downloadMissingResources = false
        )

        val error = assertFailsWith<MissingPaddleOcrResourceException> {
            manager.resolve(PaddleOcrModelCatalog.cjkAndEnglish)
        }

        assertTrue(error.message.orEmpty().contains("Missing Paddle OCR"))
    }
}
