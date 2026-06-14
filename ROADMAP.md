# kt-visual Roadmap

This roadmap captures the next development phase for the current Kotlin/JVM
OpenCV UI vision module.

## Current Baseline

The project already has the MVP shape:

- OpenCV runtime loading through `org.openpnp:opencv`.
- `BufferedImage` to OpenCV `Mat` conversion.
- Template matching with threshold, ROI, grayscale, and multi-scale options.
- `UiVision` facade with `find`, `waitFor`, `exists`, `click`, and `assertVisible`.
- Driver-independent screenshot and input abstractions.
- Desktop `Robot` screenshot and click adapters.

Known status:

- Phase 1 stabilization is implemented.
- Phase 2 core features are implemented.
- Phase 3 API ergonomics are implemented.
- Phase 4 lightweight extensions are implemented.
- Phase 5 visual analysis facade and app-automation helpers are implemented.
- The project currently targets Java 21 through the Gradle toolchain.

## Phase 1: Stabilize 0.1.x

Goal: make the current MVP reliable enough to publish and consume from another
automation project.

Tasks:

- Remove generated Gradle state from version control if it is staged.
- Keep the documented Java requirement aligned with the Gradle toolchain.
- Add validation for `MatchOptions`:
  - `threshold` must be in a valid range.
  - `scales` must contain at least one positive scale.
  - unsupported OpenCV template matching methods should fail early.
- Add focused tests for:
  - exact template hit.
  - no match below threshold.
  - ROI coordinate offset.
  - invalid ROI bounds.
  - multi-scale match.
  - grayscale versus color matching.
  - template larger than search image.
- Confirm `./gradlew test` and `./gradlew build` pass in a normal developer environment.

Acceptance criteria:

- `./gradlew test` passes.
- `./gradlew build` passes.
- Public README examples match the compiled API.
- No generated Gradle caches are tracked.

## Phase 2: Implement 0.2.x Core Features

Goal: improve performance, debuggability, and practical UI automation coverage.

Tasks:

- Add template caching for file-backed `UiTarget` templates. Done.
- Implement `findAll` with duplicate suppression for overlapping matches. Done.
- Support multi-template targets for different UI states, themes, or DPI captures. Done.
- Implement debug output when `MatchOptions.debug` is enabled:
  - matched bounds.
  - center point.
  - target name.
  - score.
  - scale.
  - ROI boundary.
  - failed search screenshot.
  Done.
- Record match timing and expose it through a diagnostic result or optional metadata. Done.

Acceptance criteria:

- Repeated lookup avoids repeated disk reads for unchanged templates.
- `findAll` returns stable ordered matches without obvious duplicate rectangles.
- Debug images are useful enough to diagnose common threshold, ROI, and scale issues.
- Debug output can be disabled with zero file output.

## Phase 3: Improve API Ergonomics

Goal: make common automation flows less repetitive without coupling to Appium,
Selenium, ADB, or a specific desktop driver.

Tasks:

- Add click offset support. Done.
- Add `waitGone`. Done.
- Add `assertNotVisible`. Done.
- Consider `waitStable` for UI transitions. Done.
- Decide whether `RobotUiInput.type` should remain unsupported or use clipboard paste. Done: keep text typing in the external automation layer.
- Add documentation for safe high-risk actions with page context checks. Done.

Acceptance criteria:

- Existing 0.1 API remains source-compatible where practical.
- New helpers reduce boilerplate but keep driver behavior outside the core module.

## Phase 4: Explore 0.3.x Extensions

Goal: add adjacent image assertions and extensibility without bloating the core
template matcher.

Candidate features:

- Relative positioning. Done.
- Image difference assertions. Done.
- Color detection. Done.
- Edge-based matching. Done.
- OCR extension interface. Done.
- Feature matching through ORB or SIFT as a separate matcher type. Done: `VisualLocator` is the extension point; concrete ORB/SIFT implementation can live outside the default template matcher.
- Multimodal OCR engine:
  - cloud or private multimodal model client abstraction. Done.
  - OCR fallback for weak text recognition cases. Done.
  - structured JSON output with normalized coordinates. Done.
  - natural-language UI target location. Planned.

Acceptance criteria:

- Extension points are explicit. Done.
- OCR and feature matching do not force heavyweight dependencies into the default path. Done.

