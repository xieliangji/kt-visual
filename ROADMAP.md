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
