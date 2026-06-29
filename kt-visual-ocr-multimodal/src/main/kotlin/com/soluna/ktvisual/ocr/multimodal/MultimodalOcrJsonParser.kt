package com.soluna.ktvisual.ocr.multimodal

import com.soluna.ktvisual.model.OcrText
import com.soluna.ktvisual.model.Region
import kotlin.math.roundToInt

internal object MultimodalOcrJsonParser {

    fun parse(content: String, imageWidth: Int, imageHeight: Int): List<OcrText> {
        require(imageWidth > 0) { "imageWidth must be > 0." }
        require(imageHeight > 0) { "imageHeight must be > 0." }

        val json = extractJson(content)
        val root = SimpleJsonParser(json).parse()
        val items = when (root) {
            is List<*> -> root
            is Map<*, *> -> firstArray(
                root,
                "texts",
                "matches",
                "items",
                "results",
                "ocr",
                "ocr_results",
                "data"
            ) ?: if (root.containsKey("text")) listOf(root) else emptyList<Any?>()
            else -> emptyList<Any?>()
        }

        return items.mapNotNull { item ->
            val obj = item as? Map<*, *> ?: return@mapNotNull null
            val text = obj.string("text") ?: obj.string("label") ?: obj.string("content") ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            val bounds = parseBounds(obj, imageWidth, imageHeight) ?: return@mapNotNull null
            val confidence = obj.number("confidence") ?: obj.number("score")
            OcrText(text = text, bounds = bounds, confidence = confidence?.coerceIn(0.0, 1.0))
        }
    }

    private fun parseBounds(obj: Map<*, *>, imageWidth: Int, imageHeight: Int): Region? {
        val boundsObject = obj.map("bounds") ?: obj.map("box") ?: obj.map("bbox")
        if (boundsObject != null) {
            val x = boundsObject.number("x")
            val y = boundsObject.number("y")
            val width = boundsObject.number("width") ?: boundsObject.number("w")
            val height = boundsObject.number("height") ?: boundsObject.number("h")
            if (x != null && y != null && width != null && height != null) {
                return regionFromXywh(x, y, width, height, imageWidth, imageHeight)
            }

            val x1 = boundsObject.number("x1") ?: boundsObject.number("left")
            val y1 = boundsObject.number("y1") ?: boundsObject.number("top")
            val x2 = boundsObject.number("x2") ?: boundsObject.number("right")
            val y2 = boundsObject.number("y2") ?: boundsObject.number("bottom")
            if (x1 != null && y1 != null && x2 != null && y2 != null) {
                return regionFromCorners(x1, y1, x2, y2, imageWidth, imageHeight)
            }
        }

        obj.array("bounds")?.let { array ->
            val values = firstFourNumbers(array) ?: return@let
            val (x, y, width, height) = values
            return regionFromXywh(x, y, width, height, imageWidth, imageHeight)
        }

        (obj.array("box") ?: obj.array("bbox"))?.let { array ->
            val values = firstFourNumbers(array) ?: return@let
            val (x1, y1, x2, y2) = values
            return regionFromCorners(x1, y1, x2, y2, imageWidth, imageHeight)
        }

        return null
    }

    private fun firstFourNumbers(array: List<*>): List<Double>? {
        if (array.size < 4) return null
        val values = array.take(4).mapNotNull { (it as? Number)?.toDouble() }
        return values.takeIf { it.size == 4 }
    }

    private fun regionFromXywh(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        imageWidth: Int,
        imageHeight: Int
    ): Region {
        val normalized = listOf(x, y, width, height).all { it in 0.0..1.0 }
        val absoluteX = if (normalized) x * imageWidth else x
        val absoluteY = if (normalized) y * imageHeight else y
        val absoluteWidth = if (normalized) width * imageWidth else width
        val absoluteHeight = if (normalized) height * imageHeight else height
        return clampRegion(absoluteX, absoluteY, absoluteWidth, absoluteHeight, imageWidth, imageHeight)
    }

