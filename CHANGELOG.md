# Changelog

## 0.3.1 - 2026-06-14

### Fixed

- Tightened the default multimodal OCR prompt so models return only directly
  visible text instead of inferring hidden labels, translating, summarizing, or
  completing uncertain text from context.
- Updated the OpenAI-compatible system prompt to use a conservative OCR role
  that reports visual text facts only.

### Verification

- Verified the stricter prompt with the OpenAI Java SDK, Responses API,
  non-streaming mode, `reasoningEffort=high`, and the 13-language Apple Support
  screenshot set.

## 0.3.0 - 2026-06-14

### Added

- Added `kt-visual-ocr-multimodal` as an optional OCR extension module for
  cloud or private multimodal model gateways.
- Added `MultimodalOcrEngine`, which implements the core `OcrEngine` interface
  and parses structured OCR JSON with normalized bounds.
- Added `MultimodalOcrClient` for custom VLM gateways and
  `OpenAiCompatibleMultimodalOcrClient` backed by the OpenAI Java SDK and
  Responses API.
- Added multimodal OCR fallback support so teams can fall back to another
  `OcrEngine`, such as Paddle OCR, when the model returns no text or errors.
- Added configurable multimodal OCR retries for transient client failures,
  malformed model responses, and opt-in empty-result retries.
- Added opt-in online multimodal OCR validation against the same 13 Apple
  Support UI screenshots used by the Paddle OCR multilingual test.

### Verification

- Verified multimodal OCR with the OpenAI Java SDK, Responses API,
  non-streaming mode, `reasoningEffort=high`, and the 13-language Apple Support
  screenshot set.

## 0.2.0 - 2026-06-14

### Added

- Started `kt-visual-ocr-paddle` as an optional OCR extension module.
- Added `PaddleOcrEngine.multilingual13()` for the 13-language UI automation profile.
- Added `OcrLanguage` for Simplified Chinese, English, Korean, Japanese, German,
  French, Spanish, Portuguese, Russian, Thai, Vietnamese, Turkish, and Indonesian.
- Added Paddle OCR model routing for the highest available official ONNX model
  groups in the 13-language profile.
- Added `PaddleOcrResourceManager` for resolving bundled model resources into
  `~/.kt-visual/models/paddleocr/`.
- Added `PaddleOcrRuntime` as the runtime adapter interface for DJL, ONNX
  Runtime, or native Paddle inference integrations.
- Added `PaddleOcrOnnxRuntime` as the preferred cross-platform JVM runtime for
  converted PaddleOCR detection and recognition ONNX models.
- Added Gradle packaging support for embedding official PaddleOCR ONNX
  resources into the OCR extension jar with `-PincludePaddleOcrModels=true`.
- Added automatic PaddleOCR resource caching/downloading in
  `PaddleOcrResourceManager` so callers do not need to manage model files.
- Added support for reading PaddleOCR `inference.yml` `character_dict` entries
  directly when decoding ONNX recognizer output.
- Added language-group routing for the 13-language profile:
  - PP-OCRv6 medium detector shared across all groups;
  - PP-OCRv6 medium recognizer for Simplified Chinese, English, and Japanese;
  - PP-OCRv5 mobile recognizers for Korean, Latin, Cyrillic, and Thai groups,
    because official server ONNX packages are not published for these groups.
- Added opt-in online 13-language OCR validation against real Apple Support UI
  screenshots.
- Added `PaddleOcrCliRuntime` for practical PaddleOCR integration through an
  official CLI or internal wrapper that emits OCR results as JSONL.
- Added OCR text selection options with exact, contains, and regex matching.
- Added `Visual.recognizeText`, `Visual.findText`, and `Visual.findAllText`
  for OCR over encoded screenshot bytes.
- Added OCR-driven `UiVision` actions:
  - `recognizeText`
  - `findText`
  - `findAllText`
  - `waitForText`
  - `clickText`
  - `doubleClickText`
  - `assertTextVisible`

### Notes

- The preferred runtime route is ONNX Runtime. It requires converted PaddleOCR
  detection and recognition models plus dictionaries. The process-based
  `PaddleOcrCliRuntime` remains available for teams that already run official
  PaddleOCR through Python or an internal wrapper.

## 0.1.0 - Stable Baseline

First stable release of `kt-visual`, a Kotlin/JVM visual automation helper built
on OpenCV.

### Public API

- Added `Visual` as the primary facade for automation projects.
- Added `VisualAssertions` as the primary assertion facade.
- Kept lower-level `cv` APIs public for advanced extension and diagnostics.
- Supported common image inputs across the main workflows:
  - encoded PNG/JPEG/WebP bytes via `ByteArray`;
  - image files via `Path` and `File`;
  - JVM images via `BufferedImage`;
  - OpenCV images via `Mat`.

### Visual Recognition

- Template matching with threshold, ROI, grayscale, edge detection, and
  multi-scale matching.
- File-backed template caching.
- Multi-template targets through `UiTarget.alternateImagePaths`.
- `findAll` for repeated UI elements with overlap suppression.
- Match metadata including bounds, center, score, scale, and elapsed time.
- Debug image output for successful and failed template searches.

### Image Analysis

- Same-size image comparison for baseline/current visual regression checks.
- Resized comparison for screenshots with matching content but different sizes.
- Common-region detection and comparison for partially overlapping images.
- Changed-region detection with merged bounding boxes.
- Broad semantic color detection using named HSV color ranges.
- Exact RGB color detection with tolerance.
- Screenshot quality analysis for blank, mostly dark, mostly bright, and blurred
  captures.
- Light/dark/mixed theme detection.
- Text-like block detection as a lightweight OCR pre-filter.
- Repeated row and column detection for list and grid analysis.
- Heuristic occlusion detection for modal masks and blocking overlays.

### Automation Helpers

- `ScreenSource` and `UiInput` abstractions for driver-independent integration.
- `UiVision` facade for screenshot-driven find, wait, click, double-click,
  visibility, invisibility, and stability flows.
- Real double-click support through `RobotUiInput`.
- Region helpers for relative positioning.
- Match-result sorting and filtering helpers.
- Visual report artifacts for screenshots, match annotations, and diff heatmaps.
- Layout assertions for overlap, containment, alignment, and ordering.

### Build And Verification

- Kotlin Gradle plugin upgraded to `2.4.0`.
- Gradle wrapper uses `9.3.0`.
- Java toolchain targets Java 21.
- Verified with:
  - `./gradlew test`
  - `./gradlew build`
  - `git diff --check`

### Notes

- `kt-visual` does not manage Appium, Selenium, ADB, desktop sessions, or app
  launch flows. Host automation projects provide screenshot and input adapters.
- OCR is intentionally exposed as an extension interface instead of being
  bundled into the stable core.
