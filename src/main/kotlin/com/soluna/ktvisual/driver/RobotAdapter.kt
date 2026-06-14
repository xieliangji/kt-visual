package com.soluna.ktvisual.driver


import com.soluna.ktvisual.api.ScreenSource
import com.soluna.ktvisual.api.UiInput
import java.awt.AWTException
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.image.BufferedImage

class RobotScreenSource(
    private val area: Rectangle = defaultScreenBounds(),
    private val robot: Robot = createRobot()
) : ScreenSource {

    override fun capture(): BufferedImage {
        return robot.createScreenCapture(area)
    }

    companion object {
        private fun createRobot(): Robot {
            if (GraphicsEnvironment.isHeadless()) {
                throw IllegalStateException("Current JVM is headless; Robot screenshot is unavailable.")
            }

            return try {
                Robot()
            } catch (e: AWTException) {
                throw IllegalStateException("Failed to create java.awt.Robot.", e)
            }
        }

        private fun defaultScreenBounds(): Rectangle {
            val size = Toolkit.getDefaultToolkit().screenSize
            return Rectangle(0, 0, size.width, size.height)
        }
    }
}

class RobotUiInput(
    private val robot: Robot = Robot()
) : UiInput {

    override fun click(x: Int, y: Int) {
        robot.mouseMove(x, y)
        pressLeftButton()
    }

    override fun doubleClick(x: Int, y: Int) {
        robot.mouseMove(x, y)
        pressLeftButton()
        pressLeftButton()
    }

    override fun type(text: String) {
        throw UnsupportedOperationException(
            "Robot text typing should be implemented with key mapping or clipboard paste in your automation layer."
        )
    }

    private fun pressLeftButton() {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    }
}
