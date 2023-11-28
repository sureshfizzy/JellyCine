package dev.cinestream.jellycine.models

import java.util.*

data class View(
    val id: UUID,
    val name: String?,
    val genre: List<List<String>?>,
    var items: List<ViewItem>? = null
)