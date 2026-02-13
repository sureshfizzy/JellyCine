package com.jellycine.data.api

import com.jellycine.data.model.AuthenticationRequest
import com.jellycine.data.model.AuthenticationResult
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.PlaybackInfoResponse
import com.jellycine.data.model.PlaybackProgressRequest
import com.jellycine.data.model.PlaybackStartRequest
import com.jellycine.data.model.PlaybackStoppedRequest
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.ServerInfo
import com.jellycine.data.model.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MediaServerApi {

    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): Response<ServerInfo>

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(@Body request: AuthenticationRequest): Response<AuthenticationResult>

    @GET("Users/{userId}/Items/Latest")
    suspend fun getLatestItems(
        @Path("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("fields") fields: String? = null
    ): Response<List<BaseItemDto>>

    @GET("Users/{userId}/Items")
    suspend fun getUserItems(
        @Path("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("recursive") recursive: Boolean? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("startIndex") startIndex: Int? = null,
        @Query("fields") fields: String? = null
    ): Response<QueryResult<BaseItemDto>>

    @GET("Users/{userId}/Views")
    suspend fun getUserViews(@Path("userId") userId: String): Response<QueryResult<BaseItemDto>>

    @GET("Users/{userId}/Items/Resume")
    suspend fun getResumeItems(
        @Path("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("startIndex") startIndex: Int? = null,
        @Query("recursive") recursive: Boolean = true,
        @Query("sortBy") sortBy: String = "DatePlayed",
        @Query("sortOrder") sortOrder: String = "Descending",
        @Query("fields") fields: String? = null
    ): Response<QueryResult<BaseItemDto>>

    @GET("Users/{userId}")
    suspend fun getUserById(@Path("userId") userId: String): Response<UserDto>

    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItemById(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Query("fields") fields: String? = "People,Studios,Genres,Overview,ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId"
    ): Response<BaseItemDto>

    @GET("Genres")
    suspend fun getGenres(
        @Query("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("recursive") recursive: Boolean? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("enableTotalRecordCount") enableTotalRecordCount: Boolean? = null,
        @Query("enableImages") enableImages: Boolean? = null,
        @Query("startIndex") startIndex: Int? = null,
        @Query("limit") limit: Int? = null
    ): Response<QueryResult<BaseItemDto>>

    @GET("Items")
    suspend fun getItemsByGenre(
        @Query("userId") userId: String,
        @Query("genreIds") genreIds: String,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("recursive") recursive: Boolean? = true,
        @Query("limit") limit: Int? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("fields") fields: String? = null
    ): Response<QueryResult<BaseItemDto>>

    // TV Show specific endpoints
    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasons(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("fields") fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount"
    ): Response<QueryResult<BaseItemDto>>

    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("seasonId") seasonId: String? = null,
        @Query("fields") fields: String? = "Overview,MediaStreams,SeriesName,SeriesId,SeasonName,SeasonId",
        @Query("limit") limit: Int? = null,
        @Query("startIndex") startIndex: Int? = null
    ): Response<QueryResult<BaseItemDto>>

    // Streaming/Playback endpoints
    @GET("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String
    ): Response<PlaybackInfoResponse>

    @GET("Videos/{itemId}/stream")
    suspend fun getVideoStreamUrl(
        @Path("itemId") itemId: String,
        @Query("static") static: Boolean = true,
        @Query("mediaSourceId") mediaSourceId: String? = null,
        @Query("deviceId") deviceId: String? = null,
        @Query("api_key") apiKey: String? = null
    ): String

    @GET("Audio/{itemId}/stream")
    suspend fun getAudioStreamUrl(
        @Path("itemId") itemId: String,
        @Query("static") static: Boolean = true,
        @Query("mediaSourceId") mediaSourceId: String? = null,
        @Query("deviceId") deviceId: String? = null,
        @Query("api_key") apiKey: String? = null
    ): String

    // Search endpoint - using the correct Jellyfin search parameter
    @GET("Users/{userId}/Items")
    suspend fun searchItems(
        @Path("userId") userId: String,
        @Query("searchTerm") searchTerm: String,
        @Query("includeItemTypes") includeItemTypes: String? = "Movie,Series",
        @Query("recursive") recursive: Boolean = true,
        @Query("limit") limit: Int? = 50,
        @Query("fields") fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,ProductionYear,Overview"
    ): Response<QueryResult<BaseItemDto>>

    // Alternative search endpoint using nameStartsWith
    @GET("Users/{userId}/Items")
    suspend fun searchItemsByName(
        @Path("userId") userId: String,
        @Query("nameStartsWith") nameStartsWith: String,
        @Query("includeItemTypes") includeItemTypes: String? = "Movie,Series",
        @Query("recursive") recursive: Boolean = true,
        @Query("limit") limit: Int? = 50,
        @Query("fields") fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,ProductionYear,Overview"
    ): Response<QueryResult<BaseItemDto>>

    // Session reporting endpoints for playback progress tracking
    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(@Body request: PlaybackStartRequest): Response<Unit>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(@Body request: PlaybackProgressRequest): Response<Unit>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(@Body request: PlaybackStoppedRequest): Response<Unit>
}

typealias JellyfinApi = MediaServerApi
typealias EmbyApi = MediaServerApi