## Verification Notes

The current implementation has been verified with:

```bash
./gradlew test
./gradlew build
```

## Phase 5: Visual Analysis API Surface

Goal: keep external automation projects on a small, discoverable API surface
while expanding practical computer-vision checks.

Implemented:

- `Visual` facade as the primary visual-analysis entry point.
- `VisualAssertions` as the primary assertion entry point.
- Encoded image bytes, file paths, buffered images, and `Mat` inputs across
  common workflows.
- Changed-region detection for baseline/current visual regression analysis.
- Screen quality analysis for blank, dark, bright, and blurred screenshots.
- Theme detection for light, dark, and mixed pages.
- Text-like block detection as a lightweight OCR pre-filter.
- Repeated row/column region detection for list and grid analysis.
- Visual occlusion heuristic for modal masks and blocking overlays.
- Visual stability waiting over a `ScreenSource`.
- Layout assertions for overlap, safe-area containment, center alignment, and
  left-to-right ordering.

Acceptance criteria:

- Common callers can start from `Visual` and `VisualAssertions` without reading
  the lower-level `cv` package.
- New API docs explain input ownership and encoded-byte expectations.
- `./gradlew test` and `./gradlew build` pass.

Verification status:

- `./gradlew test` passed.
- `./gradlew build` passed.
- `git diff --check` passed.
- Appium visual integration test was not rerun in this phase because `adb`
  currently reports no connected devices.

## Phase 6: OCR Actions And Paddle Extension

Goal: support high-density UI automation flows where text recognition directly
drives visual actions.

Implemented in core:

- OCR text matching options:
  - exact match.
  - contains match.
  - regex match.
  - case-insensitive matching.
  - whitespace normalization.
  - confidence filtering.
  - ROI filtering.
- `Visual` OCR helpers for encoded screenshot bytes:
  - `recognizeText`.
  - `findText`.
  - `findAllText`.
- `UiVision` OCR actions:
  - `recognizeText`.
  - `findText`.
  - `findAllText`.
  - `waitForText`.
  - `clickText`.
  - `doubleClickText`.
  - `assertTextVisible`.

Planned optional extension:

- `kt-visual-ocr-paddle`. Done.
- `PaddleOcrEngine.multilingual13()`. Done.
- Built-in routing for:
  - Simplified Chinese.
  - English.
  - Korean.
  - Japanese.
  - German.
  - French.
  - Spanish.
  - Portuguese.
  - Russian.
  - Thai.
  - Vietnamese.
  - Turkish.
  - Indonesian.
- Resource manager for bundled dictionaries and model files. Done.
- Runtime adapter interface for DJL, ONNX Runtime, or native Paddle inference. Done.
- Cross-platform ONNX Runtime implementation for converted PaddleOCR det/rec models. Done.
- PP-OCRv6 `inference.yml` dictionary parsing for official ONNX model packages. Done.
- Process-based PaddleOCR CLI runtime. Done.
- Opt-in real-model integration test using official PP-OCRv6 medium ONNX models. Done.
- Optional jar packaging for official PaddleOCR ONNX model resources with
  `-PincludePaddleOcrModels=true`. Done.
- Automatic PaddleOCR model cache population from packaged resources or official
  remote URLs. Done.
- Language-group model routing for the 13-language profile:
  - PP-OCRv6 medium detector shared by all groups. Done.
  - PP-OCRv6 medium recognizer for Simplified Chinese, English, and Japanese. Done.
  - Highest official ONNX availability for other groups:
    - PP-OCRv5 mobile Korean recognizer. Done.
    - PP-OCRv5 mobile Latin recognizer. Done.
    - PP-OCRv5 mobile Cyrillic recognizer. Done.
    - PP-OCRv5 mobile Thai recognizer. Done.
- Online 13-language OCR validation against Apple Support UI screenshots. Done.

- Prepare 0.2.0 release artifacts:
  - normal OCR extension jar without models. Done.
  - OCR extension jar with embedded model resources. Done.

Acceptance criteria:

- Automation callers can click or wait by text without knowing OCR model paths.
- Paddle resources are isolated from the core artifact.
- Core remains usable without downloading OCR runtime or model resources.

## Phase 7: Multimodal OCR Extension

Goal: add an optional OCR engine for cloud or private multimodal models while
keeping the default core artifact dependency-light.

