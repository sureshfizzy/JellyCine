package com.vela.data.api

import com.vela.data.util.currentTimeMillis
import com.vela.data.model.ActivityLogResult
import com.vela.data.model.AuthenticationRequest
import com.vela.data.model.AuthenticationResult
import com.vela.data.model.BaseItemDto
import com.vela.data.model.PlaybackInfoRequest
import com.vela.data.model.PlaybackInfoResponse
import com.vela.data.model.PlaybackProgressRequest
import com.vela.data.model.PlaybackStartRequest
import com.vela.data.model.PlaybackStoppedRequest
import com.vela.data.model.QuickConnectDto
import com.vela.data.model.QuickConnectResult
import com.vela.data.model.QueryResult
import com.vela.data.model.RecommendationDto
import com.vela.data.model.ItemIdResult
import com.vela.data.model.AdminSessionInfo
import com.vela.data.model.ServerInfo
import com.vela.data.model.SystemInfoFull
import com.vela.data.model.UserDto
import com.vela.data.network.ApiHeaders
import com.vela.data.network.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.contentType

internal class MediaServerApiClient(
    private val client: HttpClient,
    private val baseUrl: String
) : MediaServerApi {

    override suspend fun getPublicSystemInfo(): ApiResponse<ServerInfo> =
        get("System/Info/Public")

    override suspend fun authenticateByName(
        request: AuthenticationRequest
    ): ApiResponse<AuthenticationResult> = post(
        endpoint = "Users/AuthenticateByName",
        requestBody = request
    )

    override suspend fun initiateQuickConnect(): ApiResponse<QuickConnectResult> =
        post("QuickConnect/Initiate")

    override suspend fun authenticateWithQuickConnect(
        request: QuickConnectDto
    ): ApiResponse<AuthenticationResult> = post(
        endpoint = "Users/AuthenticateWithQuickConnect",
        requestBody = request
    )

    override suspend fun getLatestItems(
        userId: String,
        parentId: String?,
        includeItemTypes: String?,
        limit: Int?,
        fields: String?
    ): ApiResponse<List<BaseItemDto>> = get(
        endpoint = "Users/$userId/Items/Latest",
        queryParameters = listOf(
            "parentId" to parentId,
            "includeItemTypes" to includeItemTypes,
            "limit" to limit,
            "fields" to fields
        )
    )

    override suspend fun getUserItems(
        userId: String,
        parentId: String?,
        personIds: String?,
        genres: String?,
        genreIds: String?,
        includeItemTypes: String?,
        recursive: Boolean?,
        sortBy: String?,
        sortOrder: String?,
        limit: Int?,
        startIndex: Int?,
        filters: String?,
        anyProviderIdEquals: String?,
        fields: String?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Users/$userId/Items",
        queryParameters = listOf(
            "parentId" to parentId,
            "PersonIds" to personIds,
            "genres" to genres,
            "genreIds" to genreIds,
            "includeItemTypes" to includeItemTypes,
            "recursive" to recursive,
            "sortBy" to sortBy,
            "sortOrder" to sortOrder,
            "limit" to limit,
            "startIndex" to startIndex,
            "filters" to filters,
            "AnyProviderIdEquals" to anyProviderIdEquals,
            "fields" to fields
        )
    )

    override suspend fun getSuggestions(
        endpoint: String,
        userId: String?,
        mediaType: String?,
        type: String?,
        includeItemTypes: String?,
        limit: Int?,
        fields: String?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = endpoint,
        queryParameters = listOf(
            "userId" to userId,
            "mediaType" to mediaType,
            "type" to type,
            "IncludeItemTypes" to includeItemTypes,
            "limit" to limit,
            "fields" to fields
        )
    )

    override suspend fun getUserViews(userId: String): ApiResponse<QueryResult<BaseItemDto>> =
        get("Users/$userId/Views")

    override suspend fun getMovieRecommendations(
        userId: String,
        parentId: String?,
        categoryLimit: Int?,
        itemLimit: Int?,
        fields: String?
    ): ApiResponse<List<RecommendationDto>> = get(
        endpoint = "Movies/Recommendations",
        queryParameters = listOf(
            "userId" to userId,
            "parentId" to parentId,
            "categoryLimit" to categoryLimit,
            "itemLimit" to itemLimit,
            "fields" to fields
        )
    )

    override suspend fun getResumeItems(
        userId: String,
        parentId: String?,
        includeItemTypes: String?,
        limit: Int?,
        startIndex: Int?,
        recursive: Boolean,
        sortBy: String,
        sortOrder: String,
        fields: String?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Users/$userId/Items/Resume",
        queryParameters = listOf(
            "parentId" to parentId,
            "includeItemTypes" to includeItemTypes,
            "limit" to limit,
            "startIndex" to startIndex,
            "recursive" to recursive,
            "sortBy" to sortBy,
            "sortOrder" to sortOrder,
            "fields" to fields
        )
    )

    override suspend fun getNextUp(
        userId: String,
        seriesId: String?,
        parentId: String?,
        limit: Int?,
        startIndex: Int?,
        legacyNextUp: Boolean?,
        fields: String?,
        enableUserData: Boolean?,
        enableImages: Boolean?,
        imageTypeLimit: Int?,
        enableImageTypes: String?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Shows/NextUp",
        queryParameters = listOf(
            "userId" to userId,
            "seriesId" to seriesId,
            "parentId" to parentId,
            "limit" to limit,
            "startIndex" to startIndex,
            "LegacyNextUp" to legacyNextUp,
            "fields" to fields,
            "enableUserData" to enableUserData,
            "enableImages" to enableImages,
            "imageTypeLimit" to imageTypeLimit,
            "enableImageTypes" to enableImageTypes
        )
    )

    override suspend fun getUserById(userId: String): ApiResponse<UserDto> =
        get("Users/$userId")

    override suspend fun getItemById(
        userId: String,
        itemId: String,
        fields: String?
    ): ApiResponse<BaseItemDto> = get(
        endpoint = "Users/$userId/Items/$itemId",
        queryParameters = listOf("fields" to fields)
    )

    override suspend fun getSimilarItems(
        itemId: String,
        userId: String,
        limit: Int?,
        fields: String?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Items/$itemId/Similar",
        queryParameters = listOf(
            "userId" to userId,
            "limit" to limit,
            "fields" to fields
        )
    )

    override suspend fun markAsFavorite(
        userId: String,
        itemId: String
    ): ApiResponse<Unit> = post("Users/$userId/FavoriteItems/$itemId")

    override suspend fun unmarkAsFavorite(
        userId: String,
        itemId: String
    ): ApiResponse<Unit> = execute(
        method = HttpMethod.Delete,
        endpoint = "Users/$userId/FavoriteItems/$itemId"
    )

    override suspend fun markAsPlayed(
        userId: String,
        itemId: String
    ): ApiResponse<Unit> = post("Users/$userId/PlayedItems/$itemId")

    override suspend fun unmarkAsPlayed(
        userId: String,
        itemId: String
    ): ApiResponse<Unit> = execute(
        method = HttpMethod.Delete,
        endpoint = "Users/$userId/PlayedItems/$itemId"
    )

    override suspend fun getGenres(
        userId: String,
        parentId: String?,
        includeItemTypes: String?,
        recursive: Boolean?,
        sortBy: String?,
        sortOrder: String?,
        enableTotalRecordCount: Boolean?,
        enableImages: Boolean?,
        startIndex: Int?,
        limit: Int?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Genres",
        queryParameters = listOf(
            "userId" to userId,
            "parentId" to parentId,
            "includeItemTypes" to includeItemTypes,
            "recursive" to recursive,
            "sortBy" to sortBy,
            "sortOrder" to sortOrder,
            "enableTotalRecordCount" to enableTotalRecordCount,
            "enableImages" to enableImages,
            "startIndex" to startIndex,
            "limit" to limit
        )
    )

    override suspend fun getItemsByGenre(
        userId: String,
        genreIds: String,
        includeItemTypes: String?,
        recursive: Boolean?,
        limit: Int?,
        sortBy: String?,
        sortOrder: String?,
        fields: String?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Items",
        queryParameters = listOf(
            "userId" to userId,
            "genreIds" to genreIds,
            "includeItemTypes" to includeItemTypes,
            "recursive" to recursive,
            "limit" to limit,
            "sortBy" to sortBy,
            "sortOrder" to sortOrder,
            "fields" to fields
        )
    )

    override suspend fun getSeasons(
        seriesId: String,
        userId: String,
        fields: String?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Shows/$seriesId/Seasons",
        queryParameters = listOf(
            "userId" to userId,
            "fields" to fields
        )
    )

    override suspend fun getEpisodes(
        seriesId: String,
        userId: String,
        seasonId: String?,
        fields: String?,
        limit: Int?,
        startIndex: Int?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Shows/$seriesId/Episodes",
        queryParameters = listOf(
            "userId" to userId,
            "seasonId" to seasonId,
            "fields" to fields,
            "limit" to limit,
            "startIndex" to startIndex
        )
    )

    override suspend fun getPlaybackInfoGet(
        itemId: String,
        userId: String,
        maxStreamingBitrate: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        enableDirectPlay: Boolean?,
        enableDirectStream: Boolean?,
        enableTranscoding: Boolean?
    ): ApiResponse<PlaybackInfoResponse> = get(
        endpoint = "Items/$itemId/PlaybackInfo",
        queryParameters = listOf(
            "userId" to userId,
            "maxStreamingBitrate" to maxStreamingBitrate,
            "audioStreamIndex" to audioStreamIndex,
            "subtitleStreamIndex" to subtitleStreamIndex,
            "enableDirectPlay" to enableDirectPlay,
            "enableDirectStream" to enableDirectStream,
            "enableTranscoding" to enableTranscoding
        )
    )

    override suspend fun getPlaybackInfoPost(
        itemId: String,
        request: PlaybackInfoRequest
    ): ApiResponse<PlaybackInfoResponse> = post(
        endpoint = "Items/$itemId/PlaybackInfo",
        requestBody = request
    )

    override suspend fun getVideoStreamUrl(
        itemId: String,
        static: Boolean,
        mediaSourceId: String?,
        deviceId: String?,
        apiKey: String?
    ): String = buildUrl(
        endpoint = "Videos/$itemId/stream",
        queryParameters = listOf(
            "static" to static,
            "mediaSourceId" to mediaSourceId,
            "deviceId" to deviceId,
            "api_key" to apiKey
        )
    )

    override suspend fun searchItems(
        userId: String,
        searchTerm: String,
        includeItemTypes: String?,
        recursive: Boolean,
        limit: Int?,
        fields: String?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Users/$userId/Items",
        queryParameters = listOf(
            "searchTerm" to searchTerm,
            "includeItemTypes" to includeItemTypes,
            "recursive" to recursive,
            "limit" to limit,
            "fields" to fields
        )
    )

    override suspend fun searchItemsByName(
        userId: String,
        nameStartsWith: String,
        includeItemTypes: String?,
        recursive: Boolean,
        limit: Int?,
        fields: String?
    ): ApiResponse<QueryResult<BaseItemDto>> = get(
        endpoint = "Users/$userId/Items",
        queryParameters = listOf(
            "nameStartsWith" to nameStartsWith,
            "includeItemTypes" to includeItemTypes,
            "recursive" to recursive,
            "limit" to limit,
            "fields" to fields
        )
    )

    override suspend fun reportPlaybackStart(
        request: PlaybackStartRequest
    ): ApiResponse<Unit> = post(
        endpoint = "Sessions/Playing",
        requestBody = request
    )

    override suspend fun reportPlaybackProgress(
        request: PlaybackProgressRequest
    ): ApiResponse<Unit> = post(
        endpoint = "Sessions/Playing/Progress",
        requestBody = request
    )

    override suspend fun reportPlaybackStopped(
        request: PlaybackStoppedRequest
    ): ApiResponse<Unit> = post(
        endpoint = "Sessions/Playing/Stopped",
        requestBody = request
    )

    override suspend fun getSystemInfo(): ApiResponse<SystemInfoFull> =
        get("System/Info")

    override suspend fun getActiveSessions(): ApiResponse<List<AdminSessionInfo>> =
        get(
            endpoint = "Sessions",
            queryParameters = listOf(
                "ActiveWithinSeconds" to 960,
                "_" to currentTimeMillis()
            )
        )

    override suspend fun getActivityLog(
        startIndex: Int?,
        limit: Int?
    ): ApiResponse<ActivityLogResult> = get(
        endpoint = "System/ActivityLog/Entries",
        queryParameters = listOf(
            "startIndex" to startIndex,
            "limit" to limit,
            "_" to currentTimeMillis()
        )
    )

    override suspend fun refreshItem(
        itemId: String,
        recursive: Boolean,
        metadataRefreshMode: String,
        imageRefreshMode: String,
        replaceAllMetadata: Boolean
    ): ApiResponse<Unit> = post(
        endpoint = "Items/$itemId/Refresh",
        queryParameters = listOf(
            "Recursive" to recursive,
            "MetadataRefreshMode" to metadataRefreshMode,
            "ImageRefreshMode" to imageRefreshMode,
            "ReplaceAllMetadata" to replaceAllMetadata
        )
    )

    override suspend fun deleteItem(itemId: String): ApiResponse<Unit> = execute(
        method = HttpMethod.Delete,
        endpoint = "Items/$itemId"
    )

    override suspend fun updateItem(
        itemId: String,
        item: BaseItemDto
    ): ApiResponse<Unit> = post(
        endpoint = "Items/$itemId",
        requestBody = item
    )

    override suspend fun createPlaylist(
        name: String,
        ids: String,
        userId: String
    ): ApiResponse<ItemIdResult> = post(
        endpoint = "Playlists",
        queryParameters = listOf(
            "Name" to name,
            "Ids" to ids,
            "UserId" to userId
        )
    )

    override suspend fun addToPlaylist(
        playlistId: String,
        ids: String,
        userId: String
    ): ApiResponse<Unit> = post(
        endpoint = "Playlists/$playlistId/Items",
        queryParameters = listOf(
            "Ids" to ids,
            "UserId" to userId
        )
    )

    override suspend fun createCollection(
        name: String,
        ids: String
    ): ApiResponse<ItemIdResult> = post(
        endpoint = "Collections",
        queryParameters = listOf(
            "Name" to name,
            "Ids" to ids
        )
    )

    override suspend fun addToCollection(
        collectionId: String,
        ids: String
    ): ApiResponse<Unit> = post(
        endpoint = "Collections/$collectionId/Items",
        queryParameters = listOf("Ids" to ids)
    )

    private suspend inline fun <reified T> get(
        endpoint: String,
        queryParameters: List<Pair<String, Any?>> = emptyList()
    ): ApiResponse<T> = execute(
        method = HttpMethod.Get,
        endpoint = endpoint,
        queryParameters = queryParameters
    )

    private suspend inline fun <reified T> post(
        endpoint: String,
        requestBody: Any? = null,
        queryParameters: List<Pair<String, Any?>> = emptyList()
    ): ApiResponse<T> = execute(
        method = HttpMethod.Post,
        endpoint = endpoint,
        requestBody = requestBody,
        queryParameters = queryParameters
    )

    private suspend inline fun <reified T> execute(
        method: HttpMethod,
        endpoint: String,
        requestBody: Any? = null,
        queryParameters: List<Pair<String, Any?>> = emptyList()
    ): ApiResponse<T> {
        val response = client.request(endpoint) {
            this.method = method
            queryParameters.forEach { (name, value) ->
                value?.let { parameter(name, it) }
            }
            if (requestBody != null) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }
        return response.toApiResponse()
    }

    private suspend inline fun <reified T> HttpResponse.toApiResponse(): ApiResponse<T> {
        val headers = ApiHeaders.from(this.headers.entries().associate { it.key to it.value })
        val code = status.value
        val message = status.description

        return if (status.value in 200..299) {
            val bodyValue = if (T::class == Unit::class) {
                @Suppress("UNCHECKED_CAST")
                Unit as T
            } else {
                body<T>()
            }
            ApiResponse(
                bodyValue = bodyValue,
                statusCode = code,
                statusMessage = message,
                headerValues = headers
            )
        } else {
            ApiResponse(
                bodyValue = null,
                statusCode = code,
                statusMessage = message,
                headerValues = headers,
                errorBodyValue = bodyAsText()
            )
        }
    }

    private fun buildUrl(
        endpoint: String,
        queryParameters: List<Pair<String, Any?>>
    ): String {
        val requestUrl = if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            endpoint
        } else {
            "${baseUrl.trimEnd('/')}/${endpoint.trimStart('/')}"
        }
        val urlBuilder = URLBuilder(requestUrl)
        queryParameters.forEach { (name, value) ->
            value?.let { urlBuilder.parameters.append(name, it.toString()) }
        }
        return urlBuilder.buildString()
    }
}