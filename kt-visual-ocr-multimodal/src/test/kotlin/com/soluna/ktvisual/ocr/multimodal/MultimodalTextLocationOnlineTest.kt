package com.soluna.ktvisual.ocr.multimodal

import com.soluna.ktvisual.model.Region
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultimodalTextLocationOnlineTest {

    @Test
    fun `locate semantic text from element crop and full screenshot`() {
        if (System.getenv("KT_VISUAL_RUN_ONLINE_MULTIMODAL_TEXT_LOCATION") != "true") {
            return
        }

        val engine = MultimodalOcrEngine(
            client = OpenAiCompatibleMultimodalOcrClient.fromConfig(
                config = OpenAiCompatibleMultimodalOcrConfig(
                    baseUrl = requiredEnv("KT_VISUAL_MULTIMODAL_BASE_URL").let(URI::create),
                    apiKey = requiredEnv("KT_VISUAL_MULTIMODAL_API_KEY"),
                    model = requiredEnv("KT_VISUAL_MULTIMODAL_MODEL"),
                    timeout = Duration.ofSeconds(180),
                    reasoningEffort = System.getenv("KT_VISUAL_MULTIMODAL_REASONING_EFFORT"),
                    stream = false
                )
            ),
            options = MultimodalOcrOptions(
                useFallbackWhenEmpty = false,
                useFallbackOnClientError = false
            )
        )

        val elementPath = Path.of(requiredEnv("KT_VISUAL_MULTIMODAL_ELEMENT_IMAGE"))
        val fullPath = Path.of(requiredEnv("KT_VISUAL_MULTIMODAL_FULL_IMAGE"))
        val elementImage = ImageIO.read(elementPath.toFile())
        val expectedElementBounds = Region(
            x = intEnv("KT_VISUAL_MULTIMODAL_EXPECTED_X", 120),
            y = intEnv("KT_VISUAL_MULTIMODAL_EXPECTED_Y", 1147),
            width = intEnv("KT_VISUAL_MULTIMODAL_ELEMENT_WIDTH", elementImage.width),
            height = intEnv("KT_VISUAL_MULTIMODAL_ELEMENT_HEIGHT", elementImage.height)
        )
        val target = System.getenv("KT_VISUAL_MULTIMODAL_TEXT_TARGET")
            ?: "the consent text for user agreement, privacy policy, and personal information collection list"
        val expectedTextTerms = expectedTextTerms()

        val location = assertNotNull(
            engine.locateTextInImagePart(
                partImage = Files.readAllBytes(elementPath),
                fullImage = Files.readAllBytes(fullPath),
                target = target,
                locationOptions = MultimodalTextLocationOptions(minConfidence = 0.0)
            ),
            "Expected semantic text location from element crop and full screenshot."
        )
        println("image-part-location: $location")
        assertLocationLooksLikeConsentText(location, expectedElementBounds, expectedTextTerms)
    }

    private fun assertLocationLooksLikeConsentText(
        location: MultimodalTextLocation,
        expectedBounds: Region,
        expectedTextTerms: List<String>
    ) {
        assertTrue(
            location.center.x in expectedBounds.x until expectedBounds.x + expectedBounds.width &&
                location.center.y in expectedBounds.y until expectedBounds.y + expectedBounds.height,
            "Expected center=${location.center} to be inside expectedBounds=$expectedBounds for $location"
        )
        assertTrue(
            location.bounds.width >= expectedBounds.width / 2,
            "Expected text-rich block width to cover most consent text, actual=${location.bounds}"
        )
        assertTrue(
            location.bounds.height >= expectedBounds.height / 3,
            "Expected text-rich block height to cover wrapped consent text, actual=${location.bounds}"
        )
        assertTrue(
            expectedTextTerms.any { term -> location.text.contains(term, ignoreCase = true) },
            "Expected visible consent text containing one of $expectedTextTerms, actual=${location.text}"
        )
    }

    private fun expectedTextTerms(): List<String> {
        val configured = System.getenv("KT_VISUAL_MULTIMODAL_EXPECTED_TEXT_TERMS")
            ?.split('|', ',', ';')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
        return configured?.takeIf { it.isNotEmpty() }
            ?: listOf(
                "协议",
                "隐私",
                "个人信息",
                "Contrat",
                "Politique",
                "confidentialité",
                "informations personnelles"
            )
    }

    private fun requiredEnv(name: String): String {
        return System.getenv(name)
            ?: error("$name must be set when KT_VISUAL_RUN_ONLINE_MULTIMODAL_TEXT_LOCATION=true.")
    }

    private fun intEnv(name: String, defaultValue: Int): Int {
        return System.getenv(name)?.toIntOrNull() ?: defaultValue
    }
}
