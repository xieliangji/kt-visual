# kt-visual

基于 OpenCV 的 Kotlin/JVM UI 自动化视觉识别&图片处理模块。

当前稳定版本：`0.3.0`

该模块用于在普通 Kotlin Gradle 自动化项目中，通过截图 + 模板图定位 UI 元素，并返回坐标、匹配区域和置信度。

## 适用场景

- 桌面 App UI 自动化
- 模拟器 UI 自动化
- 远程设备截图识别
- Appium / Selenium 项目中的图像辅助定位
- 游戏、客户端、嵌入式屏幕等无法稳定获取控件树的场景
- UI 元素缺少可用 selector 时的兜底定位
- 图片处理/比对

## 设计边界

`kt-visual` 只负责视觉识别。

它负责：

- OpenCV 初始化
- 截图转换为 `Mat`
- 模板图加载
- 模板匹配
- ROI 局部查找
- 多尺度匹配
- 等待元素出现
- 返回匹配坐标、区域、置信度

它不负责：

- 启动被测 App
- 管理自动化 Driver
- 强制绑定 Appium、Selenium 或 ADB
- 维护测试用例流程
- 替代所有 selector 定位能力

推荐架构：

```text
automation-project
  ├── driver layer
  │     ├── 截图
  │     ├── 点击
  │     ├── 输入
  │     └── 滑动
  │
  └── kt-visual
        ├── 图像识别
        ├── 元素等待
        ├── 坐标返回
        ├── 图片处理
        └── 匹配调试
```

## 环境要求

- Kotlin/JVM
- Java 21 或以上
- Gradle 9.3.0 或以上
- OpenCV Java bindings

默认依赖使用：

```kotlin
api("org.openpnp:opencv:4.9.0-0")
```

`org.openpnp:opencv` 会随 Maven 依赖提供 OpenCV Java bindings 和常用平台的 native libraries。模块内部会调用：

```kotlin
nu.pattern.OpenCV.loadLocally()
```

因此使用方通常不需要手工配置 `java.library.path`。

## 安装

### 方式一：发布到本地 Maven

在 `kt-visual` 项目中执行：

```bash
./gradlew publishToMavenLocal
```

在自动化项目中引入：

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.soluna:kt-visual:0.3.0")
}
```

### 方式二：从 GitHub 通过 JitPack 引入

当本仓库推送到 GitHub 并打上 `v0.3.0` tag 后，自动化项目可以通过 JitPack 直接引用 GitHub 版本：

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.xieliangji:kt-visual:v0.3.0")
}
```

这种方式适合团队内部先快速试用 GitHub 上的稳定 tag。正式团队产物仍建议发布到公司 Maven 仓库，避免构建依赖外部临时服务。

### 方式三：OCR 扩展模块

OCR 扩展独立提供，避免不用 OCR 的项目下载 Paddle 或云端多模态相关依赖。Paddle OCR 扩展从 `0.2.0` 开始提供，多模态 OCR 扩展从 `0.3.0` 开始提供：

```kotlin
dependencies {
    implementation("com.soluna:kt-visual:0.3.0")
    implementation("com.soluna:kt-visual-ocr-paddle:0.3.0")
    implementation("com.soluna:kt-visual-ocr-multimodal:0.3.0")
}
```

当前扩展模块已经提供：

- `PaddleOcrEngine.multilingual13()`
- 13 种语言枚举 `OcrLanguage`
- 语言到模型组的路由
- Paddle OCR 模型资源解析和 cache 目录管理
- `PaddleOcrRuntime` 运行时适配接口
- `PaddleOcrOnnxRuntime`，通过 ONNX Runtime 在 JVM 内执行 PaddleOCR det/rec 模型
- `PaddleOcrCliRuntime`，可调用官方 PaddleOCR CLI 或团队内部 wrapper
- `MultimodalOcrEngine`，通过云端或私有多模态模型执行结构化 OCR
- `MultimodalOcrClient`，用于接入团队自己的 VLM 网关
- `OpenAiCompatibleMultimodalOcrClient`，基于 OpenAI Java SDK 和 Responses API 接入 OpenAI 或兼容网关

推荐 runtime 是 `PaddleOcrOnnxRuntime`。当前模型选择原则是“官方可直接发布的 ONNX 资源里优先精度”：检测使用 PP-OCRv6 medium，中文/英文/日文识别使用 PP-OCRv6 medium；官方没有 server ONNX 的语言组使用对应 PP-OCRv5 mobile ONNX，即韩语、拉丁语系、俄语和泰语。正式发布 OCR 扩展包时，可以把模型资源直接打进 `kt-visual-ocr-paddle` jar：

```bash
./gradlew :kt-visual-ocr-paddle:publish -PincludePaddleOcrModels=true
```

