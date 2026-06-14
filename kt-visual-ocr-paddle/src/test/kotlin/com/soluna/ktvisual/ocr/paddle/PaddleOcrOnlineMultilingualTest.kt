package com.soluna.ktvisual.ocr.paddle

import com.soluna.ktvisual.model.Region
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class PaddleOcrOnlineMultilingualTest {

    @Test
    fun `recognize apple support settings screenshots in thirteen languages`() {
        if (System.getenv("KT_VISUAL_RUN_ONLINE_MULTILINGUAL_OCR") != "true") {
            return
        }

        val engine = PaddleOcrEngine.multilingual13(
            runtime = PaddleOcrOnnxRuntime(),
            resourceResolver = PaddleOcrResourceManager(
                cacheDirectory = Path.of("build", "real-ocr-models-cache").toAbsolutePath().normalize()
            )
        )

        engine.use {
            samples.forEach { sample ->
                val image = ImageIO.read(download(sample))
                val results = it.recognize(
                    image = image,
                    languages = setOf(sample.language),
                    roi = sample.roi
                )
                val recognized = results.joinToString(" ") { text -> text.text }
                println("${sample.id}: $recognized")

                assertTrue(
                    matchesAnyExpected(recognized, sample.expected),
                    "Expected ${sample.id} OCR to contain one of ${sample.expected}, actual: $recognized"
                )
            }
        }
    }

    private fun download(sample: OnlineOcrSample): java.io.File {
        val target = Path.of("build", "online-ocr-samples", "${sample.id}-${sample.name}.png")
            .toAbsolutePath()
            .normalize()
        if (Files.exists(target) && Files.size(target) > 0) return target.toFile()

        Files.createDirectories(target.parent)
        URI.create(sample.url).toURL().openStream().use { input ->
            Files.newOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target.toFile()
    }

    private fun normalized(value: String): String {
        return value.lowercase().filterNot { it.isWhitespace() }
    }

    private fun matchesAnyExpected(recognized: String, expected: List<String>): Boolean {
        val normalizedRecognized = normalized(recognized)
        val tokens = recognized.split(Regex("\\s+"))
            .map(::normalized)
            .filter { it.isNotEmpty() }
        return expected.any { expectedText ->
            val normalizedExpected = normalized(expectedText)
            normalizedRecognized.contains(normalizedExpected) ||
                tokens.any { token -> levenshtein(token, normalizedExpected) <= editTolerance(normalizedExpected) }
        }
    }

    private fun editTolerance(value: String): Int {
        return when {
            value.length <= 2 -> 0
            value.length <= 5 -> 1
            else -> 2
        }
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val substitution = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + substitution
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    private data class OnlineOcrSample(
        val id: String,
        val name: String,
        val language: OcrLanguage,
        val url: String,
        val expected: List<String>,
        val roi: Region
    )

    private companion object {
        private const val BASE = "https://cdsassets.apple.com/live/7WUAS350/images/ios"
        private val samples = listOf(
            OnlineOcrSample(
                id = "zh-cn",
                name = "language-region",
                language = OcrLanguage.SIMPLIFIED_CHINESE,
                url = "$BASE/locale/zh-cn/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("通用"),
                roi = Region(230, 90, 130, 50)
            ),
            OnlineOcrSample(
                id = "en",
                name = "language-region",
                language = OcrLanguage.ENGLISH,
                url = "$BASE/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("General"),
                roi = Region(220, 90, 160, 50)
            ),
            OnlineOcrSample(
                id = "ko-kr",
                name = "language-region",
                language = OcrLanguage.KOREAN,
                url = "$BASE/locale/ko-kr/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("일반"),
                roi = Region(230, 90, 130, 50)
            ),
            OnlineOcrSample(
                id = "ja-jp",
                name = "language-region",
                language = OcrLanguage.JAPANESE,
                url = "$BASE/locale/ja-jp/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("一般"),
                roi = Region(230, 90, 130, 50)
            ),
            OnlineOcrSample(
                id = "de-de",
                name = "language-region",
                language = OcrLanguage.GERMAN,
                url = "$BASE/locale/de-de/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Allgemein"),
                roi = Region(185, 90, 220, 50)
            ),
            OnlineOcrSample(
                id = "fr-fr",
                name = "language-region",
                language = OcrLanguage.FRENCH,
                url = "$BASE/locale/fr-fr/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Général", "Génral", "General"),
                roi = Region(205, 90, 180, 50)
            ),
            OnlineOcrSample(
                id = "es-es",
                name = "language-region",
                language = OcrLanguage.SPANISH,
                url = "$BASE/locale/es-es/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("General"),
                roi = Region(205, 90, 180, 50)
            ),
            OnlineOcrSample(
                id = "pt-br",
                name = "language-region",
                language = OcrLanguage.PORTUGUESE,
                url = "$BASE/locale/pt-br/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Geral"),
                roi = Region(220, 90, 160, 50)
            ),
            OnlineOcrSample(
                id = "ru-ru",
                name = "language-region",
                language = OcrLanguage.RUSSIAN,
                url = "$BASE/locale/ru-ru/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Основные"),
                roi = Region(175, 90, 240, 50)
            ),
            OnlineOcrSample(
                id = "th-th",
                name = "language-region",
                language = OcrLanguage.THAI,
                url = "$BASE/locale/th-th/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("ทั่วไป"),
                roi = Region(205, 90, 180, 50)
            ),
            OnlineOcrSample(
                id = "vi-vn",
                name = "language-region",
                language = OcrLanguage.VIETNAMESE,
                url = "$BASE/locale/vi-vn/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Cài đặt chung"),
                roi = Region(165, 90, 260, 50)
            ),
            OnlineOcrSample(
                id = "tr-tr",
                name = "language-region",
                language = OcrLanguage.TURKISH,
                url = "$BASE/locale/tr-tr/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Genel"),
                roi = Region(220, 90, 160, 50)
            ),
            OnlineOcrSample(
                id = "id-id",
                name = "language-region",
                language = OcrLanguage.INDONESIAN,
                url = "$BASE/locale/id-id/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Umum"),
                roi = Region(220, 90, 160, 50)
            )
        )
    }
}
