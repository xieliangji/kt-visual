package com.soluna.ktvisual.report

import com.soluna.ktvisual.model.MatchResult
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import com.soluna.ktvisual.cv.MatConverters

/**
 * Writes visual evidence files for automation reports.
 *
 * This class does not own any input [Mat]. Callers remain responsible for
 * releasing their own OpenCV objects. Generated files are ordinary PNG images.
 */
class VisualReportWriter(
    private val outputDirectory: Path = Path.of("build", "kt-visual-report")
) {

    /**
     * Saves [screen] as a PNG artifact without drawing annotations.
     */
    fun saveScreenshot(name: String, screen: Mat): VisualArtifact {
        require(!screen.empty()) { "screen Mat is empty." }
        val path = outputPath(name, "screenshot")
        val image = toBgr(screen)
        try {
            write(path, image)
            return VisualArtifact(name, path, "Raw screenshot")
        } finally {
            image.release()
        }
    }

    /**
     * Loads [screen] from a file and saves it as a PNG artifact.
     */
    fun saveScreenshot(name: String, screen: Path): VisualArtifact {
        return withImage(screen) { saveScreenshot(name, it) }
    }

    /**
     * Loads [screen] from a file and saves it as a PNG artifact.
     */
    fun saveScreenshot(name: String, screen: File): VisualArtifact {
        return saveScreenshot(name, screen.toPath())
    }

    /**
     * Converts [screen] and saves it as a PNG artifact.
     */
    fun saveScreenshot(name: String, screen: BufferedImage): VisualArtifact {
        return withImage(screen) { saveScreenshot(name, it) }
    }

    /**
     * Decodes encoded screenshot bytes and saves them as a PNG artifact.
     */
    fun saveScreenshot(name: String, screen: ByteArray): VisualArtifact {
        return withImage(screen) { saveScreenshot(name, it) }
    }

    /**
     * Saves [screen] with match rectangles and center points drawn on top.
     */
    fun saveMatches(name: String, screen: Mat, matches: List<MatchResult>): VisualArtifact {
        require(!screen.empty()) { "screen Mat is empty." }
        val annotated = toBgr(screen)

        try {
            matches.forEach { match ->
                Imgproc.rectangle(
                    annotated,
                    Point(match.bounds.x.toDouble(), match.bounds.y.toDouble()),
                    Point(
                        (match.bounds.x + match.bounds.width).toDouble(),
                        (match.bounds.y + match.bounds.height).toDouble()
                    ),
                    Scalar(0.0, 255.0, 0.0),
                    2
                )
                Imgproc.circle(
                    annotated,
                    Point(match.center.x.toDouble(), match.center.y.toDouble()),
                    4,
                    Scalar(0.0, 0.0, 255.0),
                    Imgproc.FILLED
                )
                Imgproc.putText(
                    annotated,
                    "${match.targetName} ${String.format(Locale.US, "%.3f", match.score)}",
                    Point(match.bounds.x.toDouble(), (match.bounds.y - 8).coerceAtLeast(16).toDouble()),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.45,
                    Scalar(0.0, 255.0, 255.0),
                    1,
                    Imgproc.LINE_AA
                )
            }

            val path = outputPath(name, "matches")
            write(path, annotated)
            return VisualArtifact(name, path, "Screenshot with ${matches.size} match annotation(s)")
        } finally {
            annotated.release()
        }
    }

    /**
     * Loads [screen] from a file and saves match annotations on top.
     */
    fun saveMatches(name: String, screen: Path, matches: List<MatchResult>): VisualArtifact {
        return withImage(screen) { saveMatches(name, it, matches) }
    }

    /**
     * Loads [screen] from a file and saves match annotations on top.
     */
    fun saveMatches(name: String, screen: File, matches: List<MatchResult>): VisualArtifact {
        return saveMatches(name, screen.toPath(), matches)
    }

    /**
     * Converts [screen] and saves match annotations on top.
     */
    fun saveMatches(name: String, screen: BufferedImage, matches: List<MatchResult>): VisualArtifact {
        return withImage(screen) { saveMatches(name, it, matches) }
    }

    /**
     * Decodes encoded screenshot bytes and saves match annotations on top.
     */
    fun saveMatches(name: String, screen: ByteArray, matches: List<MatchResult>): VisualArtifact {
        return withImage(screen) { saveMatches(name, it, matches) }
    }

    /**
     * Saves a heatmap of pixel differences between same-sized images.
     *
     * Dark areas represent little or no difference. Brighter colored areas
     * represent stronger visual differences.
     */
    fun saveDiffHeatmap(name: String, expected: Mat, actual: Mat): VisualArtifact {
        require(!expected.empty()) { "expected Mat is empty." }
        require(!actual.empty()) { "actual Mat is empty." }
        require(expected.rows() == actual.rows() && expected.cols() == actual.cols()) {
            "Images must have the same size."
        }

        val expectedGray = toGray(expected)
        val actualGray = toGray(actual)
        val diff = Mat()
        val heatmap = Mat()

        try {
            Core.absdiff(expectedGray, actualGray, diff)
            Imgproc.applyColorMap(diff, heatmap, Imgproc.COLORMAP_JET)

            val path = outputPath(name, "diff-heatmap")
            write(path, heatmap)
            return VisualArtifact(name, path, "Pixel difference heatmap")
        } finally {
            expectedGray.release()
            actualGray.release()
            diff.release()
            heatmap.release()
        }
    }

    /**
     * Loads two image files and saves a heatmap of their pixel differences.
     */
    fun saveDiffHeatmap(name: String, expected: Path, actual: Path): VisualArtifact {
        return withImages(expected, actual) { expectedMat, actualMat ->
            saveDiffHeatmap(name, expectedMat, actualMat)
        }
    }

    /**
     * Loads two image files and saves a heatmap of their pixel differences.
     */
    fun saveDiffHeatmap(name: String, expected: File, actual: File): VisualArtifact {
        return saveDiffHeatmap(name, expected.toPath(), actual.toPath())
    }

    /**
     * Converts two in-memory images and saves a heatmap of their pixel differences.
     */
    fun saveDiffHeatmap(name: String, expected: BufferedImage, actual: BufferedImage): VisualArtifact {
        return withImages(expected, actual) { expectedMat, actualMat ->
            saveDiffHeatmap(name, expectedMat, actualMat)
        }
    }

    /**
     * Decodes two encoded images and saves a heatmap of their pixel differences.
     */
    fun saveDiffHeatmap(name: String, expected: ByteArray, actual: ByteArray): VisualArtifact {
        return withImages(expected, actual) { expectedMat, actualMat ->
            saveDiffHeatmap(name, expectedMat, actualMat)
        }
    }

    private fun outputPath(name: String, suffix: String): Path {
        Files.createDirectories(outputDirectory)
        return outputDirectory.resolve("${sanitize(name)}-$suffix.png")
    }

    private fun write(path: Path, image: Mat) {
        require(Imgcodecs.imwrite(path.toString(), image)) {
            "Failed to write visual artifact: $path"
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

    private fun toGray(input: Mat): Mat {
        if (input.channels() == 1) {
            return input.clone()
        }

        val gray = Mat()
        Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY)
        return gray
    }

    private fun sanitize(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "artifact" }
    }

    private inline fun <T> withImage(path: Path, block: (Mat) -> T): T {
        val image = MatConverters.fromPath(path)
        return try {
            block(image)
        } finally {
            image.release()
        }
    }

    private inline fun <T> withImage(image: BufferedImage, block: (Mat) -> T): T {
        val mat = MatConverters.fromBufferedImage(image)
        return try {
            block(mat)
        } finally {
            mat.release()
        }
    }

    private inline fun <T> withImage(bytes: ByteArray, block: (Mat) -> T): T {
        val image = MatConverters.fromBytes(bytes)
        return try {
            block(image)
        } finally {
            image.release()
        }
    }

    private inline fun <T> withImages(first: Path, second: Path, block: (Mat, Mat) -> T): T {
        val firstMat = MatConverters.fromPath(first)
        val secondMat = MatConverters.fromPath(second)
        return try {
            block(firstMat, secondMat)
        } finally {
            firstMat.release()
            secondMat.release()
        }
    }

    private inline fun <T> withImages(first: BufferedImage, second: BufferedImage, block: (Mat, Mat) -> T): T {
        val firstMat = MatConverters.fromBufferedImage(first)
        val secondMat = MatConverters.fromBufferedImage(second)
        return try {
            block(firstMat, secondMat)
        } finally {
            firstMat.release()
            secondMat.release()
        }
    }

    private inline fun <T> withImages(first: ByteArray, second: ByteArray, block: (Mat, Mat) -> T): T {
        val firstMat = MatConverters.fromBytes(first)
        val secondMat = MatConverters.fromBytes(second)
        return try {
            block(firstMat, secondMat)
        } finally {
            firstMat.release()
            secondMat.release()
        }
    }
}
