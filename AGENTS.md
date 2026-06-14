# AGENTS.md

This file is the operating guide for Codex agents continuing development in
this repository.

## Project Intent

`kt-visual` is a Kotlin/JVM computer-vision helper library for UI automation.
It is meant to be consumed by other automation projects, not to own the driver
session itself.

Keep this boundary clear:

- This library owns screenshot/image analysis, matching, OCR abstractions,
  visual assertions, and coordinate results.
- Host automation projects own Appium, Selenium, ADB, desktop app launch,
  browser sessions, test flow, clicking implementation, typing, and swiping.
- Do not couple the core artifact to Appium, Selenium, ADB, cloud SDKs, or
  heavy OCR runtimes.

The maintainer generally prefers direct Chinese progress updates. Keep updates
concrete: what was changed, what was tested, and what remains.

## Repository Layout

- `src/main/kotlin/com/soluna/ktvisual/api`
  Public facades and driver-independent interfaces. Prefer adding caller-facing
  APIs here before exposing lower-level internals.
- `src/main/kotlin/com/soluna/ktvisual/cv`
  OpenCV implementation details: template matching, diffing, color detection,
  quality/layout analysis, and conversion helpers.
- `src/main/kotlin/com/soluna/ktvisual/model`
  Stable data classes and options used by public APIs.
- `src/main/kotlin/com/soluna/ktvisual/driver`
  Lightweight built-in adapters only. Keep real automation framework adapters
  outside the core unless they are test-only.
- `src/main/kotlin/com/soluna/ktvisual/report`
  Debug and visual artifact output.
- `kt-visual-ocr-paddle`
  Optional Paddle OCR extension. This is allowed to depend on ONNX Runtime and
  model resource management.
- `kt-visual-ocr-multimodal`
  Optional multimodal OCR extension. This may depend on the official OpenAI SDK
  for OpenAI-compatible Responses API calls, while still exposing small client
  interfaces for private gateways.
- `README.md`, `ROADMAP.md`, `CHANGELOG.md`
  User-facing documentation. Update these with capability changes.

## Development Principles

- Keep the public API concentrated in a small number of discoverable files:
  `Visual`, `VisualAssertions`, `UiVision`, options/models, and extension
  engines.
- Public APIs should accept common image inputs where practical:
  `ByteArray` for encoded screenshots, `Path`/`File`, `BufferedImage`, and
  `Mat` when appropriate.
- Do not require callers to convert everything to `Mat`; this library should
  hide that boilerplate for normal automation usage.
- Add clear KDoc for APIs meant for external automation projects. Document
  coordinate systems, ROI behavior, confidence thresholds, and failure modes.
- Prefer ROI and stable visual anchors over overly high global confidence
  thresholds. High OCR confidence is useful for assertions, but can make
  automation brittle when fonts, screenshots, or languages vary.
- For text actions, make `UiVision` work with any `OcrEngine`. Paddle and
  multimodal engines are implementations, not special cases in core.
- For image matching, prefer real visual matching/template/common-region
  approaches. Do not inspect mobile XML hierarchy when testing computer vision
  behavior.
- For UI input, preserve realistic automation semantics. Double-click should
  be an actual double-click action where the adapter supports it, not just two
  unrelated delayed single clicks.
- Keep generated build outputs, downloaded models, and caches out of git.

## Build Environment

- Java toolchain: 21.
- Gradle wrapper: 9.3.0.
- Kotlin Gradle plugin: 2.4.0.
- Core OpenCV dependency: `org.openpnp:opencv:4.9.0-0`.
- Paddle extension ONNX Runtime dependency: `com.microsoft.onnxruntime:onnxruntime:1.22.0`.

Use the wrapper:

```bash
./gradlew test
./gradlew build
```

If Gradle needs network access or writes outside the sandbox, run it with the
approved non-sandbox/escalated path in Codex.

## Test Matrix

Always choose the smallest useful test first, then run broader checks before a
commit or release.

Core:

