package com.soluna.ktvisual.api

/**
 * Executes coordinate-based input actions in the host automation project.
 *
 * `kt-visual` deliberately does not bind to Appium, Selenium, ADB, or any
 * desktop automation framework. Implementations must translate screen
 * coordinates into the driver-specific click, double-click, and typing APIs.
 */
interface UiInput {
    /**
     * Clicks the screen coordinate in the same coordinate system as [ScreenSource].
     */
    fun click(x: Int, y: Int)

    /**
     * Performs a real double-click through the concrete driver.
     *
     * The default implementation fails intentionally. Calling [click] twice is
     * not equivalent to a native double-click in many automation frameworks.
     */
    fun doubleClick(x: Int, y: Int) {
        throw UnsupportedOperationException(
            "Double click must be implemented by the concrete automation driver."
        )
    }

    /**
     * Types text through the concrete driver.
     */
    fun type(text: String)
}