这样调用方只需要依赖 `kt-visual-ocr-paddle`，不需要单独去 PaddleOCR 下载模型或字典。运行时 `PaddleOcrResourceManager` 会从 jar 内资源解压到 `~/.kt-visual/models/paddleocr/` 并复用本地 cache。

jar 内资源路径为：

```text
models/paddleocr/ppocrv6-det/det/inference.onnx
models/paddleocr/ppocrv6-cjk-en/rec/inference.onnx
models/paddleocr/ppocrv6-cjk-en/inference.yml
models/paddleocr/ppocrv5-korean/rec/inference.onnx
models/paddleocr/ppocrv5-korean/inference.yml
models/paddleocr/ppocrv5-latin/rec/inference.onnx
models/paddleocr/ppocrv5-latin/inference.yml
models/paddleocr/ppocrv5-cyrillic/rec/inference.onnx
models/paddleocr/ppocrv5-cyrillic/inference.yml
models/paddleocr/ppocrv5-thai/rec/inference.onnx
models/paddleocr/ppocrv5-thai/inference.yml
```

创建 runtime 不需要传模型路径：

```kotlin
val runtime = PaddleOcrOnnxRuntime()
```

真实模型集成测试默认关闭，避免普通构建联网。需要验证 ONNX runtime 对官方 PP-OCRv6 medium 资源的基础识别能力时运行：

```bash
KT_VISUAL_RUN_REAL_OCR=true ./gradlew :kt-visual-ocr-paddle:test --tests com.soluna.ktvisual.ocr.paddle.PaddleOcrOnnxRuntimeTest
```

需要验证 13 语言完整路由时运行在线验收：

```bash
KT_VISUAL_RUN_ONLINE_MULTILINGUAL_OCR=true ./gradlew :kt-visual-ocr-paddle:test --tests com.soluna.ktvisual.ocr.paddle.PaddleOcrOnlineMultilingualTest
```

测试会下载官方 PP-OCRv6 medium 和 PP-OCRv5 语言组 ONNX 资源到 `kt-visual-ocr-paddle/build/real-ocr-models-cache/`，并使用 Apple Support 的 13 语言 iOS 设置截图做在线验收。该测试允许轻微 OCR 字符误差，但必须识别到目标语言的关键文本。

如果发布包没有带模型资源，默认 `PaddleOcrResourceManager` 会在首次使用时自动下载官方模型到 cache。企业内网或完全离线环境建议发布带模型资源的 jar，或提供自定义 `PaddleOcrResourceResolver` 指向内部制品库。

CLI runtime 仍可作为兼容和调试路径。`PaddleOcrCliRuntime` 通过进程调用 PaddleOCR，并要求命令输出 JSONL：

```json
{"text":"Login","confidence":0.98,"bounds":{"x":10,"y":20,"width":80,"height":24}}
```

这样 core API 已稳定支持 OCR 动作，Paddle runtime 和模型资源也不会强耦合进主包。

### 方式四：发布到公司 Maven 仓库

在模块 `build.gradle.kts` 中配置：

```kotlin
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "com.example.automation"
            artifactId = "kt-visual"
            version = "0.3.0"
        }
    }

    repositories {
        maven {
            name = "companyRepo"
            url = uri("https://nexus.example.com/repository/maven-releases/")
            credentials {
                username = findProperty("repoUser") as String
                password = findProperty("repoPassword") as String
            }
        }
    }
}
```

发布：

```bash
./gradlew publish
```

自动化项目引入：

```kotlin
repositories {
    mavenCentral()
    maven("https://nexus.example.com/repository/maven-releases/")
}

dependencies {
    implementation("com.example.automation:kt-visual:0.3.0")
}
```

## 快速开始

### 1. 准备模板图

例如：

```text
assets/templates/login/login_button.png
```

模板图应尽量截取稳定区域，例如按钮主体、固定 icon、固定标题，不要包含时间、用户名、金额、动态文案。

### 2. 定义目标元素

```kotlin
import com.soluna.ktvisual.model.MatchOptions
import com.soluna.ktvisual.model.UiTarget
import java.nio.file.Paths

object LoginPage {

    val loginButton = UiTarget(
        name = "login.loginButton",
        imagePath = Paths.get("assets/templates/login/login_button.png"),
        options = MatchOptions(
            threshold = 0.90,
            scales = listOf(0.9, 1.0, 1.1),
            grayscale = true
        )
    )
}
```

### 3. 创建 `ScreenSource`

`ScreenSource` 负责提供当前屏幕截图。

```kotlin
import com.soluna.ktvisual.api.ScreenSource
import java.awt.image.BufferedImage

class MyScreenSource : ScreenSource {
    override fun capture(): BufferedImage {
        // 从你的自动化框架、设备 SDK、ADB、Appium 或 Robot 获取截图
        TODO("return current screenshot")
    }
}
```

