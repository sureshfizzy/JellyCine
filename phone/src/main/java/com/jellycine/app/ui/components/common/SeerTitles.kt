package com.jellycine.app.ui.components.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.jellycine.ui.components.common.SeerTitleCard
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrPersonRole
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.providerId
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.SeerrRepository

internal suspend fun fetchSeerCreditTitles(
    item: BaseItemDto,
    personId: String,
    role: SeerrPersonRole,
    activeServerId: String?,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository
): List<SeerrRecommendationTitle> {
    val personItem = mediaRepository.getItemById(personId).getOrNull() ?: return emptyList()
    val personTmdbId = personItem.providerIds.providerId("tmdb") ?: return emptyList()
    return fetchSeerTmdbPerson(
        item = item,
        personTmdbId = personTmdbId,
        role = role,
        activeServerId = activeServerId,
        seerrRepository = seerrRepository
    )
}

internal suspend fun fetchSeerTmdbPerson(
    item: BaseItemDto,
    personTmdbId: String,
    role: SeerrPersonRole,
    activeServerId: String?,
    seerrRepository: SeerrRepository
): List<SeerrRecommendationTitle> {
    val scopeId = seerrRepository.verifiedScopeId(activeServerId) ?: return emptyList()

    val seerrMediaType = if (item.type.equals("Movie", ignoreCase = true)) "movie" else "tv"
    return seerrRepository.getPersonTitles(
        scopeId = scopeId,
        personTmdbId = personTmdbId,
        mediaType = seerrMediaType,
        creditType = role.creditType
    ).getOrElse { emptyList() }
}

internal suspend fun fetchSeerTmdbPersonId(
    item: BaseItemDto,
    personId: String,
    role: SeerrPersonRole,
    activeServerId: String?,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository
): List<SeerrRecommendationTitle> {
    val tmdbPersonId = SeerrItemIds.personTmdbId(personId)
    return if (tmdbPersonId != null) {
        fetchSeerTmdbPerson(
            item = item,
            personTmdbId = tmdbPersonId,
            role = role,
            activeServerId = activeServerId,
            seerrRepository = seerrRepository
        )
    } else {
        fetchSeerCreditTitles(
            item = item,
            personId = personId,
            role = role,
            activeServerId = activeServerId,
            mediaRepository = mediaRepository,
            seerrRepository = seerrRepository
        )
    }
}

internal suspend fun fetchSeerDirectedTitlesForTmdbPerson(
    personTmdbId: String,
    activeServerId: String?,
    seerrRepository: SeerrRepository
): List<SeerrRecommendationTitle> {
    val movieTitles = fetchSeerTmdbPerson(
        item = BaseItemDto(type = "Movie"),
        personTmdbId = personTmdbId,
        role = SeerrPersonRole.DIRECTOR,
        activeServerId = activeServerId,
        seerrRepository = seerrRepository
    )
    val showTitles = fetchSeerTmdbPerson(
        item = BaseItemDto(type = "Series"),
        personTmdbId = personTmdbId,
        role = SeerrPersonRole.DIRECTOR,
        activeServerId = activeServerId,
        seerrRepository = seerrRepository
    )
    return (movieTitles + showTitles).distinctBy { item -> "${item.mediaType}:${item.tmdbId}" }
}

internal fun LazyListScope.seerTitleItems(
    items: List<SeerrRecommendationTitle>,
    onItemClick: (String) -> Unit
) {
    itemsIndexed(
        items = items,
        key = { index, item ->
            val itemId = item.jellyfinMediaId?.takeIf { it.isNotBlank() }
                ?: SeerrItemIds.detailId(tmdbId = item.tmdbId, mediaType = item.mediaType)
            "${itemId}_$index"
        }
    ) { _, item ->
        SeerTitleCard(
            item = item,
            onClick = {
                val itemId = item.jellyfinMediaId?.takeIf { it.isNotBlank() }
                    ?: SeerrItemIds.detailId(tmdbId = item.tmdbId, mediaType = item.mediaType)
                onItemClick(itemId)
            }
        )
    }
}

@Composable
internal fun SeerTitlesRow(
    title: String,
    items: List<SeerrRecommendationTitle>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 24.dp,
    verticalSpacing: Dp = 12.dp,
    horizontalPadding: Dp = 0.dp,
    titleFontSize: TextUnit = 21.sp
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        Text(
            text = title,
            fontSize = titleFontSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding)
        ) {
            seerTitleItems(
                items = items,
                onItemClick = onItemClick
            )
        }
    }
}

private fun SeerrRepository.verifiedScopeId(activeServerId: String?): String? {
    val scopeId = activeServerId?.takeIf { it.isNotBlank() } ?: return null
    return scopeId.takeIf { getSavedConnectionInfo(it)?.isVerified == true }
}
