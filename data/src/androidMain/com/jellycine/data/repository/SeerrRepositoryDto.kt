package com.jellycine.data.repository

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
    @SerialName("posterPath")
    val posterPath: String? = null,
    @SerialName("poster_path")
    val posterPathSnake: String? = null,
    @SerialName("releaseDate")
    val releaseDate: String? = null,
    @SerialName("firstAirDate")
    val firstAirDate: String? = null,
    val mediaInfo: SeerrCreditMediaInfo? = null
)

@Serializable
internal data class SeerrCreditMediaInfo(
    val jellyfinMediaId: String? = null
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
    @SerialName("media_type")
    val mediaTypeSnake: String? = null,
    @SerialName("posterPath")
    val posterPath: String? = null,
    @SerialName("poster_path")
    val posterPathSnake: String? = null,
    @SerialName("releaseDate")
    val releaseDate: String? = null,
    @SerialName("release_date")
    val releaseDateSnake: String? = null,
    @SerialName("firstAirDate")
    val firstAirDate: String? = null,
    @SerialName("first_air_date")
    val firstAirDateSnake: String? = null,
    val mediaInfo: SeerrCreditMediaInfo? = null
)