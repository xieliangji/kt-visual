package com.soluna.ktvisual.ocr.paddle

/**
 * Model routing table for the bundled Paddle OCR multilingual profile.
 *
 * The catalog is intentionally small: UI automation usually needs reliable
 * text actions more than exposing every PaddleOCR model variant.
 */
object PaddleOcrModelCatalog {

    val cjkAndEnglish = PaddleOcrModelSpec(
        id = "ppocrv6-cjk-en",
        displayName = "PP-OCRv6 CJK and English",
        supportedLanguages = setOf(
            OcrLanguage.SIMPLIFIED_CHINESE,
            OcrLanguage.ENGLISH,
            OcrLanguage.JAPANESE
        ),
        detectorResource = "models/paddleocr/ppocrv6-det/det",
        recognizerResource = "models/paddleocr/ppocrv6-cjk-en/rec",
        dictionaryResource = "models/paddleocr/ppocrv6-cjk-en/inference.yml",
        remoteResources = PaddleOcrRemoteResources(
            detectorModelUrl = "$HF/PaddlePaddle/PP-OCRv6_medium_det_onnx/resolve/main/inference.onnx",
            recognizerModelUrl = "$HF/PaddlePaddle/PP-OCRv6_medium_rec_onnx/resolve/main/inference.onnx",
            dictionaryUrl = "$HF/PaddlePaddle/PP-OCRv6_medium_rec_onnx/raw/main/inference.yml"
        )
    )

    val korean = PaddleOcrModelSpec(
        id = "ppocrv5-korean",
        displayName = "PP-OCRv5 Korean",
        supportedLanguages = setOf(OcrLanguage.KOREAN),
        detectorResource = "models/paddleocr/ppocrv6-det/det",
        recognizerResource = "models/paddleocr/ppocrv5-korean/rec",
        dictionaryResource = "models/paddleocr/ppocrv5-korean/inference.yml",
        remoteResources = PaddleOcrRemoteResources(
            detectorModelUrl = "$HF/PaddlePaddle/PP-OCRv6_medium_det_onnx/resolve/main/inference.onnx",
            recognizerModelUrl = "$HF/PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx",
            dictionaryUrl = "$HF/PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx/raw/main/inference.yml"
        )
    )

    val latin = PaddleOcrModelSpec(
        id = "ppocrv5-latin",
        displayName = "PP-OCRv5 Latin",
        supportedLanguages = setOf(
            OcrLanguage.GERMAN,
            OcrLanguage.FRENCH,
            OcrLanguage.SPANISH,
            OcrLanguage.PORTUGUESE,
            OcrLanguage.VIETNAMESE,
            OcrLanguage.TURKISH,
            OcrLanguage.INDONESIAN
        ),
        detectorResource = "models/paddleocr/ppocrv6-det/det",
        recognizerResource = "models/paddleocr/ppocrv5-latin/rec",
        dictionaryResource = "models/paddleocr/ppocrv5-latin/inference.yml",
        remoteResources = PaddleOcrRemoteResources(
            detectorModelUrl = "$HF/PaddlePaddle/PP-OCRv6_medium_det_onnx/resolve/main/inference.onnx",
            recognizerModelUrl = "$HF/PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx",
            dictionaryUrl = "$HF/PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx/raw/main/inference.yml"
        )
    )

    val cyrillic = PaddleOcrModelSpec(
        id = "ppocrv5-cyrillic",
        displayName = "PP-OCRv5 Cyrillic",
        supportedLanguages = setOf(OcrLanguage.RUSSIAN),
        detectorResource = "models/paddleocr/ppocrv6-det/det",
        recognizerResource = "models/paddleocr/ppocrv5-cyrillic/rec",
        dictionaryResource = "models/paddleocr/ppocrv5-cyrillic/inference.yml",
        remoteResources = PaddleOcrRemoteResources(
            detectorModelUrl = "$HF/PaddlePaddle/PP-OCRv6_medium_det_onnx/resolve/main/inference.onnx",
            recognizerModelUrl = "$HF/PaddlePaddle/cyrillic_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx",
            dictionaryUrl = "$HF/PaddlePaddle/cyrillic_PP-OCRv5_mobile_rec_onnx/raw/main/inference.yml"
        )
    )

    val thai = PaddleOcrModelSpec(
        id = "ppocrv5-thai",
        displayName = "PP-OCRv5 Thai",
        supportedLanguages = setOf(OcrLanguage.THAI),
        detectorResource = "models/paddleocr/ppocrv6-det/det",
        recognizerResource = "models/paddleocr/ppocrv5-thai/rec",
        dictionaryResource = "models/paddleocr/ppocrv5-thai/inference.yml",
        remoteResources = PaddleOcrRemoteResources(
            detectorModelUrl = "$HF/PaddlePaddle/PP-OCRv6_medium_det_onnx/resolve/main/inference.onnx",
            recognizerModelUrl = "$HF/PaddlePaddle/th_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx",
            dictionaryUrl = "$HF/PaddlePaddle/th_PP-OCRv5_mobile_rec_onnx/raw/main/inference.yml"
        )
    )

    val all: List<PaddleOcrModelSpec> = listOf(cjkAndEnglish, korean, latin, cyrillic, thai)

    /**
     * Returns the minimum model groups needed for [languages].
     */
    fun route(languages: Set<OcrLanguage>): List<PaddleOcrModelSpec> {
        require(languages.isNotEmpty()) { "languages must not be empty." }
        val unsupported = languages - all.flatMap { it.supportedLanguages }.toSet()
        require(unsupported.isEmpty()) { "Unsupported OCR languages: $unsupported" }

        return all.filter { spec -> spec.supportedLanguages.any { it in languages } }
    }

    private const val HF = "https://huggingface.co"
}
