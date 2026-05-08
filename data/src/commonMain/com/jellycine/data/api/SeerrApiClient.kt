package com.jellycine.data.api

import com.jellycine.data.model.SeerrCombinedCreditsResponse
import com.jellycine.data.model.SeerrCurrentUserResponse
import com.jellycine.data.model.SeerrLoginRequest
import com.jellycine.data.model.SeerrPersonDetailsResponse
import com.jellycine.data.model.SeerrQuotaResponse
import com.jellycine.data.model.SeerrRequestsResponse
import com.jellycine.data.model.SeerrSearchResponse
import com.jellycine.data.model.SeerrServiceServerResponse
import com.jellycine.data.model.SeerrServiceSettingsResponse
import com.jellycine.data.model.SeerrStatusResponse
import com.jellycine.data.model.SeerrTitleDetailsResponse
import com.jellycine.data.model.SeerrTitleRequest
import com.jellycine.data.network.ApiHeaders
import com.jellycine.data.network.ApiResponse
import com.jellycine.data.network.trimTrailingSlash
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.contentType

internal class SeerrApiClient(
    private val serverUrl: String,
    private val client: HttpClient,
    private val sessionCookie: String? = null
) {
    suspend fun status(): ApiResponse<SeerrStatusResponse> = get("status")

    suspend fun login(request: SeerrLoginRequest): ApiResponse<Unit> = post("auth/jellyfin", request)

    suspend fun currentUser(): ApiResponse<SeerrCurrentUserResponse> = get("auth/me")

    suspend fun quota(userId: Int): ApiResponse<SeerrQuotaResponse> = get("user/$userId/quota")

    suspend fun requests(
        take: Int,
        skip: Int,
        filter: String = "all"
    ): ApiResponse<SeerrRequestsResponse> = get(
        path = "request",
        queryParameters = listOf(
            "take" to take.toString(),
            "skip" to skip.toString(),
            "filter" to filter
        )
    )

    suspend fun personCredits(personTmdbId: String): ApiResponse<SeerrCombinedCreditsResponse> =
        get("person/$personTmdbId/combined_credits")

    suspend fun personDetails(personTmdbId: String): ApiResponse<SeerrPersonDetailsResponse> =
        get("person/$personTmdbId")

    suspend fun search(query: String): ApiResponse<SeerrSearchResponse> = get(
        path = "search",
        queryParameters = listOf("query" to query, "page" to "1")
    )

    suspend fun trending(page: Int): ApiResponse<SeerrSearchResponse> = get(
        path = "discover/trending",
        queryParameters = listOf("page" to page.toString())
    )

    suspend fun popularMovies(page: Int): ApiResponse<SeerrSearchResponse> = get(
        path = "discover/movies",
        queryParameters = listOf("page" to page.toString())
    )

    suspend fun popularSeries(page: Int): ApiResponse<SeerrSearchResponse> = get(
        path = "discover/tv",
        queryParameters = listOf("page" to page.toString())
    )

    suspend fun upcomingMovies(page: Int): ApiResponse<SeerrSearchResponse> = get(
        path = "discover/movies/upcoming",
        queryParameters = listOf("page" to page.toString())
    )

    suspend fun upcomingSeries(page: Int): ApiResponse<SeerrSearchResponse> = get(
        path = "discover/tv/upcoming",
        queryParameters = listOf("page" to page.toString())
    )

    suspend fun studioMovies(studioId: String, page: Int): ApiResponse<SeerrSearchResponse> = get(
        path = "discover/movies/studio/$studioId",
        queryParameters = listOf("page" to page.toString())
    )

    suspend fun networkSeries(networkId: String, page: Int): ApiResponse<SeerrSearchResponse> = get(
        path = "discover/tv/network/$networkId",
        queryParameters = listOf("page" to page.toString())
    )

    suspend fun titleDetails(mediaType: String, tmdbId: String): ApiResponse<SeerrTitleDetailsResponse> =
        get("$mediaType/$tmdbId")

    suspend fun requestTitle(request: SeerrTitleRequest): ApiResponse<Unit> =
        post("request", request)

    suspend fun radarrServers(): ApiResponse<List<SeerrServiceServerResponse>> =
        get("service/radarr")

    suspend fun sonarrServers(): ApiResponse<List<SeerrServiceServerResponse>> =
        get("service/sonarr")

    suspend fun radarrService(serverId: Int): ApiResponse<SeerrServiceSettingsResponse> =
        get("service/radarr/$serverId")

    suspend fun sonarrService(serverId: Int): ApiResponse<SeerrServiceSettingsResponse> =
        get("service/sonarr/$serverId")

    private suspend inline fun <reified T> get(
        path: String,
        queryParameters: List<Pair<String, String>> = emptyList()
    ): ApiResponse<T> = client.get(apiUrl(path)) {
        accept(ContentType.Application.Json)
        sessionCookie?.let { header(HttpHeaders.Cookie, it) }
        queryParameters.forEach { (name, value) -> parameter(name, value) }
    }.toApiResponse()

    private suspend inline fun <reified T, reified R> post(
        path: String,
        requestBody: R
    ): ApiResponse<T> = client.post(apiUrl(path)) {
        accept(ContentType.Application.Json)
        contentType(ContentType.Application.Json)
        sessionCookie?.let { header(HttpHeaders.Cookie, it) }
        setBody(requestBody)
    }.toApiResponse()

    private suspend inline fun <reified T> HttpResponse.toApiResponse(): ApiResponse<T> {
        val code = status.value
        return if (code in 200..299) {
            @Suppress("UNCHECKED_CAST")
            ApiResponse(
                bodyValue = runCatching {
                    if (T::class == Unit::class) Unit as T else body<T>()
                }.getOrNull(),
                statusCode = code,
                statusMessage = status.description,
                headerValues = ApiHeaders.from(headers.entries().associate { it.key to it.value })
            )
        } else {
            ApiResponse(
                bodyValue = null,
                statusCode = code,
                statusMessage = status.description,
                headerValues = ApiHeaders.from(headers.entries().associate { it.key to it.value }),
                errorBodyValue = bodyAsText()
            )
        }
    }

    private fun apiUrl(path: String): String =
        URLBuilder("${trimTrailingSlash(serverUrl)}/api/v1/${path.trimStart('/')}").buildString()
}