### 4. 创建 `UiInput`

`UiInput` 负责执行点击、输入等动作。

```kotlin
import com.soluna.ktvisual.api.UiInput

class MyUiInput : UiInput {
    override fun click(x: Int, y: Int) {
        // 调用你的自动化框架点击能力
    }

    override fun doubleClick(x: Int, y: Int) {
        // 调用你的自动化框架原生双击能力
    }

    override fun type(text: String) {
        // 调用你的自动化框架输入能力
    }
}
```

### 5. 使用 `UiVision`

```kotlin
import com.soluna.ktvisual.api.UiVision
import java.time.Duration

fun main() {
    val vision = UiVision(
        screenSource = MyScreenSource(),
        input = MyUiInput()
    )

    val result = vision.waitFor(
        target = LoginPage.loginButton,
        timeout = Duration.ofSeconds(8)
    )

    println("found: ${result.targetName}, center=${result.center}, score=${result.score}")

    vision.click(LoginPage.loginButton)
}
```

## 桌面 Robot 示例

如果项目是桌面 UI 自动化，可以使用模块内置的 Robot 适配器。

```kotlin
import com.soluna.ktvisual.api.UiVision
import com.soluna.ktvisual.driver.RobotScreenSource
import com.soluna.ktvisual.driver.RobotUiInput
import com.soluna.ktvisual.model.MatchOptions
import com.soluna.ktvisual.model.Region
import com.soluna.ktvisual.model.UiTarget
import java.nio.file.Paths
import java.time.Duration

fun main() {
    val vision = UiVision(
        screenSource = RobotScreenSource(),
        input = RobotUiInput()
    )

    val confirmButton = UiTarget(
        name = "dialog.confirmButton",
        imagePath = Paths.get("assets/templates/common/confirm_button.png"),
        options = MatchOptions(
            threshold = 0.92,
            scales = listOf(1.0),
            roi = Region(
                x = 500,
                y = 400,
                width = 600,
                height = 400
            )
        )
    )

    val matched = vision.click(
        target = confirmButton,
        timeout = Duration.ofSeconds(5)
    )

    println("clicked ${matched.targetName} at ${matched.center}")
}
```

注意：`java.awt.Robot` 不适用于 headless 环境。CI 环境中需要真实桌面、虚拟桌面或改用自动化框架自身截图与点击能力。

## Appium / Selenium / ADB 使用方式

`kt-visual` 不直接依赖 Appium、Selenium 或 ADB。自动化项目自己实现适配器即可。

### Appium / Selenium 截图适配示例

```kotlin
import com.soluna.ktvisual.api.ScreenSource
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class WebDriverScreenSource(
    private val driver: WebDriver
) : ScreenSource {

    override fun capture(): BufferedImage {
        val bytes = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
        return ImageIO.read(ByteArrayInputStream(bytes))
    }
}
```

### 点击适配示例

```kotlin
import com.soluna.ktvisual.api.UiInput

class DriverUiInput(
    private val clickAt: (x: Int, y: Int) -> Unit,
    private val typeText: (text: String) -> Unit
) : UiInput {

    override fun click(x: Int, y: Int) {
        clickAt(x, y)
    }

    override fun type(text: String) {
        typeText(text)
    }
}
```

这样可以把点击实现留给外部项目：

```kotlin
val vision = UiVision(
    screenSource = WebDriverScreenSource(driver),
    input = DriverUiInput(
        clickAt = { x, y -> /* 执行 driver 坐标点击 */ },
        typeText = { text -> /* 执行输入 */ }
    )
)
```

## 核心 API

### `ScreenSource`

```kotlin
interface ScreenSource {
    fun capture(): BufferedImage
}
```

截图来源抽象。由外部自动化项目实现。

### `UiInput`

```kotlin
interface UiInput {
    fun click(x: Int, y: Int)
    fun doubleClick(x: Int, y: Int)
    fun type(text: String)
}
```

输入动作抽象。由外部自动化项目实现。`doubleClick` 必须使用具体 driver 的原生双击能力；模块不会用两次 `click` 伪造双击，因为很多自动化框架会把它们拆成两个独立点击。

### `UiVision`

```kotlin
class UiVision {
    fun find(target: UiTarget): MatchResult?
    fun findAll(target: UiTarget): List<MatchResult>
    fun waitFor(target: UiTarget, timeout: Duration): MatchResult
    fun exists(target: UiTarget, timeout: Duration): Boolean
    fun waitGone(target: UiTarget, timeout: Duration): Boolean
    fun assertVisible(target: UiTarget, timeout: Duration): MatchResult
    fun assertNotVisible(target: UiTarget, timeout: Duration)
    fun waitStable(target: UiTarget, timeout: Duration): MatchResult
    fun doubleClick(
        target: UiTarget,
        timeout: Duration,
        offsetX: Int = 0,
        offsetY: Int = 0
    ): MatchResult
    fun click(
        target: UiTarget,
        timeout: Duration,
        offsetX: Int = 0,
        offsetY: Int = 0
    ): MatchResult
}
```

