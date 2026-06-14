package com.soluna.ktvisual.cv

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

internal object TemplateTestImages {

    fun patterned(width: Int, height: Int): Mat {
        val template = Mat(height, width, CvType.CV_8UC3)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val blue = (30 + x * 7 + y * 3) % 255
                val green = (80 + x * 5 + y * 11) % 255
                val red = (140 + x * 13 + y * 2) % 255
                template.put(y, x, blue.toDouble(), green.toDouble(), red.toDouble())
            }
        }

        return template
    }

    fun resize(input: Mat, scale: Double): Mat {
        val resized = Mat()
        Imgproc.resize(
            input,
            resized,
            Size(input.cols() * scale, input.rows() * scale),
            0.0,
            0.0,
            Imgproc.INTER_AREA
        )
        return resized
    }
}
