package com.soluna.ktvisual.ocr.paddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaddleOcrModelCatalogTest {

    @Test
    fun `multilingual profile covers requested thirteen languages`() {
        val routedLanguages = PaddleOcrModelCatalog.all.flatMap { it.supportedLanguages }.toSet()

        assertEquals(OcrLanguage.MULTILINGUAL_13, routedLanguages)
    }

    @Test
    fun `route returns minimum model groups for selected languages`() {
        val route = PaddleOcrModelCatalog.route(
            setOf(
                OcrLanguage.SIMPLIFIED_CHINESE,
                OcrLanguage.ENGLISH,
                OcrLanguage.FRENCH,
                OcrLanguage.RUSSIAN
            )
        )

        assertEquals(listOf("ppocrv6-cjk-en", "ppocrv5-latin", "ppocrv5-cyrillic"), route.map { it.id })
        assertTrue(route.all { it.supportedLanguages.isNotEmpty() })
    }
}