典型用法：

```kotlin
vision.find(LoginPage.loginButton)
vision.findAll(CommonPage.toastMessage)
vision.waitFor(LoginPage.loginButton)
vision.exists(LoginPage.loginButton)
vision.waitGone(CommonPage.loadingSpinner)
vision.assertNotVisible(CommonPage.errorDialog)
vision.waitStable(LoginPage.loginButton)
vision.click(LoginPage.loginButton)
vision.click(LoginPage.loginButton, offsetX = 8, offsetY = 0)
```

### `UiTarget`

```kotlin
data class UiTarget(
    val name: String,
    val imagePath: Path,
    val options: MatchOptions = MatchOptions(),
    val alternateImagePaths: List<Path> = emptyList()
)
```

表示一个可识别 UI 目标。`alternateImagePaths` 用于配置同一目标的备用模板，例如深浅色主题、hover / disabled 状态或不同 DPI 截图。

### `MatchOptions`

```kotlin
data class MatchOptions(
    val threshold: Double = 0.88,
    val scales: List<Double> = listOf(1.0),
    val method: Int = Imgproc.TM_CCOEFF_NORMED,
    val grayscale: Boolean = true,
    val edgeDetection: Boolean = false,
    val roi: Region? = null,
    val debug: Boolean = false,
    val debugDirectory: Path = Paths.get("build", "kt-visual-debug"),
    val maxMatches: Int = 20,
    val overlapThreshold: Double = 0.3
)
```

字段说明：

| 字段 | 说明 |
|---|---|
| `threshold` | 匹配阈值，越高越严格 |
| `scales` | 多尺度匹配比例，用于适配 DPI、缩放、分辨率差异 |
| `method` | OpenCV 模板匹配算法 |
| `grayscale` | 是否转灰度后匹配 |
| `edgeDetection` | 是否使用 Canny 边缘图进行匹配 |
| `roi` | 限制查找区域，提高速度并减少误识别 |
| `debug` | 是否启用调试输出 |
| `debugDirectory` | 调试图输出目录 |
| `maxMatches` | `findAll` 最多返回的匹配数量 |
| `overlapThreshold` | `findAll` 去重时允许的最大重叠比例 |

### `MatchResult`

```kotlin
data class MatchResult(
    val targetName: String,
    val bounds: Region,
    val score: Double,
    val scale: Double,
    val elapsedMillis: Long = 0
) {
    val center: Point2
}
```

返回内容：

| 字段 | 说明 |
|---|---|
| `targetName` | 目标名称 |
| `bounds` | 匹配到的矩形区域 |
| `center` | 匹配区域中心点，通常用于点击 |
| `score` | 匹配置信度 |
| `scale` | 命中的模板缩放比例 |
| `elapsedMillis` | 本次匹配耗时，单位毫秒 |

### 辅助工具

`Region` 提供相对定位方法：

```kotlin
val buttonArea = title.bounds.below(height = 80, gap = 12)
val rightPanel = title.bounds.rightOf(width = 300, gap = 16)
```

`MatchResults` 用于稳定处理 `findAll` 返回的重复元素：

```kotlin
val matches = vision.findAll(ProductPage.itemIcon)
val firstRowIcon = MatchResults.topToBottom(matches).first()
val rightMostIcon = MatchResults.leftToRight(matches).last()
val toolbarMatches = MatchResults.centersInside(matches, Region(0, 0, 1080, 240))
```

对外 API 入口建议优先看两个文件：

- `Visual`：视觉识别、对比、颜色、质量、主题、文本块、重复区域、稳定等待。
- `VisualAssertions`：测试框架无关的视觉断言和布局断言。

`cv` 包里的类仍然是 public，适合需要深度扩展或复用底层实现的项目；常规自动化项目建议优先调用 `Visual`。

`VisualAssertions` 提供测试框架无关的视觉断言：

```kotlin
val match = VisualAssertions.assertVisible("login.button", vision.find(LoginPage.loginButton))
VisualAssertions.assertScoreAtLeast(match, 0.92)
VisualAssertions.assertCenterInside(match, LoginPage.safeClickArea)
```

图像差异断言。调用方不需要先把图片转成 `Mat`，常见的 `Path` 和截图字节都可以直接传入：

