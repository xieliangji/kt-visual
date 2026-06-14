package com.soluna.ktvisual.ocr.paddle

import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaddleOcrCliRuntimeTest {

    @Test
    fun `recognize parses jsonl command output`() {
        val script = Files.createTempFile("kt-visual-fake-paddleocr", ".sh")
        Files.writeString(
            script,
            """
            #!/usr/bin/env bash
            echo '{"text":"Login","confidence":0.98,"bounds":{"x":10,"y":20,"width":80,"height":24}}'
            echo '{"text":"Cancel","confidence":0.88,"bounds":{"x":12,"y":50,"width":90,"height":24}}'
            """.trimIndent()
        )
        script.toFile().setExecutable(true)

        val runtime = PaddleOcrCliRuntime(listOf(script.toAbsolutePath().toString()))
        val model = fakeResolvedModel()

        val results = runtime.recognize(
            PaddleOcrRequest(
                image = BufferedImage(120, 80, BufferedImage.TYPE_3BYTE_BGR),
                model = model,
                languages = setOf(OcrLanguage.ENGLISH)
            )
        )

        assertEquals(2, results.size)
        assertEquals("Login", results.first().text)
        assertEquals(10, results.first().bounds.x)
        assertEquals(0.98, results.first().confidence)
    }

    @Test
    fun `recognize passes image model and language arguments`() {
        val output = Files.createTempFile("kt-visual-paddleocr-args", ".txt")
        val script = Files.createTempFile("kt-visual-fake-paddleocr-args", ".sh")
        Files.writeString(
            script,
            """
            #!/usr/bin/env bash
            printf '%s\n' "$@" > "${output.toAbsolutePath()}"
            echo '{"text":"OK","confidence":0.99,"bounds":{"x":1,"y":2,"width":3,"height":4}}'
            """.trimIndent()
        )
        script.toFile().setExecutable(true)

        val runtime = PaddleOcrCliRuntime(listOf(script.toAbsolutePath().toString()))
        runtime.recognize(
            PaddleOcrRequest(
                image = BufferedImage(10, 10, BufferedImage.TYPE_3BYTE_BGR),
                model = fakeResolvedModel(),
                languages = setOf(OcrLanguage.ENGLISH, OcrLanguage.FRENCH)
            )
        )

        val args = Files.readString(output)
        assertTrue(args.contains("--image"))
        assertTrue(args.contains("--model"))
        assertTrue(args.contains("ppocrv6-cjk-en"))
        assertTrue(args.contains("--languages"))
        assertTrue(args.contains("en,fr"))
    }

    private fun fakeResolvedModel(): ResolvedPaddleOcrModel {
        val root = Files.createTempDirectory("kt-visual-paddle-cli-test")
        val detector = Files.createDirectories(root.resolve("det"))
        val recognizer = Files.createDirectories(root.resolve("rec"))
        val dictionary = root.resolve("dict.txt")
        Files.writeString(dictionary, "test")
        return ResolvedPaddleOcrModel(
            spec = PaddleOcrModelCatalog.cjkAndEnglish,
            detectorDirectory = detector,
            recognizerDirectory = recognizer,
            dictionaryPath = dictionary
        )
    }
}
