package com.soluna.ktvisual.ocr.multimodal

import com.soluna.ktvisual.model.Region
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class MultimodalOcrOnlineMultilingualTest {

    @Test
    fun `recognize apple support settings screenshots in thirteen languages`() {
        if (System.getenv("KT_VISUAL_RUN_ONLINE_MULTIMODAL_OCR") != "true") {
            return
        }

        val events = mutableListOf<OpenAiCompatibleStreamEvent>()
        val stream = System.getenv("KT_VISUAL_MULTIMODAL_STREAM")?.toBooleanStrictOrNull() ?: false
        val engine = MultimodalOcrEngine(
            client = OpenAiCompatibleMultimodalOcrClient.fromConfig(
                config = OpenAiCompatibleMultimodalOcrConfig(
                    baseUrl = requiredEnv("KT_VISUAL_MULTIMODAL_BASE_URL").let(URI::create),
                    apiKey = requiredEnv("KT_VISUAL_MULTIMODAL_API_KEY"),
                    model = requiredEnv("KT_VISUAL_MULTIMODAL_MODEL"),
                    timeout = Duration.ofSeconds(180),
                    reasoningEffort = System.getenv("KT_VISUAL_MULTIMODAL_REASONING_EFFORT"),
                    stream = stream
                ),
                onStreamEvent = events::add
            ),
            options = MultimodalOcrOptions(
                prompt = ONLINE_OCR_PROMPT,
                minConfidence = 0.0,
                useFallbackWhenEmpty = false,
                useFallbackOnClientError = false
            )
        )

        val sampleFilter = System.getenv("KT_VISUAL_MULTIMODAL_SAMPLE")
        val activeSamples = samples.filter { sampleFilter == null || it.id == sampleFilter }
        require(activeSamples.isNotEmpty()) {
            "No online multimodal OCR sample matched KT_VISUAL_MULTIMODAL_SAMPLE=$sampleFilter."
        }

        activeSamples.forEach { sample ->
            events.clear()
            val image = ImageIO.read(download(sample))
            val results = engine.recognize(
                image = image,
                roi = sample.roi
            )
            val recognized = results.joinToString(" ") { text -> text.text }
            val reasoningChunks = events.filterIsInstance<OpenAiCompatibleStreamEvent.Reasoning>().size
            val contentChunks = events.filterIsInstance<OpenAiCompatibleStreamEvent.Content>().size
            println("${sample.id}: $recognized")
            println("${sample.id}: stream reasoningChunks=$reasoningChunks contentChunks=$contentChunks")

            assertTrue(
                matchesAnyExpected(recognized, sample.expected),
                "Expected ${sample.id} OCR to contain one of ${sample.expected}, actual: $recognized"
            )
            assertTrue(
                results.any { it.bounds.width > 0 && it.bounds.height > 0 },
                "Expected ${sample.id} OCR to return positive text bounds, actual: $results"
            )
            assertTrue(
                !stream || reasoningChunks > 0 || contentChunks > 0,
                "Expected ${sample.id} streaming response to emit reasoning or content chunks."
            )
        }
    }

    private fun download(sample: OnlineOcrSample): java.io.File {
        val target = Path.of("build", "online-multimodal-ocr-samples", "${sample.id}-${sample.name}.png")
            .toAbsolutePath()
            .normalize()
        if (Files.exists(target) && Files.size(target) > 0) return target.toFile()

        Files.createDirectories(target.parent)
        URI.create(sample.url).toURL().openStream().use { input ->
            Files.newOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target.toFile()
    }

    private fun requiredEnv(name: String): String {
        return System.getenv(name)
            ?: error("$name must be set when KT_VISUAL_RUN_ONLINE_MULTIMODAL_OCR=true.")
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
        val url: String,
        val expected: List<String>,
        val roi: Region
    )

    private companion object {
        private const val BASE = "https://cdsassets.apple.com/live/7WUAS350/images/ios"
        private const val ONLINE_OCR_PROMPT =
            "Extract all visible UI text from this cropped screenshot. " +
                "Return only JSON with this shape: " +
                "{\"texts\":[{\"text\":\"General\",\"confidence\":0.98," +
                "\"bounds\":{\"x\":0.0,\"y\":0.0,\"width\":1.0,\"height\":1.0}}]}. " +
                "Bounds must be normalized to this cropped image. Do not translate text."

        private val samples = listOf(
            OnlineOcrSample(
                id = "zh-cn",
                name = "language-region",
                url = "$BASE/locale/zh-cn/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("通用"),
                roi = Region(245, 96, 100, 34)
            ),
            OnlineOcrSample(
                id = "en",
                name = "language-region",
                url = "$BASE/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("General"),
                roi = Region(230, 96, 130, 34)
            ),
            OnlineOcrSample(
                id = "ko-kr",
                name = "language-region",
                url = "$BASE/locale/ko-kr/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("일반"),
                roi = Region(245, 96, 100, 34)
            ),
            OnlineOcrSample(
                id = "ja-jp",
                name = "language-region",
                url = "$BASE/locale/ja-jp/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("一般"),
                roi = Region(245, 96, 100, 34)
            ),
            OnlineOcrSample(
                id = "de-de",
                name = "language-region",
                url = "$BASE/locale/de-de/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Allgemein"),
                roi = Region(220, 96, 150, 34)
            ),
            OnlineOcrSample(
                id = "fr-fr",
                name = "language-region",
                url = "$BASE/locale/fr-fr/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Général", "Génral", "General"),
                roi = Region(220, 96, 150, 34)
            ),
            OnlineOcrSample(
                id = "es-es",
                name = "language-region",
                url = "$BASE/locale/es-es/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("General"),
                roi = Region(220, 96, 150, 34)
            ),
            OnlineOcrSample(
                id = "pt-br",
                name = "language-region",
                url = "$BASE/locale/pt-br/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Geral"),
                roi = Region(235, 96, 120, 34)
            ),
            OnlineOcrSample(
                id = "ru-ru",
                name = "language-region",
                url = "$BASE/locale/ru-ru/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Основные"),
                roi = Region(200, 96, 190, 34)
            ),
            OnlineOcrSample(
                id = "th-th",
                name = "language-region",
                url = "$BASE/locale/th-th/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("ทั่วไป"),
                roi = Region(220, 96, 150, 34)
            ),
            OnlineOcrSample(
                id = "vi-vn",
                name = "language-region",
                url = "$BASE/locale/vi-vn/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Cài đặt chung"),
                roi = Region(175, 96, 240, 34)
            ),
            OnlineOcrSample(
                id = "tr-tr",
                name = "language-region",
                url = "$BASE/locale/tr-tr/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Genel"),
                roi = Region(235, 96, 120, 34)
            ),
            OnlineOcrSample(
                id = "id-id",
                name = "language-region",
                url = "$BASE/locale/id-id/ios-17-iphone-15-pro-settings-general-language-region.png",
                expected = listOf("Umum"),
                roi = Region(235, 96, 120, 34)
            )
        )
    }
}
