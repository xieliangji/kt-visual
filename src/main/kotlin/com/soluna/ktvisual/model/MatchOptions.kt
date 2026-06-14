package com.soluna.ktvisual.model

import org.opencv.imgproc.Imgproc
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Controls template matching behavior.
 *
 * Defaults are optimized for common UI template matching: grayscale matching,
 * one scale, and a moderately strict threshold. For DPI differences, provide
 * multiple [scales]. For color-insensitive icon shapes, consider
 * [edgeDetection] with `TM_CCORR_NORMED`.
 */
data class MatchOptions(
    val threshold: Double = 0.88,

    /**
     * 多尺度匹配。
     * 例如 UI 在不同机器上有缩放，可设为：
     * listOf(0.8, 0.9, 1.0, 1.1, 1.25)
     */
    val scales: List<Double> = listOf(1.0),

    /**
     * 默认使用归一化相关系数。
     */
    val method: Int = Imgproc.TM_CCOEFF_NORMED,

    /**
     * 是否转灰度。
     * UI 模板匹配一般建议开启，可减少颜色差异影响。
     */
    val grayscale: Boolean = true,

    /**
     * 是否使用 Canny 边缘图进行匹配。
     */
    val edgeDetection: Boolean = false,

    /**
     * 局部查找区域。
     * 大幅降低误识别和耗时。
     */
    val roi: Region? = null,

    /**
     * 是否保存调试图。
     */
    val debug: Boolean = false,

    /**
     * 调试图输出目录。
     */
    val debugDirectory: Path = Paths.get("build", "kt-visual-debug"),

    /**
     * findAll 最多返回的匹配数量。
     */
    val maxMatches: Int = 20,

    /**
     * findAll 去重时的最大允许重叠比例。
     */
    val overlapThreshold: Double = 0.3
) {
    init {
        require(threshold in 0.0..1.0) { "threshold must be between 0.0 and 1.0." }
        require(scales.isNotEmpty()) { "scales must not be empty." }
        require(scales.all { it > 0.0 }) { "scales must only contain positive values." }
        require(method in supportedMethods) { "Unsupported template matching method: $method" }
        require(maxMatches > 0) { "maxMatches must be > 0." }
        require(overlapThreshold in 0.0..1.0) { "overlapThreshold must be between 0.0 and 1.0." }
    }

    private companion object {
        private val supportedMethods = setOf(
            Imgproc.TM_SQDIFF,
            Imgproc.TM_SQDIFF_NORMED,
            Imgproc.TM_CCORR,
            Imgproc.TM_CCORR_NORMED,
            Imgproc.TM_CCOEFF,
            Imgproc.TM_CCOEFF_NORMED
        )
    }
}
