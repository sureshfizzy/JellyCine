package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SeerrStatusResponse(
    val version: String? = null
)

@Serializable
internal data class SeerrLoginRequest(
    val username: String,
    val password: String,
    val hostname: String? = null
)

@Serializable
internal data class SeerrCurrentUserResponse(
    val id: Int? = null
)

@Serializable
internal data class SeerrQuotaResponse(
    val movie: SeerrQuotaDetails = SeerrQuotaDetails(),
    val tv: SeerrQuotaDetails = SeerrQuotaDetails()
)

@Serializable
internal data class SeerrQuotaDetails(
    val days: Int? = null,
    val limit: Int? = null
)

@Serializable
internal data class SeerrCombinedCreditsResponse(
    val cast: List<SeerrCreditEntry> = emptyList(),
    val crew: List<SeerrCreditEntry> = emptyList()
)

@Serializable
internal data class SeerrCreditEntry(
    val id: Long? = null,
    val name: String? = null,
    val title: String? = null,
    val mediaType: String? = null,
    val job: String? = null,
    val posterPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val mediaInfo: SeerrMediaInfo? = null
)

@Serializable
internal data class SeerrMediaInfo(
    val jellyfinMediaId: String? = null,
    val status: Int? = null,
    val requests: List<SeerrMediaRequest> = emptyList()
)

@Serializable
internal data class SeerrMediaRequest(
    val status: Int? = null
)

@Serializable
internal data class SeerrSearchResponse(
    val page: Int? = null,
    val totalPages: Int? = null,
    val totalResults: Int? = null,
    val results: List<SeerrSearchResult> = emptyList()
)

@Serializable
internal data class SeerrSearchResult(
    val id: Long? = null,
    val name: String? = null,
    val title: String? = null,
    val mediaType: String? = null,
    val posterPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val mediaInfo: SeerrMediaInfo? = null
)

@Serializable
internal data class SeerrTitleDetailsResponse(
    val id: Long? = null,
    val title: String? = null,
    val name: String? = null,
    val originalTitle: String? = null,
    val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val runtime: Int? = null,
    val episodeRunTime: List<Int> = emptyList(),
    val status: String? = null,
    val voteAverage: Double? = null,
    val genres: List<SeerrNamedEntity> = emptyList(),
    val productionCompanies: List<SeerrNamedEntity> = emptyList(),
    val createdBy: List<SeerrNamedEntity> = emptyList(),
    val credits: SeerrTitleCredits? = null,
    val contentRatings: SeerrContentRatings? = null,
    val releaseDates: SeerrReleaseDates? = null,
    val mediaInfo: SeerrMediaInfo? = null
)

@Serializable
internal data class SeerrPersonDetailsResponse(
    val id: Long? = null,
    val name: String? = null,
    val biography: String? = null,
    val profilePath: String? = null
)

@Serializable
internal data class SeerrNamedEntity(
    val id: Long? = null,
    val name: String? = null
)

@Serializable
internal data class SeerrTitleCredits(
    val cast: List<SeerrTitleCreditEntry> = emptyList(),
    val crew: List<SeerrTitleCreditEntry> = emptyList()
)

@Serializable
internal data class SeerrTitleCreditEntry(
    val id: Long? = null,
    val name: String? = null,
    val character: String? = null,
    val job: String? = null,
    val profilePath: String? = null
)

@Serializable
internal data class SeerrContentRatings(
    val results: List<SeerrContentRatingResult> = emptyList()
)

@Serializable
internal data class SeerrContentRatingResult(
    @SerialName("iso_3166_1")
    val iso31661: String? = null,
    val rating: String? = null
)

@Serializable
internal data class SeerrReleaseDates(
    val results: List<SeerrReleaseDateResult> = emptyList()
)

@Serializable
internal data class SeerrReleaseDateResult(
    @SerialName("iso_3166_1")
    val iso31661: String? = null,
    @SerialName("release_dates")
    val releaseDates: List<SeerrReleaseDateEntry> = emptyList()
)

@Serializable
internal data class SeerrReleaseDateEntry(
    val certification: String? = null
)

data class SeerrRecommendationTitle(
    val tmdbId: String,
    val title: String,
    val mediaType: String,
    val productionYear: Int? = null,
    val posterUrl: String? = null,
    val jellyfinMediaId: String? = null,
    val roleLabel: String? = null,
    val requestState: SeerrRequestState = SeerrRequestState.NONE
)

data class SeerrStudio(
    val id: String,
    val name: String,
    val logoUrl: String? = null
)

enum class SeerrPersonCreditType {
    DIRECTOR,
    ACTOR
}

enum class SeerrRequestState {
    NONE,
    REQUESTED
}

data class SeerrConnectionInfo(
    val serverUrl: String,
    val serverVersion: String? = null,
    val requestLimits: SeerrUserRequestLimits? = null,
    val isVerified: Boolean = false
)

data class SeerrUserRequestLimits(
    val movieQuotaLimit: Int? = null,
    val movieQuotaDays: Int? = null,
    val tvQuotaLimit: Int? = null,
    val tvQuotaDays: Int? = null
)

object SeerrItemIds {
    fun detailId(tmdbId: String, mediaType: String): String {
        val type = if (mediaType.equals("tv", ignoreCase = true)) "tv" else "movie"
        return "seerr:$type:$tmdbId"
    }

    fun detailParams(itemId: String): Pair<String, String>? {
        val parts = itemId.parts() ?: return null
        val mediaType = when (parts[1]) {
            "movie", "tv" -> parts[1]
            else -> return null
        }
        return mediaType to parts[2]
    }

    fun isDetailId(itemId: String?): Boolean {
        return itemId?.let(::detailParams) != null
    }

    fun personId(tmdbId: String): String {
        return "seerr:person:$tmdbId"
    }

    fun personTmdbId(personId: String): String? {
        val parts = personId.parts() ?: return null
        return parts[2].takeIf { parts[1] == "person" }
    }

    private fun String.parts(): List<String>? {
        val parts = split(':')
        return parts.takeIf { it.size == 3 && it[0] == "seerr" && it[2].isNotBlank() }
    }
}