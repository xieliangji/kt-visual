package com.soluna.ktvisual.model

/**
 * Broad semantic colors represented as HSV ranges.
 *
 * These ranges are intentionally approximate and intended for UI state checks
 * such as green success buttons, red errors, gray disabled controls, or blue
 * selected states. Use [RgbColor] for exact color checks.
 */
enum class NamedColor(
    val ranges: List<HsvColorRange>
) {
    RED(
        listOf(
            HsvColorRange(minHue = 0, maxHue = 10, minSaturation = 60, minValue = 50),
            HsvColorRange(minHue = 170, maxHue = 179, minSaturation = 60, minValue = 50)
        )
    ),
    ORANGE(
        listOf(HsvColorRange(minHue = 11, maxHue = 24, minSaturation = 60, minValue = 50))
    ),
    YELLOW(
        listOf(HsvColorRange(minHue = 25, maxHue = 35, minSaturation = 50, minValue = 70))
    ),
    GREEN(
        listOf(HsvColorRange(minHue = 36, maxHue = 89, minSaturation = 40, minValue = 40))
    ),
    CYAN(
        listOf(HsvColorRange(minHue = 90, maxHue = 99, minSaturation = 40, minValue = 40))
    ),
    BLUE(
        listOf(HsvColorRange(minHue = 100, maxHue = 129, minSaturation = 40, minValue = 40))
    ),
    PURPLE(
        listOf(HsvColorRange(minHue = 130, maxHue = 159, minSaturation = 35, minValue = 40))
    ),
    PINK(
        listOf(HsvColorRange(minHue = 160, maxHue = 169, minSaturation = 35, minValue = 60))
    ),
    WHITE(
        listOf(HsvColorRange(minHue = 0, maxHue = 179, minSaturation = 0, maxSaturation = 35, minValue = 200))
    ),
    BLACK(
        listOf(HsvColorRange(minHue = 0, maxHue = 179, minSaturation = 0, maxSaturation = 255, minValue = 0, maxValue = 45))
    ),
    GRAY(
        listOf(HsvColorRange(minHue = 0, maxHue = 179, minSaturation = 0, maxSaturation = 35, minValue = 46, maxValue = 199))
    )
}
