package com.jellycine.app.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.SeerrRepository
import com.jellycine.shared.R
import com.jellycine.shared.ui.components.common.SimilarItemsSection

@Composable
internal fun Recommendations(
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
    val primaryDirector = directors.firstOrNull()
    val seerrDirectorItems = seerrDirectorItemsState(
        item = item,
        directors = directors,
        isSeerDetail = isSeerDetail,
        activeServerId = activeServerId,
        mediaRepository = mediaRepository,
        seerrRepository = seerrRepository
    )

    if (
        primaryDirector != null &&
        (seerrDirectorItems.localDirectorItems.isNotEmpty() ||
            seerrDirectorItems.seerrDirectorItems.isNotEmpty())
    ) {
        SimilarItemsSection(
            similarItems = seerrDirectorItems.localDirectorItems,
            seerrItems = seerrDirectorItems.seerrDirectorItems,
            mediaRepository = mediaRepository,
            onItemClick = onItemClick,
            title = "Directed by ${primaryDirector.name}"
        )
    }

    SimilarItemsSection(
        similarItems = emptyList(),
        seerrItems = seerrRelatedItems.seerrRecommendedItems,
        mediaRepository = mediaRepository,
        onItemClick = onItemClick,
        title = stringResource(R.string.detail_seerr_recommendations_title)
    )

    SimilarItemsSection(
        similarItems = seerrRelatedItems.localSimilarItems,
        seerrItems = seerrRelatedItems.seerrSimilarItems,
        mediaRepository = mediaRepository,
        onItemClick = onItemClick
    )
}