package com.jellycine.data.model

import com.jellycine.data.network.trimTrailingSlash

internal object SeerrMapper {
    fun toBaseItem(
        response: SeerrTitleDetailsResponse,
        itemId: String,
        mediaType: String,
        serverUrl: String
    ): BaseItemDto? {
        val releaseDate = response.releaseDate.ifNotBlank()
            ?: response.firstAirDate.ifNotBlank()
        val title = response.title.ifNotBlank()
            ?: response.name.ifNotBlank()
            ?: response.originalTitle.ifNotBlank()
            ?: response.originalName.ifNotBlank()
            ?: return null
        val studios = response.productionCompanies
            .mapNotNull { company -> company.name.ifNotBlank() }
            .distinct()
        val creators = response.createdBy
            .mapNotNull { creator -> creator.name.ifNotBlank() }
            .distinct()
        val tmdbId = response.id?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val posterUrl = seerrImageUrl(
            serverUrl = serverUrl,
            imagePath = response.posterPath.ifNotBlank(),
            size = "w780"
        )
        val backdropUrl = seerrImageUrl(
            serverUrl = serverUrl,
            imagePath = response.backdropPath.ifNotBlank(),
            size = "w1280"
        )
        val runTimeTicks = (response.runtime ?: response.episodeRunTime.firstOrNull())
            ?.takeIf { it > 0 }
            ?.toLong()
            ?.times(60_000_0000L)

        return BaseItemDto(
            id = itemId,
            name = title,
            originalTitle = response.originalTitle.ifNotBlank()
                ?: response.originalName.ifNotBlank(),
            overview = response.overview.ifNotBlank(),
            taglines = listOfNotNull(response.tagline.ifNotBlank()),
            genres = response.genres.mapNotNull { genre -> genre.name.ifNotBlank() }.distinct(),
            communityRating = response.voteAverage?.toFloat(),
            runTimeTicks = runTimeTicks,
            premiereDate = releaseDate,
            productionYear = releaseDate.extractYear(),
            providerIds = mapOf("tmdb" to tmdbId),
            type = if (mediaType.equals("tv", ignoreCase = true)) "Series" else "Movie",
            people = titlePeople(response, serverUrl, creators),
            studios = studios.map { studio -> NameGuidPair(name = studio) },
            officialRating = response.contentRatings.ratingFor("US")
                ?: response.releaseDates.certificationFor("US"),
            status = response.status.ifNotBlank(),
            imageUrl = posterUrl,
            backdropImageUrl = backdropUrl,
            seerrRequestState = response.mediaInfo.toRequestState()
        )
    }

    fun toBaseItem(
        response: SeerrPersonDetailsResponse,
        serverUrl: String
    ): BaseItemDto? {
        val tmdbId = response.id?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val profileUrl = seerrImageUrl(
            serverUrl = serverUrl,
            imagePath = response.profilePath.ifNotBlank(),
            size = "w780"
        )
        return BaseItemDto(
            id = SeerrItemIds.personId(tmdbId),
            name = response.name.ifNotBlank() ?: return null,
            overview = response.biography.ifNotBlank(),
            providerIds = mapOf("tmdb" to tmdbId),
            type = "Person",
            imageUrl = profileUrl
        )
    }

    private fun titlePeople(
        response: SeerrTitleDetailsResponse,
        serverUrl: String,
        creators: List<String>
    ): List<BaseItemPerson> {
        val creatorPeople = creators.map { creator ->
            BaseItemPerson(
                name = creator,
                role = "Creator",
                type = "Creator"
            )
        }
        val crewPeople = response.credits?.crew.orEmpty()
            .mapNotNull { credit -> credit.toBaseItemPerson(serverUrl = serverUrl, useCharacter = false) }
            .take(20)
        val castPeople = response.credits?.cast.orEmpty()
            .mapNotNull { credit -> credit.toBaseItemPerson(serverUrl = serverUrl, useCharacter = true) }
            .take(20)

        return (creatorPeople + crewPeople + castPeople)
            .distinctBy { person -> "${person.type}:${person.name}:${person.role}" }
    }

    private fun SeerrTitleCreditEntry.toBaseItemPerson(
        serverUrl: String,
        useCharacter: Boolean
    ): BaseItemPerson? {
        val creditName = name.ifNotBlank() ?: return null
        val role = if (useCharacter) character.ifNotBlank() else job.ifNotBlank()
        val tmdbPersonId = id?.toString()?.takeIf { it.isNotBlank() }
        return BaseItemPerson(
            id = tmdbPersonId?.let(SeerrItemIds::personId),
            name = creditName,
            role = role,
            type = if (useCharacter) "Actor" else role,
            imageUrl = seerrImageUrl(
                serverUrl = serverUrl,
                imagePath = profilePath.ifNotBlank(),
                size = "w185"
            )
        )
    }

    private fun SeerrContentRatings?.ratingFor(countryCode: String): String? {
        return this?.results
            ?.firstOrNull { result -> result.iso31661.equals(countryCode, ignoreCase = true) }
            ?.rating
            .ifNotBlank()
            ?: this?.results?.firstNotNullOfOrNull { result -> result.rating.ifNotBlank() }
    }

    private fun SeerrReleaseDates?.certificationFor(countryCode: String): String? {
        return this?.results
            ?.firstOrNull { result -> result.iso31661.equals(countryCode, ignoreCase = true) }
            ?.releaseDates
            ?.firstNotNullOfOrNull { entry -> entry.certification.ifNotBlank() }
            ?: this?.results
                ?.asSequence()
                ?.flatMap { result -> result.releaseDates.asSequence() }
                ?.firstNotNullOfOrNull { entry -> entry.certification.ifNotBlank() }
    }
}

internal fun seerrImageUrl(
    serverUrl: String,
    imagePath: String?,
    size: String
): String? {
    val path = imagePath.ifNotBlank() ?: return null
    return "${trimTrailingSlash(serverUrl)}/imageproxy/tmdb/t/p/$size/${path.removePrefix("/")}"
}

private fun String?.ifNotBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun String?.extractYear(): Int? =
    this?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()

private fun SeerrMediaInfo?.toRequestState(): SeerrRequestState {
    if (this == null) return SeerrRequestState.NONE

    val hasActiveRequest = requests.any { request ->
        request.status == 1 || request.status == 2
    }
    val hasRequestedMediaStatus = status == 2 || status == 3

    return if (hasActiveRequest || hasRequestedMediaStatus) {
        SeerrRequestState.REQUESTED
    } else {
        SeerrRequestState.NONE
    }
}