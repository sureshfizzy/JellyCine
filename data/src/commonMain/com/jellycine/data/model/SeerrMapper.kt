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

fun filterSeerTitlesForRow(
    seerrTitles: List<SeerrRecommendationTitle>,
    baseTitles: List<BaseItemDto>,
    item: BaseItemDto
): List<SeerrRecommendationTitle> {
    if (seerrTitles.isEmpty()) return emptyList()

    val seerBaseTitles = baseTitles.map { baseTitle ->
        baseTitle.name.lookupKey() to baseTitle.productionYear
    }.toSet()
    val existingLocalIds = buildSet {
        item.id?.let(::add)
        baseTitles.mapNotNullTo(this) { it.id }
    }

    return seerrTitles.filter { seerrTitle ->
        val normalizedSeerrTitle = seerrTitle.title.lookupKey()
        val matchesCurrentItem = normalizedSeerrTitle == item.name.lookupKey() &&
            yearMatch(item.productionYear, seerrTitle.productionYear)
        val matchesBaseTitle = seerBaseTitles.any { (titleKey, year) ->
            titleKey == normalizedSeerrTitle && yearMatch(year, seerrTitle.productionYear)
        }
        val localId = seerrTitle.jellyfinMediaId?.let(existingLocalIds::contains) == true

        !matchesCurrentItem && !matchesBaseTitle && !localId
    }
}

fun filterSeerTitles(
    seerrTitles: List<SeerrRecommendationTitle>,
    localItems: List<BaseItemDto>
): List<SeerrRecommendationTitle> {
    if (seerrTitles.isEmpty()) return emptyList()

    val localTitles = localItems.map { item ->
        item.name.lookupKey() to item.productionYear
    }.toSet()
    val localIds = localItems.mapNotNull { item -> item.id }.toSet()

    return seerrTitles.filterNot { seerrTitle ->
        val normalizedTitle = seerrTitle.title.lookupKey()
        val localIdMatch = seerrTitle.jellyfinMediaId?.let(localIds::contains) == true
        val titleMatch = localTitles.any { (titleKey, year) ->
            titleKey == normalizedTitle && yearMatch(year, seerrTitle.productionYear)
        }

        localIdMatch || titleMatch
    }
}

fun seerPersonId(
    items: List<BaseItemDto>,
    personName: String,
    role: SeerrPersonRole
): String? {
    val seerPersonName = personName.lookupKey()
    return findPersonId(
        people = items.asSequence().flatMap { item -> item.people.orEmpty().asSequence() },
        seerPersonName = seerPersonName
    ) { person -> role.matches(person) }
}

fun SeerrRecommendationTitle.toSeerDetailItem(): BaseItemDto {
    return BaseItemDto(
        id = SeerrItemIds.detailId(tmdbId = tmdbId, mediaType = mediaType)
    )
}

fun BaseItemDto.seerTitleParams(): Pair<String, String>? {
    SeerrItemIds.detailParams(id.orEmpty())?.let { return it }
    val tmdbId = providerIds.providerId("tmdb") ?: return null
    val mediaType = when {
        type.equals("Series", ignoreCase = true) -> "tv"
        type.equals("Movie", ignoreCase = true) -> "movie"
        else -> return null
    }
    return mediaType to tmdbId
}

fun Map<String, String>?.providerId(providerName: String): String? {
    return this?.entries
        ?.firstOrNull { (key, value) -> key.equals(providerName, ignoreCase = true) && value.isNotBlank() }
        ?.value
}

private fun SeerrPersonRole.matches(person: BaseItemPerson): Boolean {
    return when (this) {
        SeerrPersonRole.DIRECTOR -> listOf(person.role, person.type).any { field ->
            field?.contains("Director", ignoreCase = true) == true
        }

        SeerrPersonRole.ACTOR -> {
            val isDirector = listOf(person.role, person.type).any { field ->
                field?.contains("Director", ignoreCase = true) == true
            }
            if (isDirector) {
                false
            } else {
                listOf(person.role, person.type).any { field ->
                    field?.contains("Actor", ignoreCase = true) == true ||
                        field?.contains("Star", ignoreCase = true) == true ||
                        field?.contains("Cast", ignoreCase = true) == true
                } || person.type.isNullOrBlank()
            }
        }
    }
}

private fun findPersonId(
    people: Sequence<BaseItemPerson>,
    seerPersonName: String,
    matches: (BaseItemPerson) -> Boolean = { true }
): String? {
    return people
        .filter { person ->
            person.id != null &&
                person.name.lookupKey() == seerPersonName &&
                matches(person)
        }
        .groupBy { person -> person.id.orEmpty() }
        .maxByOrNull { (_, matches) -> matches.size }
        ?.key
        ?.takeIf { it.isNotBlank() }
}

private fun String?.lookupKey(): String {
    return this
        .orEmpty()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "")
}

private fun yearMatch(localYear: Int?, seerrYear: Int?): Boolean {
    return localYear == null || seerrYear == null || localYear == seerrYear
}