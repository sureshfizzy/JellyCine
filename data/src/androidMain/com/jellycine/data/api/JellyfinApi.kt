package com.jellycine.data.api

import com.jellycine.data.model.AuthenticationRequest
import com.jellycine.data.model.AuthenticationResult
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.PlaybackInfoResponse
import com.jellycine.data.model.PlaybackInfoRequest
import com.jellycine.data.model.PlaybackProgressRequest
import com.jellycine.data.model.PlaybackStartRequest
import com.jellycine.data.model.PlaybackStoppedRequest
import com.jellycine.data.model.RecommendationDto
import com.jellycine.data.model.QuickConnectDto
import com.jellycine.data.model.QuickConnectResult
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.ServerInfo
import com.jellycine.data.model.UserDto
import com.jellycine.data.network.ApiResponse

interface MediaServerApi {

    suspend fun getPublicSystemInfo(): ApiResponse<ServerInfo>

    suspend fun authenticateByName(request: AuthenticationRequest): ApiResponse<AuthenticationResult>

    suspend fun initiateQuickConnect(): ApiResponse<QuickConnectResult>

    suspend fun authenticateWithQuickConnect(
        request: QuickConnectDto
    ): ApiResponse<AuthenticationResult>

    suspend fun getLatestItems(
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null,
        limit: Int? = null,
        fields: String? = null
    ): ApiResponse<List<BaseItemDto>>

    suspend fun getUserItems(
        userId: String,
        parentId: String? = null,
        personIds: String? = null,
        genres: String? = null,
        genreIds: String? = null,
        includeItemTypes: String? = null,
        recursive: Boolean? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        limit: Int? = null,
        startIndex: Int? = null,
        filters: String? = null,
        fields: String? = null
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun getSuggestions(
        endpoint: String,
        userId: String? = null,
        mediaType: String? = null,
        type: String? = null,
        includeItemTypes: String? = null,
        limit: Int? = null,
        fields: String? = null
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun getUserViews(userId: String): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun getMovieRecommendations(
        userId: String,
        parentId: String? = null,
        categoryLimit: Int? = null,
        itemLimit: Int? = null,
        fields: String? = null
    ): ApiResponse<List<RecommendationDto>>

    suspend fun getResumeItems(
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null,
        limit: Int? = null,
        startIndex: Int? = null,
        recursive: Boolean = true,
        sortBy: String = "DatePlayed",
        sortOrder: String = "Descending",
        fields: String? = null
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun getNextUp(
        userId: String,
        seriesId: String? = null,
        parentId: String? = null,
        limit: Int? = null,
        startIndex: Int? = null,
        legacyNextUp: Boolean? = null,
        fields: String? = null,
        enableUserData: Boolean? = null,
        enableImages: Boolean? = null,
        imageTypeLimit: Int? = null,
        enableImageTypes: String? = null
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun getUserById(userId: String): ApiResponse<UserDto>

    suspend fun getItemById(
        userId: String,
        itemId: String,
        fields: String? = "People,Studios,Genres,Overview,ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,UserData,Chapters"
    ): ApiResponse<BaseItemDto>

    suspend fun getSimilarItems(
        itemId: String,
        userId: String,
        limit: Int? = null,
        fields: String? = "Overview,Genres,CommunityRating,ProductionYear,OfficialRating,SeriesName,SeriesId,UserData"
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun markAsFavorite(
        userId: String,
        itemId: String
    ): ApiResponse<Unit>

    suspend fun unmarkAsFavorite(
        userId: String,
        itemId: String
    ): ApiResponse<Unit>

    suspend fun getGenres(
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null,
        recursive: Boolean? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        enableTotalRecordCount: Boolean? = null,
        enableImages: Boolean? = null,
        startIndex: Int? = null,
        limit: Int? = null
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun getItemsByGenre(
        userId: String,
        genreIds: String,
        includeItemTypes: String? = null,
        recursive: Boolean? = true,
        limit: Int? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        fields: String? = null
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun getSeasons(
        seriesId: String,
        userId: String,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount"
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun getEpisodes(
        seriesId: String,
        userId: String,
        seasonId: String? = null,
        fields: String? = "Overview,MediaStreams,SeriesName,SeriesId,SeasonName,SeasonId",
        limit: Int? = null,
        startIndex: Int? = null
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun getPlaybackInfoGet(
        itemId: String,
        userId: String,
        maxStreamingBitrate: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        enableDirectPlay: Boolean? = null,
        enableDirectStream: Boolean? = null,
        enableTranscoding: Boolean? = null
    ): ApiResponse<PlaybackInfoResponse>

    suspend fun getPlaybackInfoPost(
        itemId: String,
        request: PlaybackInfoRequest
    ): ApiResponse<PlaybackInfoResponse>

    suspend fun getVideoStreamUrl(
        itemId: String,
        static: Boolean = true,
        mediaSourceId: String? = null,
        deviceId: String? = null,
        apiKey: String? = null
    ): String

    suspend fun searchItems(
        userId: String,
        searchTerm: String,
        includeItemTypes: String? = "Movie,Series",
        recursive: Boolean = true,
        limit: Int? = 50,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,ProductionYear,Overview"
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun searchItemsByName(
        userId: String,
        nameStartsWith: String,
        includeItemTypes: String? = "Movie,Series",
        recursive: Boolean = true,
        limit: Int? = 50,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,ProductionYear,Overview"
    ): ApiResponse<QueryResult<BaseItemDto>>

    suspend fun reportPlaybackStart(request: PlaybackStartRequest): ApiResponse<Unit>

    suspend fun reportPlaybackProgress(request: PlaybackProgressRequest): ApiResponse<Unit>

    suspend fun reportPlaybackStopped(request: PlaybackStoppedRequest): ApiResponse<Unit>
}
