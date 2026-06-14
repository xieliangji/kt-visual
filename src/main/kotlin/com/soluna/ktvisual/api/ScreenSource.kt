package com.soluna.ktvisual.api

import java.awt.image.BufferedImage

/**
 * Provides the current screen image for visual recognition.
 *
 * Implement this interface in the host automation project by delegating to
 * Appium, Selenium, ADB, a desktop driver, a remote device API, or any other
 * screenshot source. The returned image should represent the same coordinate
 * space used by the corresponding [UiInput] implementation.
 */
interface ScreenSource {
    fun capture(): BufferedImage
}