```kotlin
// 严格同尺寸比对
val diff = Visual.compare(
    expected = Paths.get("baseline/home.png"),
    actual = Paths.get("current/home.png"),
    pixelThreshold = 5.0,
    maxDifferenceRatio = 0.01
)
VisualAssertions.assertImagesMatch(diff)

// Appium、Selenium、ADB 或自研截图接口返回 PNG/JPEG bytes 时可以直接传 ByteArray
val byteDiff = Visual.compare(
    expected = baselineBytes,
    actual = currentBytes,
    pixelThreshold = 5.0,
    maxDifferenceRatio = 0.01
)

// 两张图视觉内容相同但尺寸不同：先缩放到同一尺寸再比对
val resizedDiff = Visual.compareResized(
    expected = baselineBytes,
    actual = currentBytes,
    pixelThreshold = 8.0,
    maxDifferenceRatio = 0.02
)

// 两张图只有局部共同内容：先找共同区域，再比较共同区域
val commonRegion = Visual.findCommonRegion(
    source = baselineBytes,
    target = currentBytes,
    threshold = 0.90,
    scales = listOf(0.75, 1.0, 1.25, 1.5)
)

if (commonRegion != null) {
    val commonDiff = Visual.compareCommonRegion(
        source = baselineBytes,
        target = currentBytes,
        commonRegion = commonRegion,
        pixelThreshold = 8.0,
        maxDifferenceRatio = 0.05
    )
}
```

变化区域分析：

```kotlin
val changed = Visual.findChangedRegions(
    expected = baselineBytes,
    actual = currentBytes,
    pixelThreshold = 8.0,
    minRegionArea = 24,
    mergeGap = 3
)

changed.regions.forEach { region ->
    println("changed: $region")
}
```

颜色检测：

```kotlin
val hasGreen = Visual.containsGreen(
    image = screenshotBytes,
    minRatio = 0.05,
    roi = Region(0, 0, 200, 200)
)
```

`containsGreen`、`containsRed`、`containsBlue` 等方法使用 HSV 范围匹配，适合“绿色按钮”“红色警告”这类语义颜色判断。同一颜色名会覆盖一段色相、饱和度和亮度范围，而不是单个 RGB 值。

需要具体占比时，可以使用命名颜色检测：

```kotlin
val greenRatio = Visual.detectColor(
    image = screenshotBytes,
    color = NamedColor.GREEN,
    roi = Region(0, 0, 200, 200)
).ratio
```

需要精确色值时，可以使用 RGB + tolerance：

```kotlin
val redRatio = Visual.detectColor(
    image = screenshotBytes,
    color = RgbColor(red = 255, green = 0, blue = 0),
    tolerance = 8,
    roi = Region(0, 0, 200, 200)
).ratio
```

模板定位也支持截图字节和模板字节：

```kotlin
val back = Visual.findTemplate(
    screen = screenshotBytes,
    template = backIconBytes,
    targetName = "nav.back",
    options = MatchOptions(threshold = 0.88, scales = listOf(0.85, 1.0, 1.15))
)
```

截图质量、主题、文本块和重复区域：

```kotlin
val quality = Visual.analyzeQuality(screenshotBytes)
VisualAssertions.assertUsableScreenshot(quality)

val theme = Visual.detectTheme(screenshotBytes)
val textBlocks = Visual.findTextBlocks(screenshotBytes)
val rows = Visual.findRepeatedRows(screenshotBytes)
```

页面稳定等待：

```kotlin
val stable = Visual.waitStable(
    screenSource = appScreenSource,
    timeout = Duration.ofSeconds(5),
    samples = 3,
    roi = Region(0, 120, 1080, 1800)
)

check(stable.stable)
```

布局断言：

```kotlin
VisualAssertions.assertInside(dialog.bounds, screenSafeArea, "dialog")
VisualAssertions.assertNoOverlap(submitButton.bounds, keyboardRegion, "submit button")
VisualAssertions.assertAlignedCenterY(icon.bounds, title.bounds, tolerancePx = 2)
```

视觉报告输出：

```kotlin
val writer = VisualReportWriter(Paths.get("build/visual-report"))
writer.saveScreenshot("login-page", screenshotBytes)
writer.saveMatches("login-button", screenshotBytes, listOf(match))
writer.saveDiffHeatmap("login-diff", baselineBytes, currentBytes)
```

这些方法会输出 PNG 文件，适合挂到 JUnit、Allure、公司自研报告或 CI artifact 中。

OCR 与其他定位器通过接口扩展：

```kotlin
interface OcrEngine {
    fun recognize(image: BufferedImage, roi: Region? = null): List<OcrText>
}

interface VisualLocator {
    fun find(screen: Mat, target: UiTarget): MatchResult?
    fun findAll(screen: Mat, target: UiTarget): List<MatchResult>
}
```

OCR 可以和视觉动作联动。高密度自动化场景里，推荐把 OCR 引擎配置到 `UiVision`，然后按文本查找、等待和点击：

