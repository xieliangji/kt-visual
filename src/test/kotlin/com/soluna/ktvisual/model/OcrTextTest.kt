package com.soluna.ktvisual.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class OcrTextTest {

    @Test
    fun `ocr confidence must be valid when present`() {
        OcrText(
            text = "hello",
            bounds = Region(0, 0, 10, 10),
            confidence = 0.95
        )

        assertFailsWith<IllegalArgumentException> {
            OcrText(
                text = "bad",
                bounds = Region(0, 0, 10, 10),
                confidence = 1.1
            )
        }
    }
}
