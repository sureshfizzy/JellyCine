package com.jellycine.app.ui.components.common

import androidx.compose.material.icons.rounded.Movie
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

private fun SeerrRepository.verifiedScopeId(activeServerId: String?): String? {
    val scopeId = activeServerId?.takeIf { it.isNotBlank() } ?: return null
    return scopeId.takeIf { getSavedConnectionInfo(it)?.isVerified == true }
}