    private fun regionFromCorners(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        imageWidth: Int,
        imageHeight: Int
    ): Region {
        val normalized = listOf(x1, y1, x2, y2).all { it in 0.0..1.0 }
        val absoluteX1 = if (normalized) x1 * imageWidth else x1
        val absoluteY1 = if (normalized) y1 * imageHeight else y1
        val absoluteX2 = if (normalized) x2 * imageWidth else x2
        val absoluteY2 = if (normalized) y2 * imageHeight else y2
        return clampRegion(
            x = absoluteX1,
            y = absoluteY1,
            width = absoluteX2 - absoluteX1,
            height = absoluteY2 - absoluteY1,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    private fun clampRegion(x: Double, y: Double, width: Double, height: Double, imageWidth: Int, imageHeight: Int): Region {
        val left = x.roundToInt().coerceIn(0, imageWidth - 1)
        val top = y.roundToInt().coerceIn(0, imageHeight - 1)
        val right = (x + width).roundToInt().coerceIn(left + 1, imageWidth)
        val bottom = (y + height).roundToInt().coerceIn(top + 1, imageHeight)
        return Region(left, top, right - left, bottom - top)
    }

    private fun firstArray(obj: Map<*, *>, vararg keys: String): List<*>? {
        return keys.firstNotNullOfOrNull { key -> obj[key] as? List<*> }
    }

    private fun extractJson(content: String): String {
        val trimmed = content.trim()

        fencedJson(trimmed)?.let { return it }

        val candidates = mutableListOf<JsonCandidate>()
        trimmed.indices.forEach { index ->
            if (trimmed[index] == '{' || trimmed[index] == '[') {
                balancedJsonAt(trimmed, index)?.let { candidate ->
                    parseJson(candidate)?.let { root ->
                        candidates += JsonCandidate(candidate, ocrSchemaScore(root))
                    }
                }
            }
        }

        return candidates.maxWithOrNull(compareBy<JsonCandidate> { it.score }.thenBy { candidates.indexOf(it) })?.content
            ?: throw MultimodalOcrException("Multimodal OCR response did not contain JSON.")
    }

    private fun fencedJson(content: String): String? {
        val fenceStart = content.indexOf("```")
        if (fenceStart < 0) return null

        val jsonStart = (fenceStart + 3 until content.length)
            .firstOrNull { index -> content[index] == '{' || content[index] == '[' }
            ?: return null

        val fenceEnd = content.indexOf("```", startIndex = jsonStart)
        if (fenceEnd < 0) return null

        val candidate = content.substring(jsonStart, fenceEnd).trim()
        return candidate.takeIf { parseJson(it) != null }
    }

    private fun balancedJsonAt(content: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaping = false
        for (index in start until content.length) {
            val char = content[index]
            if (escaping) {
                escaping = false
                continue
            }
            if (char == '\\' && inString) {
                escaping = true
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (inString) continue

            when (char) {
                '{', '[' -> depth += 1
                '}', ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return content.substring(start, index + 1)
                    }
                }
            }
        }

        return null
    }

    private fun parseJson(candidate: String): Any? {
        return runCatching { SimpleJsonParser(candidate).parse() }.getOrNull()
    }

    private fun ocrSchemaScore(root: Any?): Int {
        return when (root) {
            is List<*> -> if (root.any(::looksLikeOcrItem)) 4 else 1
            is Map<*, *> -> {
                val array = firstArray(root, "texts", "matches", "items", "results", "ocr", "ocr_results", "data")
                when {
                    array?.any(::looksLikeOcrItem) == true -> 5
                    looksLikeOcrItem(root) -> 3
                    array != null -> 2
                    else -> 1
                }
            }
            else -> 0
        }
    }

    private fun looksLikeOcrItem(item: Any?): Boolean {
        val obj = item as? Map<*, *> ?: return false
        val hasText = obj.containsKey("text") || obj.containsKey("label") || obj.containsKey("content")
        val hasBounds = obj.containsKey("bounds") || obj.containsKey("box") || obj.containsKey("bbox")
        return hasText && hasBounds
    }

    private data class JsonCandidate(val content: String, val score: Int)

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.number(key: String): Double? = (this[key] as? Number)?.toDouble()

    private fun Map<*, *>.map(key: String): Map<*, *>? = this[key] as? Map<*, *>

    private fun Map<*, *>.array(key: String): List<*>? = this[key] as? List<*>
}

internal class SimpleJsonParser(private val input: String) {
    private var index = 0

    fun parse(): Any? {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        if (index != input.length) {
            error("Unexpected trailing JSON content.")
        }
        return value
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            else -> parseNumber()
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val result = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (consume('}')) return result
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            result[key] = parseValue()
            skipWhitespace()
            if (consume('}')) return result
            expect(',')
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val result = mutableListOf<Any?>()
        skipWhitespace()
        if (consume(']')) return result
        while (true) {
            result += parseValue()
            skipWhitespace()
            if (consume(']')) return result
            expect(',')
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (index < input.length) {
            val char = input[index++]
            when (char) {
                '"' -> return builder.toString()
                '\\' -> builder.append(parseEscape())
                else -> builder.append(char)
            }
        }
        error("Unterminated JSON string.")
    }

    private fun parseEscape(): Char {
        val escaped = next()
        return when (escaped) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> error("Unsupported JSON escape: \\$escaped")
        }
    }

    private fun parseUnicodeEscape(): Char {
        if (index + 4 > input.length) {
            error("Incomplete unicode escape.")
        }
        val value = input.substring(index, index + 4).toInt(16)
        index += 4
        return value.toChar()
    }

    private fun parseNumber(): Number {
        val start = index
        if (peek() == '-') index += 1
        while (peekOrNull()?.isDigit() == true) index += 1
        if (peekOrNull() == '.') {
            index += 1
            while (peekOrNull()?.isDigit() == true) index += 1
        }
        if (peekOrNull() == 'e' || peekOrNull() == 'E') {
            index += 1
            if (peekOrNull() == '+' || peekOrNull() == '-') index += 1
            while (peekOrNull()?.isDigit() == true) index += 1
        }
        if (start == index) {
            error("Expected JSON value.")
        }
        val raw = input.substring(start, index)
        return raw.toDoubleOrNull() ?: error("Invalid JSON number: $raw")
    }

    private fun parseLiteral(literal: String, value: Any?): Any? {
        if (!input.startsWith(literal, index)) {
            error("Expected JSON literal: $literal")
        }
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (peekOrNull()?.isWhitespace() == true) {
            index += 1
        }
    }

    private fun consume(expected: Char): Boolean {
        if (peekOrNull() == expected) {
            index += 1
            return true
        }
        return false
    }

    private fun expect(expected: Char) {
        val actual = next()
        if (actual != expected) {
            error("Expected '$expected' but got '$actual'.")
        }
    }

    private fun next(): Char {
        if (index >= input.length) {
            error("Unexpected end of JSON.")
        }
        return input[index++]
    }

    private fun peek(): Char = peekOrNull() ?: error("Unexpected end of JSON.")

    private fun peekOrNull(): Char? = input.getOrNull(index)

    private fun error(message: String): Nothing {
        throw MultimodalOcrException(message)
    }
}
