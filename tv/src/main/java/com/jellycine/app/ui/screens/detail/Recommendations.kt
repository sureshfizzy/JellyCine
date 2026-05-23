package com.jellycine.app.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.repository.MediaRepository
import com.jellycine.shared.ui.components.common.SimilarItemsSection

@Composable
internal fun Recommendations(
    item: BaseItemDto,
    directors: List<BaseItemPerson>,
    mediaRepository: MediaRepository,
    onItemClick: (String) -> Unit
) {
    var similarItems by remember(item.id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var directorTitles by remember(item.id, directors.map { it.id }.joinToString()) {
        mutableStateOf<List<BaseItemDto>>(emptyList())
    }
    val primaryDirector = directors.firstOrNull()

    LaunchedEffect(item.id) {
        val currentItemId = item.id
        if (currentItemId.isNullOrBlank()) {
            similarItems = emptyList()
            return@LaunchedEffect
        }

        mediaRepository.getSimilarItems(itemId = currentItemId, limit = 16).fold(
            onSuccess = { items ->
                similarItems = items.filter { !it.id.isNullOrBlank() }
            },
            onFailure = {
                similarItems = emptyList()
            }
        )
    }

    LaunchedEffect(item.id, primaryDirector?.id) {
        val directorId = primaryDirector?.id
        if (directorId.isNullOrBlank()) {
            directorTitles = emptyList()
            return@LaunchedEffect
        }

        mediaRepository.getItemsForPerson(directorId).fold(
            onSuccess = { items ->
                val targetType = if (item.type.equals("Movie", ignoreCase = true)) {
                    "Movie"
                } else {
                    "Series"
                }
                directorTitles = items.filter { it.type.equals(targetType, ignoreCase = true) }
            },
            onFailure = {
                directorTitles = emptyList()
            }
        )
    }

    if (primaryDirector != null && directorTitles.isNotEmpty()) {
        SimilarItemsSection(
            similarItems = directorTitles,
            mediaRepository = mediaRepository,
            onItemClick = onItemClick,
            title = "Directed by ${primaryDirector.name}"
        )
    }

    SimilarItemsSection(
        similarItems = similarItems,
        mediaRepository = mediaRepository,
        onItemClick = onItemClick
    )
}