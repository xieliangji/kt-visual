package com.soluna.ktvisual.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RegionTest {

    @Test
    fun `relative region helpers create expected regions`() {
        val base = Region(x = 10, y = 20, width = 30, height = 40)

        assertEquals(Region(40, 20, 12, 40), base.rightOf(width = 12))
        assertEquals(Region(-7, 23, 12, 8), base.leftOf(width = 12, height = 8, gap = 5, verticalOffset = 3))
        assertEquals(Region(10, 64, 30, 9), base.below(height = 9, gap = 4))
        assertEquals(Region(12, 7, 30, 9), base.above(height = 9, gap = 4, horizontalOffset = 2))
        assertEquals(Region(8, 18, 34, 44), base.expand(2))
    }
}
