package com.soluna.ktvisual.ocr.paddle

import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

/**
 * Resolves Paddle OCR resources to local filesystem paths.
 *
 * Paddle inference runtimes typically expect model directories and dictionary
 * files on disk. This manager owns the cache location and materializes
 * resources from packaged classpath files or the official remote model URLs in
 * [PaddleOcrModelSpec].
 */
/**
 * Resolves a model spec into local files usable by a runtime.
 */
fun interface PaddleOcrResourceResolver {
    fun resolve(spec: PaddleOcrModelSpec): ResolvedPaddleOcrModel
}

class PaddleOcrResourceManager(
    private val cacheDirectory: Path = defaultCacheDirectory(),
    private val classLoader: ClassLoader = PaddleOcrResourceManager::class.java.classLoader,
    private val downloadMissingResources: Boolean = true
) : PaddleOcrResourceResolver {

    /**
     * Resolves [spec] into local paths.
     *
     * If resources are not already cached, this method first tries packaged
     * classpath resources and then, when [downloadMissingResources] is true,
     * downloads the official remote resources declared by [spec]. Downloaded
     * files are cached under [cacheDirectory] and reused by later OCR calls.
     */
    override fun resolve(spec: PaddleOcrModelSpec): ResolvedPaddleOcrModel {
        val remote = spec.remoteResources
        val detector = ensureModelDirectory(
            resourcePath = spec.detectorResource,
            modelId = spec.id,
            name = "det",
            modelFileName = remote?.modelFileName ?: DEFAULT_MODEL_FILE_NAME,
            remoteUrl = remote?.detectorModelUrl
        )
        val recognizer = ensureModelDirectory(
            resourcePath = spec.recognizerResource,
            modelId = spec.id,
            name = "rec",
            modelFileName = remote?.modelFileName ?: DEFAULT_MODEL_FILE_NAME,
            remoteUrl = remote?.recognizerModelUrl
        )
        val dictionary = ensureFile(
            resourcePath = spec.dictionaryResource,
            target = targetForResource(spec.dictionaryResource),
            remoteUrl = remote?.dictionaryUrl,
            label = "dictionary"
        )
        return ResolvedPaddleOcrModel(spec, detector, recognizer, dictionary)
    }

    private fun ensureModelDirectory(
        resourcePath: String,
        modelId: String,
        name: String,
        modelFileName: String,
        remoteUrl: String?
    ): Path {
        val target = targetForResource(resourcePath)
        ensureFile(
            resourcePath = "$resourcePath/$modelFileName",
            target = target.resolve(modelFileName),
            remoteUrl = remoteUrl,
            label = "$name model"
        )
        return target
    }

    private fun ensureFile(resourcePath: String, target: Path, remoteUrl: String?, label: String): Path {
        if (target.exists() && Files.size(target) > 0) return target

        Files.createDirectories(target.parent)

        val resource = classLoader.getResourceAsStream(resourcePath)
        if (resource != null) {
            try {
                resource.use { input ->
                    Files.newOutputStream(target).use { output -> input.copyTo(output) }
                }
                return target
            } catch (error: IOException) {
                throw MissingPaddleOcrResourceException(
                    "Failed to materialize Paddle OCR resource '$resourcePath'.",
                    error
                )
            }
        }

        if (!downloadMissingResources || remoteUrl == null) {
            throw MissingPaddleOcrResourceException(
                "Missing Paddle OCR $label resource '$resourcePath'. " +
                    "Enable downloads, bundle the resource, or provide a custom PaddleOcrResourceResolver."
            )
        }

        return try {
            val temp = Files.createTempFile(target.parent, target.fileName.toString(), ".download")
            try {
                URI.create(remoteUrl).toURL().openStream().use { input ->
                    Files.newOutputStream(temp).use { output -> input.copyTo(output) }
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                target
            } catch (error: Throwable) {
                Files.deleteIfExists(temp)
                throw error
            }
        } catch (error: IOException) {
            throw MissingPaddleOcrResourceException(
                "Failed to download Paddle OCR $label from '$remoteUrl' to '$target'.",
                error
            )
        }
    }

    private fun targetForResource(resourcePath: String): Path {
        return cacheDirectory.resolve(resourcePath.removePrefix("models/paddleocr/"))
    }

    companion object {
        private const val DEFAULT_MODEL_FILE_NAME = "model.onnx"

        fun defaultCacheDirectory(): Path {
            return Path.of(System.getProperty("user.home"), ".kt-visual", "models", "paddleocr")
        }
    }
}

/**
 * Thrown when the Paddle OCR extension cannot find required packaged resources.
 */
class MissingPaddleOcrResourceException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