```kotlin
val ocr: OcrEngine = createProjectOcrEngine()

val vision = UiVision(
    screenSource = appScreenSource,
    input = appInput,
    ocrEngine = ocr
)

vision.waitForText("登录")
vision.clickText("登录")
vision.clickText(
    query = "继续",
    options = OcrTextMatchOptions(
        mode = OcrTextMatchMode.EXACT,
        minConfidence = 0.90,
        roi = Region(0, 120, 1080, 1600)
    )
)
```

也可以只对单张截图做 OCR 文本查找：

```kotlin
val loginText = Visual.findText(
    image = screenshotBytes,
    engine = ocr,
    query = "登录"
)
```

`clickText` 点击 OCR 文本框中心点。需要点击文本所在行、右侧按钮或列表 item 时，先通过 `findText` 拿到 `bounds`，再用 `Region` 的相对定位方法扩展目标区域。

Paddle OCR 的 13 语言二次封装位于独立扩展模块：

```kotlin
val ocr = PaddleOcrEngine.multilingual13(
    runtime = PaddleOcrOnnxRuntime()
)
```

如果暂时使用官方 PaddleOCR CLI 或内部脚本，可以替换 runtime：

```kotlin
val ocr = PaddleOcrEngine.multilingual13(
    runtime = PaddleOcrCliRuntime(command = listOf("/path/to/paddleocr-jsonl"))
)
```

默认覆盖语言：

```kotlin
OcrLanguage.SIMPLIFIED_CHINESE
OcrLanguage.ENGLISH
OcrLanguage.KOREAN
OcrLanguage.JAPANESE
OcrLanguage.GERMAN
OcrLanguage.FRENCH
OcrLanguage.SPANISH
OcrLanguage.PORTUGUESE
OcrLanguage.RUSSIAN
OcrLanguage.THAI
OcrLanguage.VIETNAMESE
OcrLanguage.TURKISH
OcrLanguage.INDONESIAN
```

扩展模块会按语言自动路由到当前最高可用的官方 ONNX 模型组：PP-OCRv6 medium 用于检测和中英日识别，PP-OCRv5 mobile 用于韩语、拉丁语系、俄语和泰语识别。模型文件和字典通过 `PaddleOcrResourceManager` 解析到 `~/.kt-visual/models/paddleocr/`。

多模态 OCR 适合处理低清截图、复杂 UI 文案、传统 OCR 置信度较弱的场景。它仍然实现 core 的 `OcrEngine` 接口，因此可以直接配置给 `UiVision`：

```kotlin
val cloudClient = OpenAiCompatibleMultimodalOcrClient.fromConfig(
    OpenAiCompatibleMultimodalOcrConfig(
        baseUrl = URI.create(System.getenv("KT_VISUAL_MULTIMODAL_BASE_URL")),
        apiKey = System.getenv("KT_VISUAL_MULTIMODAL_API_KEY"),
        model = System.getenv("KT_VISUAL_MULTIMODAL_MODEL"),
        reasoningEffort = "high",
        stream = false
    ),
    onStreamEvent = { event ->
        // 仅 stream=true 时触发：记录 reasoning/content chunk。
    }
)

val localFallback = PaddleOcrEngine.multilingual13(
    runtime = PaddleOcrOnnxRuntime()
)

val ocr = MultimodalOcrEngine(
    client = cloudClient,
    fallback = localFallback,
    options = MultimodalOcrOptions(
        minConfidence = 0.80,
        requireConfidence = false,
        retry = MultimodalOcrRetryOptions(
            maxAttempts = 3,
            initialDelay = Duration.ofMillis(300),
            maxDelay = Duration.ofSeconds(2),
            retryOnEmptyResult = true
        )
    )
)
```

如果企业内网已经有自己的多模态服务，只需要实现 `MultimodalOcrClient` 并返回 JSON 文本。默认 prompt 要求模型返回：

```json
{
  "texts": [
    {
      "text": "Login",
      "confidence": 0.98,
      "bounds": {"x": 0.10, "y": 0.20, "width": 0.30, "height": 0.05}
    }
  ]
}
```

`bounds` 推荐使用相对当前图片的归一化坐标；engine 会在传入 ROI 时自动恢复到整张截图坐标。也兼容 `bbox` / `box` 角点数组，例如 `[x1, y1, x2, y2]`。

`MultimodalOcrOptions.retry` 用于处理实际自动化中常见的不稳定情况：网络抖动、网关临时错误、SDK 空响应、模型没有按 JSON schema 返回。默认 `maxAttempts=1`，不会改变调用耗时；需要增强稳定性时再显式开启多次尝试。空 OCR 结果可能是合法结果，所以只有 `retryOnEmptyResult=true` 时才会重试空结果。`minConfidence` 只过滤带置信度的条目；如果必须拒绝没有置信度的模型输出，设置 `requireConfidence=true`。

