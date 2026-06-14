package com.soluna.ktvisual.cv

import com.soluna.ktvisual.OpenCvRuntime
import com.soluna.ktvisual.model.MatchOptions
import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.UiTarget
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path

/**
 * Finds UI targets in screenshots using OpenCV template matching.
 *
 * Use this class directly when the host project already has screenshots as
 * OpenCV [Mat] values. Use `UiVision` when working with [ScreenSource] and
 * [UiInput] abstractions.
 */
class TemplateLocator(
    private val templateCache: TemplateCache = TemplateCache()
) : VisualLocator, AutoCloseable {

    /**
     * Finds [target] in a screenshot that is already loaded as a [Mat].
     */
    override fun find(screen: Mat, target: UiTarget): MatchResult? {
        OpenCvRuntime.ensureLoaded()

        require(!screen.empty()) { "screen Mat is empty." }

        val started = System.nanoTime()
        val matches = locateMatches(screen, target)
        val elapsedMillis = elapsedMillisSince(started)
        val best = matches.firstOrNull()?.withElapsed(elapsedMillis)

        if (target.options.debug) {
            TemplateDebugWriter.write(
                screen = screen,
                targetName = target.name,
                result = best,
                roi = target.options.roi,
                outputDirectory = target.options.debugDirectory
            )
        }

        return best
    }

    /**
     * Loads a screenshot file and finds [target].
     */
    fun find(screen: Path, target: UiTarget): MatchResult? {
        return withImage(screen) { find(it, target) }
    }

    /**
     * Loads a screenshot file and finds [target].
     */
    fun find(screen: File, target: UiTarget): MatchResult? {
        return find(screen.toPath(), target)
    }

    /**
     * Converts an in-memory screenshot and finds [target].
     */
    fun find(screen: BufferedImage, target: UiTarget): MatchResult? {
        return withImage(screen) { find(it, target) }
    }

    /**
     * Decodes encoded screenshot bytes and finds [target].
     *
     * [screen] must be a complete encoded image such as PNG or JPEG bytes.
     */
    fun find(screen: ByteArray, target: UiTarget): MatchResult? {
        return withImage(screen) { find(it, target) }
    }

    /**
     * Finds all non-overlapping occurrences of [target] in a [Mat] screenshot.
     */
    override fun findAll(screen: Mat, target: UiTarget): List<MatchResult> {
        OpenCvRuntime.ensureLoaded()

        require(!screen.empty()) { "screen Mat is empty." }

        val started = System.nanoTime()
        val matches = locateMatches(screen, target)
        val elapsedMillis = elapsedMillisSince(started)
        val timedMatches = matches.map { it.withElapsed(elapsedMillis) }

        if (target.options.debug) {
            TemplateDebugWriter.write(
                screen = screen,
                targetName = target.name,
                result = timedMatches.firstOrNull(),
                roi = target.options.roi,
                outputDirectory = target.options.debugDirectory
            )
        }

        return timedMatches
    }

    /**
     * Loads a screenshot file and finds all non-overlapping occurrences of [target].
     */
    fun findAll(screen: Path, target: UiTarget): List<MatchResult> {
        return withImage(screen) { findAll(it, target) }
    }

    /**
     * Loads a screenshot file and finds all non-overlapping occurrences of [target].
     */
    fun findAll(screen: File, target: UiTarget): List<MatchResult> {
        return findAll(screen.toPath(), target)
    }

    /**
     * Converts an in-memory screenshot and finds all non-overlapping occurrences of [target].
     */
    fun findAll(screen: BufferedImage, target: UiTarget): List<MatchResult> {
        return withImage(screen) { findAll(it, target) }
    }

    /**
     * Decodes encoded screenshot bytes and finds all non-overlapping occurrences of [target].
     */
    fun findAll(screen: ByteArray, target: UiTarget): List<MatchResult> {
        return withImage(screen) { findAll(it, target) }
    }

    /**
     * Finds a template [Mat] inside a screenshot [Mat].
     */
    fun find(
        screen: Mat,
        template: Mat,
        targetName: String,
        options: MatchOptions = MatchOptions()
    ): MatchResult? {
        val started = System.nanoTime()
        val matches = locateMatches(screen, template, targetName, options)
        val elapsedMillis = elapsedMillisSince(started)
        val best = matches.firstOrNull()?.withElapsed(elapsedMillis)

        if (options.debug) {
            TemplateDebugWriter.write(
                screen = screen,
                targetName = targetName,
                result = best,
                roi = options.roi,
                outputDirectory = options.debugDirectory
            )
        }

        return best
    }

    /**
     * Loads a screenshot and template from files, then finds the best match.
     */
    fun find(
        screen: Path,
        template: Path,
        targetName: String,
        options: MatchOptions = MatchOptions()
    ): MatchResult? {
        return withImages(screen, template) { screenMat, templateMat ->
            find(screenMat, templateMat, targetName, options)
        }
    }

    /**
     * Decodes encoded screenshot and template bytes, then finds the best match.
     */
    fun find(
        screen: ByteArray,
        template: ByteArray,
        targetName: String,
        options: MatchOptions = MatchOptions()
    ): MatchResult? {
        return withImages(screen, template) { screenMat, templateMat ->
            find(screenMat, templateMat, targetName, options)
        }
    }

    /**
     * Converts in-memory screenshot and template images, then finds the best match.
     */
    fun find(
        screen: BufferedImage,
        template: BufferedImage,
        targetName: String,
        options: MatchOptions = MatchOptions()
    ): MatchResult? {
        return withImages(screen, template) { screenMat, templateMat ->
            find(screenMat, templateMat, targetName, options)
        }
    }

    /**
     * Finds every non-overlapping template occurrence in a screenshot [Mat].
     */
    fun findAll(
        screen: Mat,
        template: Mat,
        targetName: String,
        options: MatchOptions = MatchOptions()
    ): List<MatchResult> {
        val started = System.nanoTime()
        val matches = locateMatches(screen, template, targetName, options)
        val elapsedMillis = elapsedMillisSince(started)
        val timedMatches = matches.map { it.withElapsed(elapsedMillis) }

        if (options.debug) {
            TemplateDebugWriter.write(
                screen = screen,
                targetName = targetName,
                result = timedMatches.firstOrNull(),
                roi = options.roi,
                outputDirectory = options.debugDirectory
            )
        }

        return timedMatches
    }

    /**
     * Loads a screenshot and template from files, then finds every non-overlapping occurrence.
     */
    fun findAll(
        screen: Path,
        template: Path,
        targetName: String,
        options: MatchOptions = MatchOptions()
    ): List<MatchResult> {
        return withImages(screen, template) { screenMat, templateMat ->
            findAll(screenMat, templateMat, targetName, options)
        }
    }

    /**
     * Decodes encoded screenshot and template bytes, then finds every non-overlapping occurrence.
     */
    fun findAll(
        screen: ByteArray,
        template: ByteArray,
        targetName: String,
        options: MatchOptions = MatchOptions()
    ): List<MatchResult> {
        return withImages(screen, template) { screenMat, templateMat ->
            findAll(screenMat, templateMat, targetName, options)
        }
    }

    /**
     * Converts in-memory screenshot and template images, then finds every non-overlapping occurrence.
     */
    fun findAll(
        screen: BufferedImage,
        template: BufferedImage,
        targetName: String,
        options: MatchOptions = MatchOptions()
    ): List<MatchResult> {
        return withImages(screen, template) { screenMat, templateMat ->
            findAll(screenMat, templateMat, targetName, options)
        }
    }

    private fun locateMatches(screen: Mat, target: UiTarget): List<MatchResult> {
        val candidates = target.imagePaths.flatMap { path ->
            locateMatches(
                screen = screen,
                template = templateCache.get(path),
                targetName = target.name,
                options = target.options
            )
        }

        return suppressOverlaps(candidates, target.options)
    }

    private fun locateMatches(
        screen: Mat,
        template: Mat,
        targetName: String,
        options: MatchOptions
    ): List<MatchResult> {
        val roi = options.roi
        val screenRoi = if (roi != null) {
            validateRoi(screen, roi)
            Mat(screen, Rect(roi.x, roi.y, roi.width, roi.height))
        } else {
            screen
        }

        val processedScreen = ImagePreprocessor.preprocess(
            screenRoi,
            options.grayscale,
            options.edgeDetection
        )

        val candidates = mutableListOf<MatchResult>()

        try {
            for (scale in options.scales) {
                if (scale <= 0.0) continue

                val scaledTemplate = resizeTemplate(template, scale)
                val processedTemplate = ImagePreprocessor.preprocess(
                    scaledTemplate,
                    options.grayscale,
                    options.edgeDetection
                )

                try {
                    if (
                        processedTemplate.cols() > processedScreen.cols() ||
                        processedTemplate.rows() > processedScreen.rows()
                    ) {
                        continue
                    }

                    val result = Mat()
                    try {
                        Imgproc.matchTemplate(
                            processedScreen,
                            processedTemplate,
                            result,
                            options.method
                        )

                        candidates += collectCandidates(
                            targetName = targetName,
                            result = result,
                            templateWidth = processedTemplate.cols(),
                            templateHeight = processedTemplate.rows(),
                            method = options.method,
                            threshold = options.threshold,
                            roiOffset = roi,
                            scale = scale
                        )
                    } finally {
                        result.release()
                    }
                } finally {
                    processedTemplate.release()
                    scaledTemplate.release()
                }
            }

            return suppressOverlaps(candidates, options)
        } finally {
            processedScreen.release()
            if (roi != null) {
                screenRoi.release()
            }
        }
    }

    private fun resizeTemplate(template: Mat, scale: Double): Mat {
        if (scale == 1.0) {
            return template.clone()
        }

        val resized = Mat()
        Imgproc.resize(
            template,
            resized,
            Size(template.cols() * scale, template.rows() * scale),
            0.0,
            0.0,
            Imgproc.INTER_AREA
        )
        return resized
    }

    private fun collectCandidates(
        targetName: String,
        result: Mat,
        templateWidth: Int,
        templateHeight: Int,
        method: Int,
        threshold: Double,
        roiOffset: Region?,
        scale: Double
    ): List<MatchResult> {
        val isSqDiff = method == Imgproc.TM_SQDIFF || method == Imgproc.TM_SQDIFF_NORMED
        val baseX = roiOffset?.x ?: 0
        val baseY = roiOffset?.y ?: 0

        val candidates = mutableListOf<MatchResult>()
        for (y in 0 until result.rows()) {
            for (x in 0 until result.cols()) {
                val rawScore = result.get(y, x)[0]
                val normalizedScore = if (isSqDiff) 1.0 - rawScore else rawScore
                if (normalizedScore < threshold) continue

                candidates += MatchResult(
                    targetName = targetName,
                    bounds = Region(
                        x = baseX + x,
                        y = baseY + y,
                        width = templateWidth,
                        height = templateHeight
                    ),
                    score = normalizedScore,
                    scale = scale
                )
            }
        }
        return candidates
    }

    private fun suppressOverlaps(
        candidates: List<MatchResult>,
        options: MatchOptions
    ): List<MatchResult> {
        val selected = mutableListOf<MatchResult>()
        for (candidate in candidates.sortedByDescending { it.score }) {
            if (selected.any { overlapRatio(it.bounds, candidate.bounds) > options.overlapThreshold }) {
                continue
            }

            selected += candidate
            if (selected.size >= options.maxMatches) break
        }
        return selected
    }

    private fun overlapRatio(first: Region, second: Region): Double {
        val left = maxOf(first.x, second.x)
        val top = maxOf(first.y, second.y)
        val right = minOf(first.x + first.width, second.x + second.width)
        val bottom = minOf(first.y + first.height, second.y + second.height)
        val intersectionWidth = (right - left).coerceAtLeast(0)
        val intersectionHeight = (bottom - top).coerceAtLeast(0)
        val intersection = intersectionWidth * intersectionHeight
        if (intersection == 0) return 0.0

        val firstArea = first.width * first.height
        val secondArea = second.width * second.height
        return intersection.toDouble() / (firstArea + secondArea - intersection).toDouble()
    }

    private fun MatchResult.withElapsed(elapsedMillis: Long): MatchResult {
        return copy(elapsedMillis = elapsedMillis)
    }

    private fun elapsedMillisSince(startedNanos: Long): Long {
        return ((System.nanoTime() - startedNanos) / 1_000_000L).coerceAtLeast(1L)
    }

    private fun validateRoi(screen: Mat, roi: Region) {
        require(roi.x >= 0) { "roi.x must be >= 0" }
        require(roi.y >= 0) { "roi.y must be >= 0" }
        require(roi.x + roi.width <= screen.cols()) {
            "roi exceeds screen width."
        }
        require(roi.y + roi.height <= screen.rows()) {
            "roi exceeds screen height."
        }
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

    override fun close() {
        templateCache.close()
    }
}
