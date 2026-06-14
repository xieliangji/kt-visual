package com.soluna.ktvisual.cv

import com.soluna.ktvisual.OpenCvRuntime
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.nio.file.Files
import java.nio.file.Path

class TemplateCache : AutoCloseable {

    private val entries = mutableMapOf<Path, Entry>()

    val size: Int
        @Synchronized get() = entries.size

    @Synchronized
    fun get(path: Path): Mat {
        OpenCvRuntime.ensureLoaded()

        val normalizedPath = path.toAbsolutePath().normalize()
        require(Files.exists(normalizedPath)) {
            "Template image not found: $path"
        }

        val metadata = TemplateMetadata(
            lastModifiedMillis = Files.getLastModifiedTime(normalizedPath).toMillis(),
            sizeBytes = Files.size(normalizedPath)
        )

        val current = entries[normalizedPath]
        if (current != null && current.metadata == metadata) {
            return current.template
        }

        val loaded = Imgcodecs.imread(
            normalizedPath.toString(),
            Imgcodecs.IMREAD_COLOR
        )

        require(!loaded.empty()) {
            "Failed to read template image: $path"
        }

        entries[normalizedPath] = Entry(metadata, loaded)
        current?.template?.release()

        return loaded
    }

    @Synchronized
    fun invalidate(path: Path) {
        val normalizedPath = path.toAbsolutePath().normalize()
        entries.remove(normalizedPath)?.template?.release()
    }

    @Synchronized
    fun clear() {
        entries.values.forEach { it.template.release() }
        entries.clear()
    }

    override fun close() {
        clear()
    }

    private data class TemplateMetadata(
        val lastModifiedMillis: Long,
        val sizeBytes: Long
    )

    private data class Entry(
        val metadata: TemplateMetadata,
        val template: Mat
    )
}
