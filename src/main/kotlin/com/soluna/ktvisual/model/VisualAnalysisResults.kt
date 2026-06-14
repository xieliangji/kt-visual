package com.soluna.ktvisual.model

/**
 * Pixel-change regions produced by visual regression analysis.
 *
 * [regions] contains merged bounding boxes in screenshot coordinates. The
 * aggregate counters use the whole compared image, not only the returned boxes.
 */
data class ChangedRegionsResult(
    val regions: List<Region>,
    val changedPixels: Int,
    val totalPixels: Int,
    val differenceRatio: Double
)

/**
 * Screenshot quality metrics for detecting unusable captures.
 *
 * [blurVariance] is the variance of the Laplacian edge response. Lower values
 * generally mean less detail and a higher chance that the screenshot is blurred.
 */
data class ScreenQualityResult(
    val averageBrightness: Double,
    val brightnessStdDev: Double,
    val darkPixelRatio: Double,
    val brightPixelRatio: Double,
    val blurVariance: Double,
    val isBlank: Boolean,
    val isMostlyDark: Boolean,
    val isMostlyBright: Boolean,
    val isBlurred: Boolean
)

/**
 * Coarse visual theme classification based on screenshot brightness.
 */
enum class VisualTheme {
    LIGHT,
    DARK,
    MIXED
}

/**
 * Theme detection result for a screenshot or ROI.
 */
data class ThemeDetectionResult(
    val theme: VisualTheme,
    val averageBrightness: Double,
    val darkPixelRatio: Double,
    val brightPixelRatio: Double
)

/**
 * A region that visually looks like text.
 *
 * This is not OCR. It only describes text-like visual blocks so callers can
 * assert layout density or decide where to run a real OCR engine.
 */
data class TextBlock(
    val bounds: Region,
    val density: Double
)

/**
 * Repeated visual regions such as list rows or grid cells.
 */
data class RepeatedRegionsResult(
    val regions: List<Region>,
    val orientation: RepeatedRegionOrientation
)

/**
 * Orientation used when grouping repeated visual regions.
 */
enum class RepeatedRegionOrientation {
    ROWS,
    COLUMNS
}

/**
 * Result returned by screenshot stability waiting.
 */
data class VisualStabilityResult(
    val stable: Boolean,
    val samples: Int,
    val lastDifferenceRatio: Double
)
