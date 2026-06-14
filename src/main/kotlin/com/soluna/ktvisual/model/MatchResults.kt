package com.soluna.ktvisual.model

/**
 * Utility functions for ordering and filtering template match results.
 *
 * These helpers are intentionally deterministic. They are useful when `findAll`
 * returns repeated UI elements such as list rows, tab icons, cards, or repeated
 * action buttons and the caller needs a stable "first", "last", or "nth" item.
 */
object MatchResults {

    /**
     * Returns matches sorted by confidence from highest to lowest.
     */
    fun byScoreDescending(matches: List<MatchResult>): List<MatchResult> {
        return matches.sortedWith(compareByDescending<MatchResult> { it.score }
            .thenBy { it.bounds.y }
            .thenBy { it.bounds.x })
    }

    /**
     * Returns matches sorted visually from top to bottom, then left to right.
     */
    fun topToBottom(matches: List<MatchResult>): List<MatchResult> {
        return matches.sortedWith(compareBy<MatchResult> { it.bounds.y }
            .thenBy { it.bounds.x }
            .thenByDescending { it.score })
    }

    /**
     * Returns matches sorted visually from left to right, then top to bottom.
     */
    fun leftToRight(matches: List<MatchResult>): List<MatchResult> {
        return matches.sortedWith(compareBy<MatchResult> { it.bounds.x }
            .thenBy { it.bounds.y }
            .thenByDescending { it.score })
    }

    /**
     * Keeps matches whose center point is inside [region].
     */
    fun centersInside(matches: List<MatchResult>, region: Region): List<MatchResult> {
        return matches.filter { match ->
            match.center.x in region.x until region.x + region.width &&
                match.center.y in region.y until region.y + region.height
        }
    }
}