```bash
./gradlew test
./gradlew build
git diff --check
```

Paddle OCR unit tests:

```bash
./gradlew :kt-visual-ocr-paddle:test
```

Paddle real-model smoke test, opt-in because it downloads official models:

```bash
KT_VISUAL_RUN_REAL_OCR=true ./gradlew :kt-visual-ocr-paddle:test --tests com.soluna.ktvisual.ocr.paddle.PaddleOcrOnnxRuntimeTest
```

Paddle 13-language online validation, opt-in because it downloads screenshots
and uses real model resources:

```bash
KT_VISUAL_RUN_ONLINE_MULTILINGUAL_OCR=true ./gradlew :kt-visual-ocr-paddle:test --tests com.soluna.ktvisual.ocr.paddle.PaddleOcrOnlineMultilingualTest
```

Multimodal OCR online validation, opt-in because it calls a configured external
or private VLM gateway:

```bash
KT_VISUAL_RUN_ONLINE_MULTIMODAL_OCR=true \
KT_VISUAL_MULTIMODAL_BASE_URL=<openai-compatible-v1-base-url> \
KT_VISUAL_MULTIMODAL_API_KEY=<api-key> \
KT_VISUAL_MULTIMODAL_MODEL=<model> \
KT_VISUAL_MULTIMODAL_REASONING_EFFORT=high \
./gradlew :kt-visual-ocr-multimodal:test --tests com.soluna.ktvisual.ocr.multimodal.MultimodalOcrOnlineMultilingualTest
```

Android Appium visual integration, only when a real device and Appium server
are available:

```bash
./gradlew appiumTest -PappiumServerUrl=http://127.0.0.1:4723
```

Do not make online tests part of the default build.

## Paddle OCR Model Policy

The 13-language Paddle profile should use the highest currently available
official ONNX resources, while staying cross-platform and packageable in JVM
projects.

Current 0.2.x policy:

- Shared detector: PP-OCRv6 medium detector ONNX.
- Simplified Chinese, English, Japanese recognizer: PP-OCRv6 medium recognizer
  ONNX.
- Korean recognizer: official Korean PP-OCRv5 mobile ONNX.
- Latin group recognizer: official Latin PP-OCRv5 mobile ONNX.
- Cyrillic/Russian recognizer: official Cyrillic PP-OCRv5 mobile ONNX.
- Thai recognizer: official Thai PP-OCRv5 mobile ONNX.

Do not replace this with a weaker or older model unless the maintainer asks for
that tradeoff. If official server ONNX packages become available for these
language groups, verify them and update the catalog, tests, README, and
release notes.

Important implementation details:

- Packaged resources live under `models/paddleocr/...`.
- Runtime cache defaults to `~/.kt-visual/models/paddleocr/`.
- `PaddleOcrResourceManager` must support:
  packaged classpath resources;
  automatic official remote download/cache;
  caller-provided custom resolvers.
- `PaddleOcrOnnxRuntime` currently uses `inference.onnx`.
- Keep dictionary parsing robust for Unicode entries, including full-width
  spaces in Paddle `inference.yml`.

## Paddle OCR Packaging

Two OCR extension artifacts are expected for releases:

- Normal jar without model resources.
- With-models jar containing the official ONNX/YAML resources.

Build and verify the normal jar:

```bash
./gradlew :kt-visual-ocr-paddle:jar
jar tf kt-visual-ocr-paddle/build/libs/kt-visual-ocr-paddle-<version>.jar | rg 'models/paddleocr|inference'
```

The `jar tf | rg ...` command should return no output for the normal jar.

Build and verify the with-models jar:

```bash
./gradlew :kt-visual-ocr-paddle:jar -PincludePaddleOcrModels=true
jar tf kt-visual-ocr-paddle/build/libs/kt-visual-ocr-paddle-<version>.jar | rg 'models/paddleocr|inference'
jar tf kt-visual-ocr-paddle/build/libs/kt-visual-ocr-paddle-<version>.jar | rg 'ppocrv6-multilingual'
```

