package com.jellycine.app.ui.screens.detail

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import kotlinx.coroutines.flow.first

internal suspend fun resolveEpisodePrimaryOrSeriesBackdrop(
    episode: BaseItemDto,
    mediaRepository: MediaRepository,
    width: Int,
    height: Int,
    quality: Int
): String? {
    val episodeId = episode.id ?: return null
    return mediaRepository.getImageUrl(
        itemId = episodeId,
        imageType = "Primary",
        width = width,
        height = height,
        quality = quality,
        enableImageEnhancers = false
    ).first() ?: episode.seriesId?.let { seriesId ->
        mediaRepository.getBackdropImageUrl(
            itemId = seriesId,
            imageIndex = 0,
            width = width,
            height = height,
            quality = quality,
            enableImageEnhancers = false
        ).first()
    }
}
