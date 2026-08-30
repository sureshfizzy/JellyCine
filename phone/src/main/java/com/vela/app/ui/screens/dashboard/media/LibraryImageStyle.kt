package com.vela.app.ui.screens.dashboard.media

internal enum class LibraryImageStyle {
    POSTER,
    BACKDROP,
    BANNER;

    val imageType: String
        get() = when (this) {
            POSTER -> "Primary"
            BACKDROP -> "Backdrop"
            BANNER -> "Banner"
        }

    val fallbackImageType: String?
        get() = when (this) {
            POSTER -> null
            BACKDROP -> "Thumb"
            BANNER -> "Thumb"
        }

    val extraFallbackImageTypes: List<String>
        get() = when (this) {
            POSTER -> emptyList()
            BACKDROP -> listOf("Primary")
            BANNER -> listOf("Backdrop", "Primary")
        }

    val aspectRatio: Float
        get() = when (this) {
            POSTER -> 2f / 3f
            BACKDROP -> 16f / 9f
            BANNER -> 1000f / 185f
        }

    val cropImage: Boolean
        get() = true

    companion object {
        fun fromPersisted(value: String?): LibraryImageStyle {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: POSTER
        }
    }
}