The with-models jar should contain only the current model matrix and must not
contain stale historical directories such as `ppocrv6-multilingual`.

When touching packaging logic, clean stale generated resources before copying
new resources. Past failures came from old model resources remaining in
`build/generated/paddle-ocr-resources` or `build/resources/main`.

## Multimodal OCR Policy

The multimodal extension is planned for 0.3.x-style work and must remain
optional.

Guidelines:

- Keep cloud-specific SDKs out of the core module. The optional multimodal
  extension may use the official OpenAI Java SDK for OpenAI-compatible
  Responses API calls.
- Expose `MultimodalOcrClient` for private gateways.
- Use the official OpenAI Java SDK for `OpenAiCompatibleMultimodalOcrClient`.
- Keep custom/private gateway support behind `MultimodalOcrClient` when the
  gateway does not expose an OpenAI-compatible Responses API.
- Prefer structured JSON output with normalized bounds.
- Keep multimodal OCR prompts conservative: extract only directly visible text;
  do not infer hidden labels, translate, summarize, normalize, or complete
  uncertain text from context.
- Keep retry behavior configurable through `MultimodalOcrOptions.retry`; it
  should cover transient client errors and malformed model responses without
  forcing retries on callers that need strict latency.
- Support fallback to another `OcrEngine`, such as Paddle OCR.
- Keep `UiVision` text actions unchanged; callers should be able to swap OCR
  engines without rewriting automation flows.
- Never log API keys or full secrets in tests, docs, or errors.

## Release Checklist

Before creating a release:

1. Ensure `README.md`, `CHANGELOG.md`, and `ROADMAP.md` describe the current
   behavior and version.
2. Change `build.gradle.kts` from `-SNAPSHOT` to the release version.
3. Run:

```bash
./gradlew build
git diff --check
```

4. Run opt-in real/online OCR tests when the release changes OCR behavior.
5. Generate release assets, normally under `build/release/`.
6. Verify normal and with-models jars as described above.
7. Commit with a message that explains user-visible capabilities.
8. Tag with `v<version>`.
9. Push `main` and the tag.
10. Create the GitHub Release and upload assets.

Example GitHub release flow:

```bash
git tag -a v0.3.1 -m "kt-visual 0.3.1"
git push origin main
git push origin v0.3.1
gh release create v0.3.1 build/release/*.jar --repo xieliangji/kt-visual --title "kt-visual 0.3.1" --notes "<summary>"
```

## Git And Workspace Rules

- Check `git status -sb` before edits.
- The worktree may contain user changes. Do not revert or overwrite changes you
  did not make.
- If user changes are unrelated, leave them alone.
- If user changes affect the task, work with them and mention the assumption.
- Do not run destructive commands such as `git reset --hard` or checkout files
  unless the maintainer explicitly asks.
- Prefer small, focused commits. For release work, a single coherent release
  commit is acceptable.

## Documentation Rules

- Update API docs/KDoc when changing external APIs.
- Update README examples when constructor signatures, artifact names, version
  numbers, or dependency coordinates change.
- Update ROADMAP statuses as phases move from planned to done.
- Update CHANGELOG for user-visible additions, fixes, and release notes.
- Keep examples practical for automation callers. Show screenshot bytes,
  `UiVision`, ROI, and engine configuration rather than internal `Mat`
  conversion unless the low-level API is the topic.

## Current Stable Baseline

The latest published stable release at the time this guide was written is
`0.3.1`:

- Core visual matching and analysis facade.
- OCR text actions in `Visual` and `UiVision`.
- Optional Paddle OCR extension with 13-language profile.
- Optional multimodal OCR extension backed by custom clients or the OpenAI Java
  SDK Responses API client.
- Conservative multimodal OCR prompts that avoid inferred, translated,
  summarized, or completed text.
- Release assets include the core jar, normal Paddle OCR jar, with-models
  Paddle OCR jar, and multimodal OCR jar.

Consult `ROADMAP.md` before starting the next task.
