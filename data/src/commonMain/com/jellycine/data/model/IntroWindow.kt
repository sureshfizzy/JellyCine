package com.jellycine.data.model

data class IntroWindow(
    val startMs: Long,
    val endMs: Long,
    val source: IntroWindowSource
)

enum class IntroWindowSource {
    SERVER_MARKER,
    INTRO_DB
}