`baseUrl`、`apiKey`、`model` 都由调用方从环境变量、配置中心或测试参数传入。库不会内置任何服务地址、密钥或模型名。`baseUrl` 传 `/v1` 级别地址即可，client 会通过 OpenAI Java SDK 请求 Responses API 的 `/responses` endpoint。

真实多模态模型验收测试默认关闭。需要按 Paddle OCR 同一批 Apple Support 13 语言截图验证时运行：

```bash
KT_VISUAL_RUN_ONLINE_MULTIMODAL_OCR=true \
KT_VISUAL_MULTIMODAL_BASE_URL="https://your-vlm-gateway.example.com/v1" \
KT_VISUAL_MULTIMODAL_API_KEY="..." \
KT_VISUAL_MULTIMODAL_MODEL="your-vision-model" \
KT_VISUAL_MULTIMODAL_REASONING_EFFORT="high" \
./gradlew :kt-visual-ocr-multimodal:test --tests com.soluna.ktvisual.ocr.multimodal.MultimodalOcrOnlineMultilingualTest
```

该测试默认使用非流式 SDK 调用。需要排查长请求是否持续输出时，可额外设置 `KT_VISUAL_MULTIMODAL_STREAM=true`，测试会统计 `reasoning` / `content` chunk。

## 模板图片规范

推荐目录：

```text
assets/templates/
├── common/
│   ├── close.png
│   ├── confirm.png
│   └── cancel.png
├── login/
│   ├── username_field.png
│   ├── password_field.png
│   └── login_button.png
└── payment/
    ├── pay_button.png
    └── success_icon.png
```

推荐命名：

```text
页面_元素_状态.png
```

示例：

```text
login_button_normal.png
login_button_disabled.png
payment_success_icon.png
common_dialog_confirm_button.png
```

模板截取建议：

- 截稳定区域，不截动态内容。
- 避免包含时间、金额、用户名、头像、角标等动态元素。
- 优先截 icon、固定按钮、固定标题、固定图形。
- 模板不要太大，区域越大越容易受 UI 变化影响。
- 对深色主题和浅色主题分别维护模板。
- 对不同 DPI 或缩放比例，优先配置 `scales`。
- 对相似元素，必须配置 `roi`。
- 对高风险操作，点击前增加页面状态确认。

## ROI 使用建议

ROI 用于限制搜索区域：

```kotlin
UiTarget(
    name = "payment.payButton",
    imagePath = Paths.get("assets/templates/payment/pay_button.png"),
    options = MatchOptions(
        threshold = 0.93,
        roi = Region(
            x = 700,
            y = 900,
            width = 500,
            height = 300
        )
    )
)
```

建议：

- 全屏查找只用于少量唯一元素。
- 页面中存在相似按钮时必须使用 ROI。
- ROI 应覆盖元素可能出现的区域，但不要过大。
- ROI 坐标基于截图左上角，单位为像素。

## 多尺度匹配建议

当测试机器分辨率、DPI、系统缩放比例不同，可以配置多尺度：

```kotlin
MatchOptions(
    threshold = 0.88,
    scales = listOf(0.8, 0.9, 1.0, 1.1, 1.25)
)
```

建议：

- 默认先用 `listOf(1.0)`。
- 环境缩放不一致时再增加 scales。
- scales 越多，耗时越高。
- 多尺度匹配应尽量配合 ROI 使用。

## 阈值建议

| 场景 | 推荐阈值 |
|---|---:|
| 普通图标 | `0.85` - `0.90` |
| 普通按钮 | `0.88` - `0.92` |
| 关键按钮 | `0.92` - `0.96` |
| 删除、支付、提交类操作 | `>= 0.94`，且需要二次确认 |

不建议把阈值调得过低。低阈值会明显增加误点击风险。

## 高风险操作建议

不要只靠单个模板匹配结果直接执行删除、支付、提交等动作。

推荐流程：

```kotlin
vision.assertVisible(PaymentPage.title)
vision.assertVisible(PaymentPage.amountLabel)
vision.click(PaymentPage.payButton)
vision.assertVisible(PaymentPage.confirmDialogTitle)
vision.click(PaymentPage.confirmButton)
```

即：

1. 先确认页面。
2. 再确认上下文元素。
3. 最后点击目标元素。
4. 点击后验证结果。

## 调试建议

当识别不稳定时，优先检查：

- 当前截图是否正确。
- 模板是否截到了动态区域。
- 模板和运行环境是否存在 DPI 差异。
- 是否需要开启灰度匹配。
- 是否需要增加 `scales`。
- 是否需要缩小 ROI。
- 阈值是否过高或过低。
- 页面是否存在多个相似元素。

启用 `MatchOptions(debug = true)` 后，模块会输出调试图：

```text
build/kt-visual-debug/
├── 20260614_153000_login_button_matched_score_0.934.png
└── 20260614_153010_login_button_failed.png
```

调试图建议绘制：

