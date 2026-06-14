package com.soluna.ktvisual.ocr.paddle

import com.soluna.ktvisual.model.OcrText

/**
 * Runtime adapter for executing one resolved Paddle OCR model group.
 *
 * This interface keeps `kt-visual-ocr-paddle` independent from a specific JVM
 * inference technology. Implementations can use DJL PaddlePaddle, ONNX Runtime
 * with converted models, or native Paddle inference.
 */
fun interface PaddleOcrRuntime : AutoCloseable {

    /**
     * Recognizes text for one routed model group.
     */
    fun recognize(request: PaddleOcrRequest): List<OcrText>

    override fun close() = Unit
}

/**
 * Runtime used when no concrete Paddle inference adapter has been installed.
 */
object UnavailablePaddleOcrRuntime : PaddleOcrRuntime {
    override fun recognize(request: PaddleOcrRequest): List<OcrText> {
        throw PaddleOcrRuntimeUnavailableException(
            "No Paddle OCR runtime is configured. Provide a PaddleOcrRuntime implementation " +
                "for DJL, ONNX Runtime, or native Paddle inference."
        )
    }
}

class PaddleOcrRuntimeUnavailableException(message: String) : IllegalStateException(message)
