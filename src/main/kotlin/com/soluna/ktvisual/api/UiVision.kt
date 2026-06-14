package com.soluna.ktvisual.api


import com.soluna.ktvisual.cv.MatConverters
import com.soluna.ktvisual.cv.TemplateLocator
import com.soluna.ktvisual.model.MatchResult
import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.OcrTextMatchOptions
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.UiTarget
import com.soluna.ktvisual.utils.RetryWaiter
import java.time.Duration

class UiVision(
    private val screenSource: ScreenSource,
    private val input: UiInput? = null,
    private val locator: TemplateLocator = TemplateLocator(),
    private val ocrEngine: OcrEngine? = null
) {

    fun find(target: UiTarget): MatchResult? {
        val image = screenSource.capture()
        val mat = MatConverters.fromBufferedImage(image)

        return try {
            locator.find(mat, target)
        } finally {
            mat.release()
        }
    }

    fun findAll(target: UiTarget): List<MatchResult> {
        val image = screenSource.capture()
        val mat = MatConverters.fromBufferedImage(image)

        return try {
            locator.findAll(mat, target)
        } finally {
            mat.release()
        }
    }

    /**
     * Runs OCR on the current screenshot.
     *
     * Configure [ocrEngine] in the constructor or pass [engine] for this call.
     */
    fun recognizeText(
        roi: Region? = null,
        engine: OcrEngine? = null
    ): List<OcrText> {
        val activeEngine = engine ?: configuredOcrEngine()
        return activeEngine.recognize(screenSource.capture(), roi)
    }

    /**
     * Finds the first OCR text on the current screen that matches [query].
     */
    fun findText(
        query: String,
        options: OcrTextMatchOptions = OcrTextMatchOptions(),
        engine: OcrEngine? = null
    ): OcrText? {
        require(query.isNotBlank()) { "query must not be blank." }
        return recognizeText(options.roi, engine)
            .filter { Visual.matchesText(it, query, options) }
            .sortedWith(compareBy<OcrText> { it.bounds.y }.thenBy { it.bounds.x })
            .firstOrNull()
    }

    /**
     * Finds all OCR texts on the current screen that match [query].
     */
    fun findAllText(
        query: String,
        options: OcrTextMatchOptions = OcrTextMatchOptions(),
        engine: OcrEngine? = null
    ): List<OcrText> {
        require(query.isNotBlank()) { "query must not be blank." }
        return recognizeText(options.roi, engine)
            .filter { Visual.matchesText(it, query, options) }
            .sortedWith(compareBy<OcrText> { it.bounds.y }.thenBy { it.bounds.x })
    }

    /**
     * Waits until OCR finds [query] on the current screen.
     */
    fun waitForText(
        query: String,
        options: OcrTextMatchOptions = OcrTextMatchOptions(),
        timeout: Duration = Duration.ofSeconds(10),
        interval: Duration = Duration.ofMillis(300),
        engine: OcrEngine? = null
    ): OcrText {
        return RetryWaiter.waitUntilNotNull(timeout, interval) {
            findText(query, options, engine)
        } ?: throw VisionException(
            "Text not found within ${timeout.toMillis()}ms: $query"
        )
    }

    fun waitFor(
        target: UiTarget,
        timeout: Duration = Duration.ofSeconds(10),
        interval: Duration = Duration.ofMillis(300)
    ): MatchResult {
        return RetryWaiter.waitUntilNotNull(timeout, interval) {
            find(target)
        } ?: throw VisionException(
            "Target not found within ${timeout.toMillis()}ms: ${target.name}"
        )
    }

    fun exists(
        target: UiTarget,
        timeout: Duration = Duration.ofSeconds(3),
        interval: Duration = Duration.ofMillis(300)
    ): Boolean {
        return RetryWaiter.waitUntilNotNull(timeout, interval) {
            find(target)
        } != null
    }

    fun waitGone(
        target: UiTarget,
        timeout: Duration = Duration.ofSeconds(5),
        interval: Duration = Duration.ofMillis(300)
    ): Boolean {
        return RetryWaiter.waitUntil(timeout, interval) {
            find(target) == null
        }
    }

    fun assertNotVisible(
        target: UiTarget,
        timeout: Duration = Duration.ofSeconds(5),
        interval: Duration = Duration.ofMillis(300)
    ) {
        if (!waitGone(target, timeout, interval)) {
            throw VisionException(
                "Target is still visible after ${timeout.toMillis()}ms: ${target.name}"
            )
        }
    }

    fun waitStable(
        target: UiTarget,
        timeout: Duration = Duration.ofSeconds(5),
        interval: Duration = Duration.ofMillis(300),
        samples: Int = 3,
        tolerancePx: Int = 1
    ): MatchResult {
        require(samples > 0) { "samples must be > 0." }
        require(tolerancePx >= 0) { "tolerancePx must be >= 0." }

        var previous: MatchResult? = null
        var stableCount = 0
        var stableResult: MatchResult? = null

        val stable = RetryWaiter.waitUntil(timeout, interval) {
            val current = find(target) ?: run {
                previous = null
                stableCount = 0
                stableResult = null
                return@waitUntil false
            }

            val last = previous
            stableCount = if (last != null && isNear(last.bounds, current.bounds, tolerancePx)) {
                stableCount + 1
            } else {
                1
            }
            previous = current
            stableResult = current

            stableCount >= samples
        }

        return if (stable) {
            stableResult ?: throw VisionException("Stable target result is unavailable: ${target.name}")
        } else {
            throw VisionException(
                "Target did not become stable within ${timeout.toMillis()}ms: ${target.name}"
            )
        }
    }

    fun click(
        target: UiTarget,
        timeout: Duration = Duration.ofSeconds(10),
        interval: Duration = Duration.ofMillis(300),
        offsetX: Int = 0,
        offsetY: Int = 0
    ): MatchResult {
        val uiInput = input ?: throw VisionException("UiInput is not configured.")

        val result = waitFor(target, timeout, interval)
        uiInput.click(result.center.x + offsetX, result.center.y + offsetY)
        return result
    }

    fun doubleClick(
        target: UiTarget,
        timeout: Duration = Duration.ofSeconds(10),
        interval: Duration = Duration.ofMillis(300),
        offsetX: Int = 0,
        offsetY: Int = 0
    ): MatchResult {
        val uiInput = input ?: throw VisionException("UiInput is not configured.")

        val result = waitFor(target, timeout, interval)
        uiInput.doubleClick(result.center.x + offsetX, result.center.y + offsetY)
        return result
    }

    /**
     * Clicks the center of the first OCR text that matches [query].
     */
    fun clickText(
        query: String,
        options: OcrTextMatchOptions = OcrTextMatchOptions(),
        timeout: Duration = Duration.ofSeconds(10),
        interval: Duration = Duration.ofMillis(300),
        offsetX: Int = 0,
        offsetY: Int = 0,
        engine: OcrEngine? = null
    ): OcrText {
        val uiInput = input ?: throw VisionException("UiInput is not configured.")
        val result = waitForText(query, options, timeout, interval, engine)
        uiInput.click(result.bounds.center.x + offsetX, result.bounds.center.y + offsetY)
        return result
    }

    /**
     * Double-clicks the center of the first OCR text that matches [query].
     */
    fun doubleClickText(
        query: String,
        options: OcrTextMatchOptions = OcrTextMatchOptions(),
        timeout: Duration = Duration.ofSeconds(10),
        interval: Duration = Duration.ofMillis(300),
        offsetX: Int = 0,
        offsetY: Int = 0,
        engine: OcrEngine? = null
    ): OcrText {
        val uiInput = input ?: throw VisionException("UiInput is not configured.")
        val result = waitForText(query, options, timeout, interval, engine)
        uiInput.doubleClick(result.bounds.center.x + offsetX, result.bounds.center.y + offsetY)
        return result
    }

    fun assertVisible(
        target: UiTarget,
        timeout: Duration = Duration.ofSeconds(5)
    ): MatchResult {
        return waitFor(target, timeout)
    }

    /**
     * Fails when OCR cannot find [query] within [timeout].
     */
    fun assertTextVisible(
        query: String,
        options: OcrTextMatchOptions = OcrTextMatchOptions(),
        timeout: Duration = Duration.ofSeconds(5),
        engine: OcrEngine? = null
    ): OcrText {
        return waitForText(query, options, timeout, engine = engine)
    }

    private fun isNear(first: Region, second: Region, tolerancePx: Int): Boolean {
        return kotlin.math.abs(first.x - second.x) <= tolerancePx &&
            kotlin.math.abs(first.y - second.y) <= tolerancePx &&
            kotlin.math.abs(first.width - second.width) <= tolerancePx &&
            kotlin.math.abs(first.height - second.height) <= tolerancePx
    }

    private fun configuredOcrEngine(): OcrEngine {
        return ocrEngine ?: throw VisionException(
            "OcrEngine is not configured. Pass one to UiVision or to this OCR method."
        )
    }
}