- 命中矩形
- 中心点
- target name
- score
- scale
- 匹配耗时
- ROI 边界
- 失败截图

## 推荐测试结构

```text
src/test/
├── kotlin/
│   └── com/example/uivision/
│       ├── TemplateLocatorTest.kt
│       └── UiVisionTest.kt
└── resources/
    ├── screenshots/
    │   └── login_screen.png
    └── templates/
        └── login_button.png
```

示例测试：

```kotlin
@Test
fun `should find login button from screenshot`() {
    val screen = ImageIO.read(Paths.get("src/test/resources/screenshots/login_screen.png").toFile())

    val source = object : ScreenSource {
        override fun capture(): BufferedImage = screen
    }

    val vision = UiVision(screenSource = source)

    val result = vision.find(
        UiTarget(
            name = "login.loginButton",
            imagePath = Paths.get("src/test/resources/templates/login_button.png"),
            options = MatchOptions(threshold = 0.90)
        )
    )

    assertNotNull(result)
    assertTrue(result.score >= 0.90)
}
```

## 常见问题

### 能不能用于 Android App 自动化？

可以，但不是把这个模块集成进 Android App。推荐方式是在外部 Kotlin/JVM 自动化项目里运行，通过 Appium、ADB 或其他工具获取截图，再交给 `kt-visual` 做图像识别。

### 为什么不直接依赖 Appium？

为了保持模块独立。不同项目可能使用 Appium、ADB、Selenium、Robot 或私有设备 SDK。`kt-visual` 只暴露 `ScreenSource` 和 `UiInput`，由使用方适配。

### 为什么识别到了错误位置？

常见原因：

- 模板太小或特征不足。
- 页面存在多个相似元素。
- 没有设置 ROI。
- 阈值过低。
- 截图和模板 DPI 不一致。
- 模板包含动态区域。

优先处理顺序：

1. 缩小 ROI。
2. 提高 threshold。
3. 换更稳定的模板区域。
4. 增加上下文元素确认。
5. 增加 debug 图排查。

### 为什么找不到元素？

常见原因：

- 模板和实际 UI 缩放比例不同。
- 主题颜色不同。
- 元素处于 disabled / hover / pressed 状态。
- 截图源不是当前页面。
- threshold 过高。
- ROI 没有覆盖目标区域。

优先处理顺序：

1. 保存当前截图人工比对。
2. 检查 ROI。
3. 降低 threshold 试验。
4. 增加 scales。
5. 为不同主题或状态增加模板。

### CI 环境能用吗？

可以，但要看截图和点击来源。

- 使用 Appium、Selenium、ADB 等外部 Driver 时，通常可以在 CI 使用。
- 使用 `java.awt.Robot` 时，CI 必须提供可用图形环境。
- headless 环境下不能直接用 Robot 截桌面。

### 是否支持 OCR？

当前设计的 MVP 不内置 OCR。建议先把 OCR 做成独立扩展层，例如：

```text
kt-visual-ocr
```

并通过接口接入，不要把 OCR 强耦合进模板匹配核心。

## 版本规划

### 0.1.x

- OpenCV 初始化
- `BufferedImage` 转 `Mat`
- 单模板匹配
- ROI
- 多尺度匹配
- `waitFor` / `exists` / `click`
- Robot 截图与点击适配

### 0.2.x

- `findAll`
- 多模板 target
- 模板缓存
- 调试图输出
- 失败截图保存
- 匹配耗时统计

### 0.3.x

- 相对定位
- 图像差异断言
- 颜色检测
- 边缘匹配
- OCR 扩展接口
- 定位器扩展接口

### 0.4.x+

- ORB/SIFT 特征匹配
- 并行模板搜索
- 模板库管理
- 远程截图源
- 测试报告集成

## 开发命令

```bash
# 编译
./gradlew build

# 运行测试
./gradlew test

# 运行 Android Appium 真机集成测试
# 需要本机 Appium server 已启动，且已连接 Android 设备
./gradlew appiumTest

# 指定 Appium server 或设备
./gradlew appiumTest \
  -PappiumServerUrl=http://127.0.0.1:4723 \
  -PappiumUdid=your-device-udid

# 发布到本地 Maven
./gradlew publishToMavenLocal

# 发布到配置的 Maven 仓库
./gradlew publish
```

## 最小使用示例

```kotlin
val vision = UiVision(
    screenSource = MyScreenSource(),
    input = MyUiInput()
)

val loginButton = UiTarget(
    name = "login.loginButton",
    imagePath = Paths.get("assets/templates/login/login_button.png"),
    options = MatchOptions(
        threshold = 0.90,
        scales = listOf(0.9, 1.0, 1.1)
    )
)

vision.click(loginButton, timeout = Duration.ofSeconds(8))
```

## License

```text
MIT / Apache-2.0 / Proprietary
```
