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
internal data class SeerrLocalLoginRequest(
    val email: String,
    val password: String
)

@Serializable
internal data class SeerrTitleRequest(
    val mediaType: String,
    val mediaId: Int,
    @SerialName("is4k")
    val is4K: Boolean = false,
    val seasons: List<Int>? = null,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val languageProfileId: Int? = null
)

@Serializable
internal data class SeerrServiceSettingsResponse(
    val server: SeerrServiceServerResponse? = null,
    val profiles: List<SeerrServiceProfileResponse> = emptyList(),
    val rootFolders: List<SeerrRootFolderResponse> = emptyList(),
    val languageProfiles: List<SeerrServiceProfileResponse>? = null
)

@Serializable
internal data class SeerrServiceServerResponse(
    val id: Int? = null,
    val name: String? = null,
    val activeProfileId: Int? = null,
    val activeDirectory: String? = null,
    val activeLanguageProfileId: Int? = null,
    @SerialName("is4k")
    val is4K: Boolean? = null,
    val isDefault: Boolean? = null
)

@Serializable
internal data class SeerrServiceProfileResponse(
    val id: Int? = null,
    val name: String? = null
)

@Serializable
internal data class SeerrRootFolderResponse(
    val path: String? = null
)

@Serializable
internal data class SeerrCurrentUserResponse(
    val id: Int? = null,
    val permissions: Int? = null
)

@Serializable
internal data class SeerrQuotaResponse(
    val movie: SeerrQuotaDetails = SeerrQuotaDetails(),
    val tv: SeerrQuotaDetails = SeerrQuotaDetails()
)

@Serializable
internal data class SeerrQuotaDetails(
    val days: Int? = null,
    val limit: Int? = null,
    val remaining: Int? = null
)

@Serializable
internal data class SeerrRequestsResponse(
    val results: List<SeerrRequestEntry> = emptyList()
)

@Serializable
internal data class SeerrRequestEntry(
    val id: Long? = null,
    val status: Int? = null,
    val type: String? = null,
    val media: SeerrRequestMedia? = null,
    val requestedBy: SeerrRequestUser? = null,
    val createdAt: String? = null,
    val seasons: List<SeerrRequestSeason> = emptyList(),
    val seasonCount: Int? = null,
    @SerialName("is4k")
    val is4K: Boolean? = null
)

@Serializable
internal data class SeerrRequestMedia(
    val jellyfinMediaId: String? = null,
    val tmdbId: Long? = null,
    val mediaType: String? = null,
    val status: Int? = null
)

@Serializable
internal data class SeerrRequestUser(
    val id: Int? = null,
    val displayName: String? = null,
    val username: String? = null
)

@Serializable
internal data class SeerrRequestSeason(
    val seasonNumber: Int? = null
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
    val requests: List<SeerrMediaRequest> = emptyList(),
    val seasons: List<SeerrMediaSeason> = emptyList()
)

@Serializable
internal data class SeerrMediaRequest(
    val status: Int? = null
)

@Serializable
internal data class SeerrMediaSeason(
    val seasonNumber: Int? = null,
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
    val seasons: List<SeerrTitleSeason> = emptyList(),
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
internal data class SeerrTitleSeason(
    val seasonNumber: Int? = null,
    val episodeCount: Int? = null,
    @SerialName("seer_poster_path")
    val seerPosterPath: String? = null,
    val posterPath: String? = null
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

data class SeerrCatalogItem(
    val id: String,
    val name: String,
    val logoUrl: String? = null
)

enum class SeerrDiscoveryCategory {
    TRENDING,
    POPULAR_MOVIES,
    POPULAR_SHOWS,
    UPCOMING_MOVIES,
    UPCOMING_SHOWS
}

enum class SeerrPersonCreditType {
    DIRECTOR,
    ACTOR
}

enum class SeerrPersonRole(
    val creditType: SeerrPersonCreditType
) {
    DIRECTOR(SeerrPersonCreditType.DIRECTOR),
    ACTOR(SeerrPersonCreditType.ACTOR)
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

data class SeerrRequestedItem(
    val requestId: Long,
    val tmdbId: String,
    val title: String,
    val mediaType: String,
    val localItemId: String? = null,
    val productionYear: Int? = null,
    val posterUrl: String? = null,
    val requestStatus: Int? = null,
    val mediaStatus: Int? = null,
    val requestedAt: String? = null,
    val seasonCount: Int? = null,
    val is4K: Boolean = false
)

data class SeerrRequestOptions(
    val mediaType: String,
    val destinations: List<SeerrRequestDestination>,
    val canUseAdvancedRequests: Boolean = false,
    val canRequest4K: Boolean = false,
    val quota: SeerrRequestQuota? = null,
    val seasons: List<SeerrSeasonRequestOption> = emptyList()
)

data class SeerrRequestDestination(
    val id: Int,
    val name: String,
    val isDefault: Boolean = false,
    val defaultProfileId: Int? = null,
    val defaultRootFolder: String? = null,
    val defaultLanguageProfileId: Int? = null,
    val qualityProfiles: List<SeerrRequestProfile> = emptyList(),
    val rootFolders: List<SeerrRequestRootFolder> = emptyList()
)

data class SeerrRequestProfile(
    val id: Int,
    val name: String
)

data class SeerrRequestRootFolder(
    val path: String
)

data class SeerrSeasonRequestOption(
    val seasonNumber: Int,
    val episodeCount: Int? = null,
    val posterUrl: String? = null,
    val requestState: SeerrRequestState = SeerrRequestState.NONE
)

data class SeerrRequestQuota(
    val remaining: Int? = null,
    val limit: Int? = null,
    val days: Int? = null
)

data class SeerrRequestSelection(
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val languageProfileId: Int? = null,
    val is4K: Boolean = false,
    val seasons: List<Int>? = null
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