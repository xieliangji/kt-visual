package com.soluna.ktvisual.model

import java.nio.file.Path

/**
 * Describes a visual UI element that can be found by template matching.
 *
 * [imagePath] is the primary template. [alternateImagePaths] can hold templates
 * for dark mode, disabled/selected states, localization, or DPI variants. All
 * templates are searched with the same [options].
 */
data class UiTarget(
    val name: String,
    val imagePath: Path,
    val options: MatchOptions = MatchOptions(),
    val alternateImagePaths: List<Path> = emptyList()
) {
    init {
        require(alternateImagePaths.none { it == imagePath }) {
            "alternateImagePaths must not contain imagePath."
        }
    }

    val imagePaths: List<Path>
        get() = listOf(imagePath) + alternateImagePaths
}
