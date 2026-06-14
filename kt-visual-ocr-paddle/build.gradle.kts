import java.net.URI

plugins {
    kotlin("jvm") version "2.4.0"
    `java-library`
    `maven-publish`
}

group = "com.soluna"
version = rootProject.version

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(project(":"))

    implementation("com.microsoft.onnxruntime:onnxruntime:1.22.0")
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val includePaddleOcrModels = providers.gradleProperty("includePaddleOcrModels")
    .map(String::toBoolean)
    .orElse(false)

val generatedPaddleOcrResources = layout.buildDirectory.dir("generated/paddle-ocr-resources")

val downloadPaddleOcrModels by tasks.registering {
    description = "Downloads official PaddleOCR ONNX resources for packaging into the OCR extension jar."
    group = "build setup"

    val outputDir = generatedPaddleOcrResources
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        delete(outputDir.get().file("models/paddleocr").asFile)

        val resources = listOf(
            "https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_det_onnx/resolve/main/inference.onnx" to
                outputDir.get().file("models/paddleocr/ppocrv6-det/det/inference.onnx").asFile,
            "https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_onnx/resolve/main/inference.onnx" to
                outputDir.get().file("models/paddleocr/ppocrv6-cjk-en/rec/inference.onnx").asFile,
            "https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_onnx/raw/main/inference.yml" to
                outputDir.get().file("models/paddleocr/ppocrv6-cjk-en/inference.yml").asFile,
            "https://huggingface.co/PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx" to
                outputDir.get().file("models/paddleocr/ppocrv5-korean/rec/inference.onnx").asFile,
            "https://huggingface.co/PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx/raw/main/inference.yml" to
                outputDir.get().file("models/paddleocr/ppocrv5-korean/inference.yml").asFile,
            "https://huggingface.co/PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx" to
                outputDir.get().file("models/paddleocr/ppocrv5-latin/rec/inference.onnx").asFile,
            "https://huggingface.co/PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx/raw/main/inference.yml" to
                outputDir.get().file("models/paddleocr/ppocrv5-latin/inference.yml").asFile,
            "https://huggingface.co/PaddlePaddle/cyrillic_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx" to
                outputDir.get().file("models/paddleocr/ppocrv5-cyrillic/rec/inference.onnx").asFile,
            "https://huggingface.co/PaddlePaddle/cyrillic_PP-OCRv5_mobile_rec_onnx/raw/main/inference.yml" to
                outputDir.get().file("models/paddleocr/ppocrv5-cyrillic/inference.yml").asFile,
            "https://huggingface.co/PaddlePaddle/th_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx" to
                outputDir.get().file("models/paddleocr/ppocrv5-thai/rec/inference.onnx").asFile,
            "https://huggingface.co/PaddlePaddle/th_PP-OCRv5_mobile_rec_onnx/raw/main/inference.yml" to
                outputDir.get().file("models/paddleocr/ppocrv5-thai/inference.yml").asFile
        )

        resources.forEach { (url, target) ->
            if (target.exists() && target.length() > 0) return@forEach

            target.parentFile.mkdirs()
            URI.create(url).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

tasks.processResources {
    inputs.property("includePaddleOcrModels", includePaddleOcrModels)

    doFirst {
        delete(destinationDir.resolve("models/paddleocr"))
    }

    if (includePaddleOcrModels.get()) {
        dependsOn(downloadPaddleOcrModels)
        from(generatedPaddleOcrResources)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "com.soluna"
            artifactId = "kt-visual-ocr-paddle"
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "localBuildRepo"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
