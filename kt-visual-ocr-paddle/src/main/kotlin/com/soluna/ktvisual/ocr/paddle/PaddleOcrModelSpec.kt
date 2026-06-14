package com.soluna.ktvisual.ocr.paddle

import java.nio.file.Path

/**
 * Logical Paddle OCR model group used by the 13-language profile.
 *
 * A group represents one recognizer family and its dictionary. Multiple
 * languages can share a group when PaddleOCR provides a multilingual recognizer
 * for the script family.
 */
data class PaddleOcrModelSpec(
    val id: String,
    val displayName: String,
    val supportedLanguages: Set<OcrLanguage>,
    val detectorResource: String,
    val recognizerResource: String,
    val dictionaryResource: String,
    val remoteResources: PaddleOcrRemoteResources? = null
)

/**
 * Download locations for a model group.
 *
 * These URLs are used by [PaddleOcrResourceManager] to populate its local cache
 * so automation projects do not need to download PaddleOCR model files by hand.
 * Teams with an internal artifact mirror can provide a custom
 * [PaddleOcrResourceResolver] instead.
 */
data class PaddleOcrRemoteResources(
    val detectorModelUrl: String,
    val recognizerModelUrl: String,
    val dictionaryUrl: String,
    val modelFileName: String = "inference.onnx"
)

/**
 * Resolved local resource paths for one Paddle OCR model group.
 */
data class ResolvedPaddleOcrModel(
    val spec: PaddleOcrModelSpec,
    val detectorDirectory: Path,
    val recognizerDirectory: Path,
    val dictionaryPath: Path
)

/**
 * Request passed to a concrete Paddle OCR runtime implementation.
 */
data class PaddleOcrRequest(
    val image: java.awt.image.BufferedImage,
    val model: ResolvedPaddleOcrModel,
    val languages: Set<OcrLanguage>
)
