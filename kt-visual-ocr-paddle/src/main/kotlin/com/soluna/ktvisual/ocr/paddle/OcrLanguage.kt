package com.soluna.ktvisual.ocr.paddle

/**
 * Languages supported by the built-in multilingual Paddle OCR profile.
 *
 * The 13-language profile targets common app UI automation needs: Chinese,
 * English, CJK neighbors, Latin-script European languages, Russian, Thai,
 * Vietnamese, Turkish, and Indonesian.
 */
enum class OcrLanguage(
    val displayName: String,
    val isoCode: String
) {
    SIMPLIFIED_CHINESE("Simplified Chinese", "zh-Hans"),
    ENGLISH("English", "en"),
    KOREAN("Korean", "ko"),
    JAPANESE("Japanese", "ja"),
    GERMAN("German", "de"),
    FRENCH("French", "fr"),
    SPANISH("Spanish", "es"),
    PORTUGUESE("Portuguese", "pt"),
    RUSSIAN("Russian", "ru"),
    THAI("Thai", "th"),
    VIETNAMESE("Vietnamese", "vi"),
    TURKISH("Turkish", "tr"),
    INDONESIAN("Indonesian", "id");

    companion object {
        /**
         * The default language set requested for the bundled multilingual profile.
         */
        val MULTILINGUAL_13: Set<OcrLanguage> = entries.toSet()
    }
}
