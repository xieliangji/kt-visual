# Changelog

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
