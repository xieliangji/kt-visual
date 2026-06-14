package com.soluna.ktvisual.cv

import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.UiTarget
import org.opencv.core.Mat

interface VisualLocator {
    fun find(screen: Mat, target: UiTarget): MatchResult?

    fun findAll(screen: Mat, target: UiTarget): List<MatchResult>
}
