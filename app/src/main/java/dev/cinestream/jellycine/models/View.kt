package dev.cinestream.jellycine.models

import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.*

data class View(
    val id: UUID,
    val name: String?,
    var items: List<ViewItem>? = null
)