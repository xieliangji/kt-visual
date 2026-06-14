package com.soluna.ktvisual.cv

import com.soluna.ktvisual.model.CommonRegionResult
import com.soluna.ktvisual.model.ImageDiffResult
import com.soluna.ktvisual.model.Region
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path

/**
 * Image comparison utilities for visual assertions.
 *
 * `compare` is strict and requires same-sized images. `compareResized` handles
 * same visual content rendered at different sizes. `findCommonRegion` and
 * `compareCommonRegion` are useful when only part of two images overlaps.
 */
object ImageDiffer {

    /**
     * Compares two same-sized images already loaded as [Mat] objects.
     *
     * [pixelThreshold] is the minimum grayscale pixel difference counted as a
     * change. [maxDifferenceRatio] is the largest allowed changed-pixel ratio.
     */
    fun compare(
        expected: Mat,
        actual: Mat,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        require(!expected.empty()) { "expected Mat is empty." }
        require(!actual.empty()) { "actual Mat is empty." }
        require(expected.rows() == actual.rows() && expected.cols() == actual.cols()) {
            "Images must have the same size."
        }
        require(pixelThreshold in 0.0..255.0) { "pixelThreshold must be between 0.0 and 255.0." }
        require(maxDifferenceRatio in 0.0..1.0) {
            "maxDifferenceRatio must be between 0.0 and 1.0."
        }

        val expectedGray = toGray(expected)
        val actualGray = toGray(actual)
        val diff = Mat()
        val mask = Mat()

        try {
            Core.absdiff(expectedGray, actualGray, diff)
            Imgproc.threshold(diff, mask, pixelThreshold, 255.0, Imgproc.THRESH_BINARY)

            val changedPixels = Core.countNonZero(mask)
            val totalPixels = expected.rows() * expected.cols()
            val ratio = changedPixels.toDouble() / totalPixels.toDouble()

            return ImageDiffResult(
                changedPixels = changedPixels,
                totalPixels = totalPixels,
                differenceRatio = ratio,
                matches = ratio <= maxDifferenceRatio
            )
        } finally {
            expectedGray.release()
            actualGray.release()
            diff.release()
            mask.release()
        }
    }

    /**
     * Loads and compares two same-sized image files.
     *
     * Use this overload for baseline-image regression tests where the expected
     * and current screenshots are stored as PNG/JPEG files. Loaded images are
     * released before this method returns.
     */
    fun compare(
        expected: Path,
        actual: Path,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return withLoadedImages(expected, actual) { expectedMat, actualMat ->
            compare(expectedMat, actualMat, pixelThreshold, maxDifferenceRatio)
        }
    }

    /**
     * Loads and compares two same-sized image files.
     */
    fun compare(
        expected: File,
        actual: File,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return compare(expected.toPath(), actual.toPath(), pixelThreshold, maxDifferenceRatio)
    }

    /**
     * Converts and compares two same-sized in-memory images.
     */
    fun compare(
        expected: BufferedImage,
        actual: BufferedImage,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return withBufferedImages(expected, actual) { expectedMat, actualMat ->
            compare(expectedMat, actualMat, pixelThreshold, maxDifferenceRatio)
        }
    }

    /**
     * Decodes and compares two same-sized encoded images.
     *
     * [expected] and [actual] must contain complete image files in memory, such
     * as PNG or JPEG bytes. They must not be raw pixel buffers.
     */
    fun compare(
        expected: ByteArray,
        actual: ByteArray,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return withEncodedImages(expected, actual) { expectedMat, actualMat ->
            compare(expectedMat, actualMat, pixelThreshold, maxDifferenceRatio)
        }
    }

    /**
     * Compares two images after resizing [actual] to [expected]'s dimensions.
     *
     * This is useful when screenshots represent the same UI at different
     * resolutions or device pixel ratios.
     */
    fun compareResized(
        expected: Mat,
        actual: Mat,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        require(!expected.empty()) { "expected Mat is empty." }
        require(!actual.empty()) { "actual Mat is empty." }

        val resizedActual = Mat()
        try {
            Imgproc.resize(
                actual,
                resizedActual,
                Size(expected.cols().toDouble(), expected.rows().toDouble()),
                0.0,
                0.0,
                Imgproc.INTER_AREA
            )

            return compare(
                expected = expected,
                actual = resizedActual,
                pixelThreshold = pixelThreshold,
                maxDifferenceRatio = maxDifferenceRatio
            )
        } finally {
            resizedActual.release()
        }
    }

    /**
     * Loads two image files and compares them after resizing [actual] to
     * [expected]'s dimensions.
     */
    fun compareResized(
        expected: Path,
        actual: Path,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return withLoadedImages(expected, actual) { expectedMat, actualMat ->
            compareResized(expectedMat, actualMat, pixelThreshold, maxDifferenceRatio)
        }
    }

