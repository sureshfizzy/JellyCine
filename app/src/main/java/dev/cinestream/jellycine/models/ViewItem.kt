package dev.cinestream.jellycine.models

import java.util.*

data class ViewItem(
    val id: UUID,
    val name: String?,
    val primaryImageUrl: String,
    val itemType: String? = null,
    val productionYear: Int? = null,
    val runTimeTicks: Long? = null,
    val playbackPositionTicks: Long? = null,
    val playedPercentage: Double? = null,
    val isFavorite: Boolean = false,
    val overview: String? = null,
    val backdropImageUrl: String? = null,
    val seriesId: UUID? = null,
    val seriesName: String? = null
)