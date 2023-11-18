package dev.cinestream.jellycine.models

import java.util.*

data class ViewItem(
    val id: UUID,
    val name: String?,
    val primaryImageUrl: String
)