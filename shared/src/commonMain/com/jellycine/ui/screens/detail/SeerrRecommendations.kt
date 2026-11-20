package com.jellycine.ui.screens.detail

import androidx.compose.runtime.*
import com.jellycine.data.model.*
import com.jellycine.data.repository.*
import com.jellycine.resources.*
import com.jellycine.ui.components.common.SimilarItemsSection
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SeerrRecommendations(
    item: BaseItemDto,
    directors: List<BaseItemPerson>,
    isSeerDetail: Boolean,
    activeServerId: String?,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository,
    onItemClick: (String) -> Unit
) {
    val seerrRelatedItems = seerrRelatedItemsState(
        item = item,
        isSeerDetail = isSeerDetail,
        activeServerId = activeServerId,
        mediaRepository = mediaRepository,
        seerrRepository = seerrRepository
    )
    directors.forEachIndexed { index, director ->
        key(director.id ?: director.name ?: index) {
            val seerrDirectorItems = seerrDirectorItemsState(
                item = item,
                directors = listOf(director),
                isSeerDetail = isSeerDetail,
                activeServerId = activeServerId,
                mediaRepository = mediaRepository,
                seerrRepository = seerrRepository
            )

            if (
                seerrDirectorItems.localDirectorItems.isNotEmpty() ||
                    seerrDirectorItems.seerrDirectorItems.isNotEmpty()
            ) {
                SimilarItemsSection(
                    similarItems = seerrDirectorItems.localDirectorItems,
                    seerrItems = seerrDirectorItems.seerrDirectorItems,
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick,
                    title = stringResource(
                        Res.string.detail_directed_by,
                        director.name ?: stringResource(Res.string.detail_similar_item_unknown)
                    )
                )
            }
        }
    }

    SimilarItemsSection(
        similarItems = emptyList(),
        seerrItems = seerrRelatedItems.seerrRecommendedItems,
        mediaRepository = mediaRepository,
        onItemClick = onItemClick,
        title = stringResource(Res.string.detail_seerr_recommendations_title)
    )

    SimilarItemsSection(
        similarItems = seerrRelatedItems.localSimilarItems,
        seerrItems = seerrRelatedItems.seerrSimilarItems,
        mediaRepository = mediaRepository,
        onItemClick = onItemClick
    )
}