    /**
     * Loads two image files and compares them after resizing [actual].
     */
    fun compareResized(
        expected: File,
        actual: File,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return compareResized(expected.toPath(), actual.toPath(), pixelThreshold, maxDifferenceRatio)
    }

    /**
     * Converts two in-memory images and compares them after resizing [actual].
     */
    fun compareResized(
        expected: BufferedImage,
        actual: BufferedImage,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return withBufferedImages(expected, actual) { expectedMat, actualMat ->
            compareResized(expectedMat, actualMat, pixelThreshold, maxDifferenceRatio)
        }
    }

    /**
     * Decodes two encoded images and compares them after resizing [actual].
     */
    fun compareResized(
        expected: ByteArray,
        actual: ByteArray,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return withEncodedImages(expected, actual) { expectedMat, actualMat ->
            compareResized(expectedMat, actualMat, pixelThreshold, maxDifferenceRatio)
        }
    }

    /**
     * Finds the strongest shared visual region between two images.
     *
     * The smaller image is treated as the template. [scales] lets the shared
     * content match despite resolution or density differences.
     */
    fun findCommonRegion(
        source: Mat,
        target: Mat,
        threshold: Double = 0.88,
        scales: List<Double> = listOf(1.0),
        grayscale: Boolean = true
    ): CommonRegionResult? {
        require(!source.empty()) { "source Mat is empty." }
        require(!target.empty()) { "target Mat is empty." }
        require(threshold in 0.0..1.0) { "threshold must be between 0.0 and 1.0." }
        require(scales.isNotEmpty()) { "scales must not be empty." }
        require(scales.all { it > 0.0 }) { "scales must only contain positive values." }

        val sourceArea = source.cols() * source.rows()
        val targetArea = target.cols() * target.rows()

        return if (sourceArea <= targetArea) {
            findScaledTemplate(
                template = source,
                searchImage = target,
                threshold = threshold,
                scales = scales,
                grayscale = grayscale,
                sourceIsTemplate = true
            )
        } else {
            findScaledTemplate(
                template = target,
                searchImage = source,
                threshold = threshold,
                scales = scales,
                grayscale = grayscale,
                sourceIsTemplate = false
            )
        }
    }

    /**
     * Loads two image files and finds their strongest shared visual region.
     */
    fun findCommonRegion(
        source: Path,
        target: Path,
        threshold: Double = 0.88,
        scales: List<Double> = listOf(1.0),
        grayscale: Boolean = true
    ): CommonRegionResult? {
        return withLoadedImages(source, target) { sourceMat, targetMat ->
            findCommonRegion(sourceMat, targetMat, threshold, scales, grayscale)
        }
    }

    /**
     * Loads two image files and finds their strongest shared visual region.
     */
    fun findCommonRegion(
        source: File,
        target: File,
        threshold: Double = 0.88,
        scales: List<Double> = listOf(1.0),
        grayscale: Boolean = true
    ): CommonRegionResult? {
        return findCommonRegion(source.toPath(), target.toPath(), threshold, scales, grayscale)
    }

    /**
     * Converts two in-memory images and finds their strongest shared visual region.
     */
    fun findCommonRegion(
        source: BufferedImage,
        target: BufferedImage,
        threshold: Double = 0.88,
        scales: List<Double> = listOf(1.0),
        grayscale: Boolean = true
    ): CommonRegionResult? {
        return withBufferedImages(source, target) { sourceMat, targetMat ->
            findCommonRegion(sourceMat, targetMat, threshold, scales, grayscale)
        }
    }

    /**
     * Decodes two encoded images and finds their strongest shared visual region.
     */
    fun findCommonRegion(
        source: ByteArray,
        target: ByteArray,
        threshold: Double = 0.88,
        scales: List<Double> = listOf(1.0),
        grayscale: Boolean = true
    ): CommonRegionResult? {
        return withEncodedImages(source, target) { sourceMat, targetMat ->
            findCommonRegion(sourceMat, targetMat, threshold, scales, grayscale)
        }
    }

    /**
     * Compares an already discovered common region between two images.
     */
    fun compareCommonRegion(
        source: Mat,
        target: Mat,
        commonRegion: CommonRegionResult,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        require(!source.empty()) { "source Mat is empty." }
        require(!target.empty()) { "target Mat is empty." }

        val sourceRoi = Mat(
            source,
            Rect(
                commonRegion.sourceRegion.x,
                commonRegion.sourceRegion.y,
                commonRegion.sourceRegion.width,
                commonRegion.sourceRegion.height
            )
        )
        val targetRoi = Mat(
            target,
            Rect(
                commonRegion.targetRegion.x,
                commonRegion.targetRegion.y,
                commonRegion.targetRegion.width,
                commonRegion.targetRegion.height
            )
        )

        return try {
            compareResized(
                expected = sourceRoi,
                actual = targetRoi,
                pixelThreshold = pixelThreshold,
                maxDifferenceRatio = maxDifferenceRatio
            )
        } finally {
            sourceRoi.release()
            targetRoi.release()
        }
    }

