package com.soluna.ktvisual.cv

import com.soluna.ktvisual.OpenCvRuntime
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.nio.file.Path

/**
 * Converts common JVM image inputs into OpenCV [Mat] objects.
 *
 * Every method returns a new caller-owned [Mat]. Callers that keep the returned
 * object should release it when they are done. Higher-level APIs such as
 * `ImageDiffer.compare(Path, Path)` release internally loaded images for you.
 */
object MatConverters {

    /**
     * Loads an image file into a BGR [Mat].
     *
     * The default [flags] value produces a 3-channel color image suitable for
     * template matching, color detection, and visual diffing.
     */
    fun fromPath(path: Path, flags: Int = Imgcodecs.IMREAD_COLOR): Mat {
        OpenCvRuntime.ensureLoaded()

        val image = Imgcodecs.imread(path.toAbsolutePath().toString(), flags)
        require(!image.empty()) { "Failed to read image: ${path.toAbsolutePath()}" }
        return image
    }

    /**
     * Loads an image file into a BGR [Mat].
     */
    fun fromFile(file: File, flags: Int = Imgcodecs.IMREAD_COLOR): Mat {
        return fromPath(file.toPath(), flags)
    }

    /**
     * Decodes encoded image bytes into a BGR [Mat].
     *
     * [bytes] must contain a complete encoded image such as PNG, JPEG, or WebP
     * data. Raw pixel buffers are not supported by this method because they do
     * not carry width, height, channel, or color-order metadata.
     */
    fun fromBytes(bytes: ByteArray, flags: Int = Imgcodecs.IMREAD_COLOR): Mat {
        OpenCvRuntime.ensureLoaded()
        require(bytes.isNotEmpty()) { "image bytes must not be empty." }

        val encoded = Mat(1, bytes.size, CvType.CV_8UC1)
        return try {
            encoded.put(0, 0, bytes)
            val image = Imgcodecs.imdecode(encoded, flags)
            require(!image.empty()) { "Failed to decode image bytes." }
            image
        } finally {
            encoded.release()
        }
    }

    /**
     * Converts a JVM [BufferedImage] into a 3-channel BGR [Mat].
     */
    fun fromBufferedImage(image: BufferedImage): Mat {
        OpenCvRuntime.ensureLoaded()

        val converted = BufferedImage(
            image.width,
            image.height,
            BufferedImage.TYPE_3BYTE_BGR
        )

        val g: Graphics2D = converted.createGraphics()
        try {
            g.drawImage(image, 0, 0, null)
        } finally {
            g.dispose()
        }

        val pixels = (converted.raster.dataBuffer as DataBufferByte).data

        return Mat(
            converted.height,
            converted.width,
            CvType.CV_8UC3
        ).apply {
            put(0, 0, pixels)
        }
    }
}
