package com.jellycine.ui.screens.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.ui.components.common.SimilarItemsSection

/** Similar items from the user's own library. */
@Composable
internal fun LocalRecommendations(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    onItemClick: (String) -> Unit
) {
    var similarItems by remember(item.id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }

    LaunchedEffect(item.id) {
        val itemId = item.id
        similarItems = if (itemId.isNullOrBlank() || item.isSeerDetailItem()) {
            emptyList()
        } else {
            mediaRepository.getSimilarItems(itemId = itemId, limit = 16)
                .getOrDefault(emptyList())
                .filter { !it.id.isNullOrBlank() }
        }
    }

    SimilarItemsSection(
        similarItems = similarItems,
        seerrItems = emptyList(),
        mediaRepository = mediaRepository,
        onItemClick = onItemClick
    )
}
