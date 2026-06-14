package com.soluna.ktvisual.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchResultsTest {

    @Test
    fun `sorts matches by visual order`() {
        val matches = listOf(
            result("c", x = 40, y = 20, score = 0.95),
            result("a", x = 10, y = 10, score = 0.90),
            result("b", x = 30, y = 10, score = 0.92)
        )

        assertEquals(listOf("a", "b", "c"), MatchResults.topToBottom(matches).map { it.targetName })
        assertEquals(listOf("a", "b", "c"), MatchResults.leftToRight(matches).map { it.targetName })
    }

    @Test
    fun `sorts matches by score descending`() {
        val matches = listOf(
            result("low", x = 10, y = 10, score = 0.80),
            result("high", x = 20, y = 20, score = 0.95),
            result("mid", x = 30, y = 30, score = 0.90)
        )

        assertEquals(listOf("high", "mid", "low"), MatchResults.byScoreDescending(matches).map { it.targetName })
    }

    @Test
    fun `filters matches by center inside region`() {
        val matches = listOf(
            result("inside", x = 10, y = 10, score = 0.90),
            result("outside", x = 100, y = 100, score = 0.90)
        )

        val filtered = MatchResults.centersInside(matches, Region(x = 0, y = 0, width = 50, height = 50))

        assertEquals(listOf("inside"), filtered.map { it.targetName })
    }

    private fun result(name: String, x: Int, y: Int, score: Double): MatchResult {
        return MatchResult(
            targetName = name,
            bounds = Region(x = x, y = y, width = 10, height = 10),
            score = score,
            scale = 1.0
        )
    }
}