Implemented:

- `kt-visual-ocr-multimodal` optional extension module.
- `MultimodalOcrClient` abstraction for private VLM gateways.
- `MultimodalOcrEngine` implementation of the core `OcrEngine` interface.
- Structured OCR JSON parsing from top-level arrays, `texts`, `items`, or
  `results` objects.
- Normalized `bounds` coordinates and `bbox` / `box` corner arrays.
- ROI cropping with coordinate restoration to the original screenshot.
- Optional OCR fallback when the multimodal model returns no text or fails.
- `OpenAiCompatibleMultimodalOcrClient` backed by the OpenAI Java SDK and
  Responses API. Done.
- Configurable retry policy for client errors, malformed model responses, and
  opt-in empty-result retries. Done.
- Opt-in online multilingual validation against the 13 Apple Support UI
  screenshots used by Paddle OCR validation. Done.

Acceptance criteria:

- Core remains independent from cloud SDKs and API-specific dependencies.
- Teams can plug in private multimodal services with a small client adapter.
- `UiVision` OCR actions work unchanged with the multimodal engine.
- Online validation passes for the configured OpenAI-compatible high-accuracy
  model before the 0.3.0 release. Done.

Verification status:

- `./gradlew :kt-visual-ocr-multimodal:test --rerun-tasks` passed.
- `./gradlew build` passed.
- `git diff --check` passed.
- Online 13-language multimodal OCR validation passed with the OpenAI SDK,
  Responses API, non-streaming mode, and `reasoningEffort=high`.

## Phase 8: Practical Automation Hardening

Goal: improve stability, diagnosis, and cost control for real UI automation
projects without expanding the library into a driver framework or a general
purpose UI agent.

Assessment:

- The current 0.3.x capability set already covers the main recognition paths:
  template matching, visual comparison, color/layout/quality analysis, OCR
  actions, Paddle OCR, and multimodal OCR.
- New work should be justified by high-frequency automation failures, not by
  adding broad visual-AI features.
- The most valuable next features are those that make failures explainable,
  reduce repeated OCR cost, and make text-driven clicks safer.

Recommended for 0.3.x patch releases:

- Keep 0.3.x focused on stability and diagnostics only:
  - prompt fixes for multimodal OCR;
  - JSON parsing robustness;
  - retry and fallback edge cases;
  - documentation and test coverage for failure modes.
- Avoid changing core driver boundaries in patch releases.

Recommended for 0.4.0:

- Visual evidence chain for recognition and actions:
  - original screenshot;
  - ROI crop;
  - OCR or match boxes overlay;
  - raw OCR/model response where applicable;
  - parsed `OcrText` or match result;
  - final click point and selected target.
- OCR result cache:
  - key by image hash, ROI, engine identity, prompt/version, and relevant
    options;
  - keep cache short-lived and caller-controllable;
  - avoid repeated Paddle or multimodal calls for the same screenshot.
- Text-click disambiguation:
  - choose the nth match;
  - constrain by ROI;
  - choose nearest to a visual template or another text;
  - support above/below/left/right relative text constraints;
  - report why a candidate was selected or rejected.

Recommended for 0.5.0 or later:

- OCR fallback strategy chains:
  - Paddle first, multimodal only when confidence is low or no target text is
    found;
  - multimodal first for low-quality crops, with Paddle fallback;
  - per-language, per-ROI, or per-cost engine selection;
  - explicit fallback reason in diagnostics.
- Action preflight checks:
  - target remains stable immediately before click;
  - target is not visually occluded;
  - target remains inside the expected ROI;
  - click point is still valid after a short re-screenshot.
- Higher-level visual state recognition only when justified by real cases:
  - disabled/enabled;
  - loading/ready;
  - selected/unselected;
  - table/list row and cell targeting.

Explicit non-goals:

- Do not integrate Appium, Selenium, ADB, browser sessions, app launch, or
  test-flow orchestration into the core library.
- Do not inspect mobile XML hierarchy when testing or implementing visual
  recognition behavior.
- Do not build a generic multimodal UI agent that reads a page and decides what
  to do.
- Do not encourage multimodal OCR to guess labels, infer intent, translate, or
  complete uncertain text.
- Do not expand OCR languages or model families without a concrete project
  failure that requires it.
