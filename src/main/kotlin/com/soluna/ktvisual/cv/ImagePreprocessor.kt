package com.soluna.ktvisual.cv

import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object ImagePreprocessor {

    fun preprocess(input: Mat, grayscale: Boolean, edgeDetection: Boolean = false): Mat {
        if (!grayscale && !edgeDetection) {
            return input.clone()
        }

        val gray = Mat()
        Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY)

        if (!edgeDetection) {
            return gray
        }

        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        gray.release()
        return edges
    }
}
