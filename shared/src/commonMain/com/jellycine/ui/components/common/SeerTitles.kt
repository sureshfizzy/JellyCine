package com.jellycine.ui.components.common

import com.jellycine.data.model.*
import com.jellycine.data.repository.*

suspend fun fetchSeerCreditTitles(
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

suspend fun fetchSeerTmdbPerson(
    item: BaseItemDto,
    personTmdbId: String,
    role: SeerrPersonRole,
    activeServerId: String?,
    seerrRepository: SeerrRepository,
    limit: Int = 12
): List<SeerrRecommendationTitle> {
    val scopeId = seerrRepository.verifiedScopeId(activeServerId) ?: return emptyList()

    val seerrMediaType = if (item.type.equals("Movie", ignoreCase = true)) "movie" else "tv"
    return seerrRepository.getPersonTitles(
        scopeId = scopeId,
        personTmdbId = personTmdbId,
        mediaType = seerrMediaType,
        creditType = role.creditType,
        limit = limit
    ).getOrElse { emptyList() }
}

suspend fun fetchSeerTmdbPersonId(
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

private fun SeerrRepository.verifiedScopeId(activeServerId: String?): String? {
    val scopeId = activeServerId?.takeIf { it.isNotBlank() } ?: return null
    return scopeId.takeIf { getSavedConnectionInfo(it)?.isVerified == true }
}

suspend fun fetchSeerFilmographyForTmdbPerson(
    personTmdbId: String,
    activeServerId: String?,
    seerrRepository: SeerrRepository
): List<SeerrRecommendationTitle> =
    listOf("Movie", "Series")
        .flatMap { type ->
            fetchSeerTmdbPerson(
                item = BaseItemDto(type = type),
                personTmdbId = personTmdbId,
                role = SeerrPersonRole.ACTOR,
                activeServerId = activeServerId,
                seerrRepository = seerrRepository,
                limit = 60
            )
        }
        .distinctBy { "${it.mediaType}:${it.tmdbId}" }
        .sortedByDescending { it.productionYear ?: Int.MIN_VALUE }
