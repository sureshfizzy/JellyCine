package com.jellycine.data.repository

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.network.ServerType
import com.jellycine.data.preferences.NetworkTimeoutConfig
import io.ktor.client.HttpClient

enum class MediaMessage {
    DATA_ERROR_ACCESS_TOKEN_NOT_AVAILABLE,
    DATA_ERROR_API_NOT_AVAILABLE,
    DATA_ERROR_NO_MEDIA_SOURCE_AVAILABLE,
    DATA_ERROR_PLAYBACK_INFO_FAILED,
    DATA_ERROR_PLAYBACK_INFO_NOT_AVAILABLE,
    DATA_ERROR_SESSION_NOT_AVAILABLE,
    DATA_ERROR_TRANSCODING_URL_UNAVAILABLE,
    DATA_ERROR_USER_ID_NOT_AVAILABLE,
    MEDIA_ERROR_FETCH_EPISODES_FAILED,
    MEDIA_ERROR_FETCH_ITEM_FAILED,
    MEDIA_ERROR_FETCH_ITEMS_BY_GENRE_FAILED,
    MEDIA_ERROR_FETCH_LATEST_ITEMS_FAILED,
    MEDIA_ERROR_FETCH_MOVIE_RECOMMENDATIONS_FAILED,
    MEDIA_ERROR_FETCH_NEXT_UP_ITEMS_FAILED,
    MEDIA_ERROR_FETCH_RESUME_ITEMS_FAILED,
    MEDIA_ERROR_FETCH_SEASONS_FAILED,
    MEDIA_ERROR_FETCH_SIMILAR_ITEMS_FAILED,
    MEDIA_ERROR_FETCH_SUGGESTIONS_FAILED,
    MEDIA_ERROR_FETCH_USER_INFO_FAILED,
    MEDIA_ERROR_FETCH_USER_ITEMS_FAILED,
    MEDIA_ERROR_FETCH_USER_VIEWS_FAILED,
    MEDIA_ERROR_REPORT_PLAYBACK_PROGRESS_FAILED,
    MEDIA_ERROR_REPORT_PLAYBACK_START_FAILED,
    MEDIA_ERROR_REPORT_PLAYBACK_STOPPED_FAILED,
    MEDIA_ERROR_SEARCH_ITEMS_FAILED
}

interface MediaPlatform {
    val clientDeviceId: String

    val snapshotDirectory: String

    suspend fun text(message: MediaMessage, vararg args: Any): String

    fun isInternetAvailable(): Boolean

    fun timeoutConfig(): NetworkTimeoutConfig

    fun createApi(
        baseUrl: String,
        accessToken: String?,
        serverType: ServerType?,
        timeoutConfig: NetworkTimeoutConfig
    ): MediaServerApi

    fun createExternalHttpClient(): HttpClient

    fun createFastHttpClient(): HttpClient
}
