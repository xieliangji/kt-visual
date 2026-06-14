package com.soluna.ktvisual.model

/**
 * Rectangle in screenshot coordinates.
 *
 * Coordinates use the common image convention: origin at the top-left, `x`
 * increasing to the right, and `y` increasing downward.
 */
data class Region(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0) { "width must be > 0" }
        require(height > 0) { "height must be > 0" }
    }

    val center: Point2
        get() = Point2(
            x = x + width / 2,
            y = y + height / 2
        )

    fun offset(dx: Int, dy: Int): Region {
        return copy(x = x + dx, y = y + dy)
    }

    fun expand(pixels: Int): Region {
        require(pixels >= 0) { "pixels must be >= 0" }
        return Region(
            x = x - pixels,
            y = y - pixels,
            width = width + pixels * 2,
            height = height + pixels * 2
        )
    }

    fun rightOf(width: Int, height: Int = this.height, gap: Int = 0, verticalOffset: Int = 0): Region {
        return Region(
            x = x + this.width + gap,
            y = y + verticalOffset,
            width = width,
            height = height
        )
    }

    fun leftOf(width: Int, height: Int = this.height, gap: Int = 0, verticalOffset: Int = 0): Region {
        return Region(
            x = x - gap - width,
            y = y + verticalOffset,
            width = width,
            height = height
        )
    }

    fun below(width: Int = this.width, height: Int, gap: Int = 0, horizontalOffset: Int = 0): Region {
        return Region(
            x = x + horizontalOffset,
            y = y + this.height + gap,
            width = width,
            height = height
        )
    }

    fun above(width: Int = this.width, height: Int, gap: Int = 0, horizontalOffset: Int = 0): Region {
        return Region(
            x = x + horizontalOffset,
            y = y - gap - height,
            width = width,
            height = height
        )
    }
}
