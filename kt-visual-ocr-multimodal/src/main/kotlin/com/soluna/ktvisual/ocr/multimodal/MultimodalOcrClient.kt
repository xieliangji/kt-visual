package com.soluna.ktvisual.ocr.multimodal

import java.util.Base64

/**
 * Minimal client abstraction for cloud or private multimodal model gateways.
 *
 * Implementations receive an encoded screenshot crop and must return the model
 * text response. [MultimodalOcrEngine] owns the OCR JSON schema and parsing.
 */
fun interface MultimodalOcrClient {
    fun complete(request: MultimodalOcrRequest): String
}

data class MultimodalOcrRequest(
    val imageBytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val prompt: String
) {
    init {
        require(imageBytes.isNotEmpty()) { "imageBytes must not be empty." }
        require(width > 0) { "width must be > 0." }
        require(height > 0) { "height must be > 0." }
        require(mimeType.isNotBlank()) { "mimeType must not be blank." }
        require(prompt.isNotBlank()) { "prompt must not be blank." }
    }

    fun base64Image(): String = Base64.getEncoder().encodeToString(imageBytes)

    fun dataUrl(): String = "data:$mimeType;base64,${base64Image()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MultimodalOcrRequest

        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (mimeType != other.mimeType) return false
        if (prompt != other.prompt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + prompt.hashCode()
        return result
    }
}
