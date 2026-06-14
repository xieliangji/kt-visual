package com.soluna.ktvisual.cv

import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.Region
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TemplateDebugWriter {

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")

    fun write(
        screen: Mat,
        targetName: String,
        result: MatchResult?,
        roi: Region?,
        outputDirectory: Path
    ): Path {
        Files.createDirectories(outputDirectory)

        val annotated = toBgr(screen)
        try {
            if (roi != null) {
                drawRectangle(annotated, roi, Scalar(255.0, 0.0, 0.0), thickness = 2)
            }

            if (result != null) {
                drawRectangle(annotated, result.bounds, Scalar(0.0, 255.0, 0.0), thickness = 2)
                Imgproc.circle(
                    annotated,
                    Point(result.center.x.toDouble(), result.center.y.toDouble()),
                    4,
                    Scalar(0.0, 0.0, 255.0),
                    Imgproc.FILLED
                )
                putLabel(
                    annotated,
                    "${result.targetName} score=${format(result.score, 3)} scale=${format(result.scale, 2)} time=${result.elapsedMillis}ms",
                    x = result.bounds.x,
                    y = (result.bounds.y - 8).coerceAtLeast(16)
                )
            } else {
                putLabel(
                    annotated,
                    "$targetName not found",
                    x = roi?.x ?: 8,
                    y = ((roi?.y ?: 8) + 18).coerceAtLeast(18)
                )
            }

            val outputPath = outputDirectory.resolve(fileName(targetName, result))
            require(Imgcodecs.imwrite(outputPath.toString(), annotated)) {
                "Failed to write debug image: $outputPath"
            }
            return outputPath
        } finally {
            annotated.release()
        }
    }

    private fun toBgr(input: Mat): Mat {
        if (input.channels() == 3) {
            return input.clone()
        }

        val converted = Mat()
        when (input.channels()) {
            1 -> Imgproc.cvtColor(input, converted, Imgproc.COLOR_GRAY2BGR)
            4 -> Imgproc.cvtColor(input, converted, Imgproc.COLOR_BGRA2BGR)
            else -> input.copyTo(converted)
        }
        return converted
    }

    private fun drawRectangle(image: Mat, region: Region, color: Scalar, thickness: Int) {
        Imgproc.rectangle(
            image,
            Point(region.x.toDouble(), region.y.toDouble()),
            Point((region.x + region.width).toDouble(), (region.y + region.height).toDouble()),
            color,
            thickness
        )
    }

    private fun putLabel(image: Mat, text: String, x: Int, y: Int) {
        Imgproc.putText(
            image,
            text,
            Point(x.toDouble(), y.toDouble()),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.45,
            Scalar(0.0, 255.0, 255.0),
            1,
            Imgproc.LINE_AA
        )
    }

    private fun fileName(targetName: String, result: MatchResult?): String {
        val timestamp = timestampFormatter.format(LocalDateTime.now())
        val suffix = if (result == null) {
            "failed"
        } else {
            "matched_score_${format(result.score, 3)}"
        }
        return "${timestamp}_${System.nanoTime()}_${sanitize(targetName)}_$suffix.png"
    }

    private fun format(value: Double, digits: Int): String {
        return String.format(Locale.US, "%.${digits}f", value)
    }

    private fun sanitize(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "target" }
    }
}