    /**
     * Loads two image files and compares an already discovered common region.
     */
    fun compareCommonRegion(
        source: Path,
        target: Path,
        commonRegion: CommonRegionResult,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return withLoadedImages(source, target) { sourceMat, targetMat ->
            compareCommonRegion(sourceMat, targetMat, commonRegion, pixelThreshold, maxDifferenceRatio)
        }
    }

    /**
     * Loads two image files and compares an already discovered common region.
     */
    fun compareCommonRegion(
        source: File,
        target: File,
        commonRegion: CommonRegionResult,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return compareCommonRegion(
            source.toPath(),
            target.toPath(),
            commonRegion,
            pixelThreshold,
            maxDifferenceRatio
        )
    }

    /**
     * Converts two in-memory images and compares an already discovered common region.
     */
    fun compareCommonRegion(
        source: BufferedImage,
        target: BufferedImage,
        commonRegion: CommonRegionResult,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return withBufferedImages(source, target) { sourceMat, targetMat ->
            compareCommonRegion(sourceMat, targetMat, commonRegion, pixelThreshold, maxDifferenceRatio)
        }
    }

    /**
     * Decodes two encoded images and compares an already discovered common region.
     */
    fun compareCommonRegion(
        source: ByteArray,
        target: ByteArray,
        commonRegion: CommonRegionResult,
        pixelThreshold: Double = 0.0,
        maxDifferenceRatio: Double = 0.0
    ): ImageDiffResult {
        return withEncodedImages(source, target) { sourceMat, targetMat ->
            compareCommonRegion(sourceMat, targetMat, commonRegion, pixelThreshold, maxDifferenceRatio)
        }
    }

    private fun toGray(input: Mat): Mat {
        if (input.channels() == 1) {
            return input.clone()
        }

        val gray = Mat()
        Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY)
        return gray
    }

    private fun findScaledTemplate(
        template: Mat,
        searchImage: Mat,
        threshold: Double,
        scales: List<Double>,
        grayscale: Boolean,
        sourceIsTemplate: Boolean
    ): CommonRegionResult? {
        val processedSearch = ImagePreprocessor.preprocess(searchImage, grayscale)
        var best: CommonRegionResult? = null

        try {
            for (scale in scales) {
                val scaledTemplate = resize(template, scale)
                val processedTemplate = ImagePreprocessor.preprocess(scaledTemplate, grayscale)

                try {
                    if (
                        processedTemplate.cols() > processedSearch.cols() ||
                        processedTemplate.rows() > processedSearch.rows()
                    ) {
                        continue
                    }

                    val result = Mat()
                    try {
                        Imgproc.matchTemplate(
                            processedSearch,
                            processedTemplate,
                            result,
                            Imgproc.TM_CCOEFF_NORMED
                        )

                        val match = Core.minMaxLoc(result)
                        if (match.maxVal < threshold) continue

                        val templateRegion = Region(
                            x = 0,
                            y = 0,
                            width = template.cols(),
                            height = template.rows()
                        )
                        val scaledRegion = Region(
                            x = match.maxLoc.x.toInt(),
                            y = match.maxLoc.y.toInt(),
                            width = processedTemplate.cols(),
                            height = processedTemplate.rows()
                        )

                        val candidate = if (sourceIsTemplate) {
                            CommonRegionResult(
                                sourceRegion = templateRegion,
                                targetRegion = scaledRegion,
                                score = match.maxVal,
                                scale = scale
                            )
                        } else {
                            CommonRegionResult(
                                sourceRegion = scaledRegion,
                                targetRegion = templateRegion,
                                score = match.maxVal,
                                scale = scale
                            )
                        }

                        if (best == null || candidate.score > best.score) {
                            best = candidate
                        }
                    } finally {
                        result.release()
                    }
                } finally {
                    processedTemplate.release()
                    scaledTemplate.release()
                }
            }

            return best
        } finally {
            processedSearch.release()
        }
    }

    private fun resize(input: Mat, scale: Double): Mat {
        if (scale == 1.0) {
            return input.clone()
        }

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

    private inline fun <T> withLoadedImages(
        first: Path,
        second: Path,
        block: (Mat, Mat) -> T
    ): T {
        val firstMat = MatConverters.fromPath(first)
        val secondMat = MatConverters.fromPath(second)

        return try {
            block(firstMat, secondMat)
        } finally {
            firstMat.release()
            secondMat.release()
        }
    }

    private inline fun <T> withBufferedImages(
        first: BufferedImage,
        second: BufferedImage,
        block: (Mat, Mat) -> T
    ): T {
        val firstMat = MatConverters.fromBufferedImage(first)
        val secondMat = MatConverters.fromBufferedImage(second)

        return try {
            block(firstMat, secondMat)
        } finally {
            firstMat.release()
            secondMat.release()
        }
    }

    private inline fun <T> withEncodedImages(
        first: ByteArray,
        second: ByteArray,
        block: (Mat, Mat) -> T
    ): T {
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
