package com.soluna.ktvisual.cv

import com.soluna.ktvisual.OpenCvRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.opencv.imgcodecs.Imgcodecs
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class TemplateCacheTest {

    init {
        OpenCvRuntime.ensureLoaded()
    }

    @Test
    fun `get reuses unchanged template mat`() {
        val directory = Files.createTempDirectory("kt-visual-cache-test")
        val imagePath = directory.resolve("template.png")
        val template = patternedTemplate(width = 12, height = 10)

        try {
            writeImage(imagePath, template)

            TemplateCache().use { cache ->
                val first = cache.get(imagePath)
                val second = cache.get(imagePath)

                assertSame(first, second)
                assertEquals(1, cache.size)
            }
        } finally {
            template.release()
        }
    }

    @Test
    fun `get reloads template when file metadata changes`() {
        val directory = Files.createTempDirectory("kt-visual-cache-test")
        val imagePath = directory.resolve("template.png")
        val firstTemplate = patternedTemplate(width = 12, height = 10)
        val secondTemplate = patternedTemplate(width = 16, height = 14)

        try {
            writeImage(imagePath, firstTemplate)

            TemplateCache().use { cache ->
                val first = cache.get(imagePath)

                writeImage(imagePath, secondTemplate)
                Files.setLastModifiedTime(
                    imagePath,
                    FileTime.fromMillis(Files.getLastModifiedTime(imagePath).toMillis() + 2_000)
                )

                val second = cache.get(imagePath)

                assertFalse(first === second)
                assertEquals(16, second.cols())
                assertEquals(14, second.rows())
                assertEquals(1, cache.size)
            }
        } finally {
            firstTemplate.release()
            secondTemplate.release()
        }
    }

    @Test
    fun `clear releases cached templates`() {
        val directory = Files.createTempDirectory("kt-visual-cache-test")
        val imagePath = directory.resolve("template.png")
        val template = patternedTemplate(width = 12, height = 10)

        try {
            writeImage(imagePath, template)

            val cache = TemplateCache()
            cache.get(imagePath)
            assertEquals(1, cache.size)

            cache.clear()

            assertEquals(0, cache.size)
        } finally {
            template.release()
        }
    }

    private fun patternedTemplate(width: Int, height: Int) =
        TemplateTestImages.patterned(width, height)

    private fun writeImage(path: Path, image: org.opencv.core.Mat) {
        assertTrue(Imgcodecs.imwrite(path.toString(), image))
    }
}
