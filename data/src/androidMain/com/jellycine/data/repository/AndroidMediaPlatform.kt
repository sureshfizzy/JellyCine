package com.jellycine.data.repository

import android.content.Context
import com.jellycine.data.R
import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.network.DohDns
import com.jellycine.data.network.JellyCineJson
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.network.ServerType
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.preferences.NetworkTimeoutConfig
import com.jellycine.data.security.SecureSessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

class AndroidMediaPlatform(private val context: Context) : MediaPlatform {

    private val networkPreferences = NetworkPreferences(context)

    override val clientDeviceId: String
        get() = NetworkModule.getClientDeviceId()

    override val snapshotDirectory: String
        get() = context.filesDir.absolutePath

    override suspend fun text(message: MediaMessage, vararg args: Any): String {
        return context.getString(stringRes(message), *args)
    }

    override fun isInternetAvailable(): Boolean = NetworkModule.isInternetAvailable(context)

    override fun timeoutConfig(): NetworkTimeoutConfig = networkPreferences.getTimeoutConfig()

    override fun createApi(
        baseUrl: String,
        accessToken: String?,
        serverType: ServerType?,
        timeoutConfig: NetworkTimeoutConfig
    ): MediaServerApi {
        return NetworkModule.createMediaServerApi(
            baseUrl = baseUrl,
            accessToken = accessToken,
            serverType = serverType,
            storageDir = context.filesDir,
            timeoutConfig = timeoutConfig
        )
    }

    override fun createExternalHttpClient(): HttpClient {
        val timeouts = networkPreferences.getTimeoutConfig()
        val dispatcher = Dispatcher().apply { maxRequestsPerHost = 32 }
        val okHttpClient = OkHttpClient.Builder()
            .callTimeout(timeouts.requestTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(timeouts.connectionTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .dns(DohDns())
            .dispatcher(dispatcher)
            .build()

        return HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(JellyCineJson)
            }
        }
    }

    override fun createFastHttpClient(): HttpClient {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

        return HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                preconfigured = okHttpClient
            }
        }
    }

    private fun stringRes(message: MediaMessage): Int = when (message) {
        MediaMessage.DATA_ERROR_ACCESS_TOKEN_NOT_AVAILABLE -> R.string.data_error_access_token_not_available
        MediaMessage.DATA_ERROR_API_NOT_AVAILABLE -> R.string.data_error_api_not_available
        MediaMessage.DATA_ERROR_NO_MEDIA_SOURCE_AVAILABLE -> R.string.data_error_no_media_source_available
        MediaMessage.DATA_ERROR_PLAYBACK_INFO_FAILED -> R.string.data_error_playback_info_failed
        MediaMessage.DATA_ERROR_PLAYBACK_INFO_NOT_AVAILABLE -> R.string.data_error_playback_info_not_available
        MediaMessage.DATA_ERROR_SESSION_NOT_AVAILABLE -> R.string.data_error_session_not_available
        MediaMessage.DATA_ERROR_TRANSCODING_URL_UNAVAILABLE -> R.string.data_error_transcoding_url_unavailable
        MediaMessage.DATA_ERROR_USER_ID_NOT_AVAILABLE -> R.string.data_error_user_id_not_available
        MediaMessage.MEDIA_ERROR_FETCH_EPISODES_FAILED -> R.string.media_error_fetch_episodes_failed
        MediaMessage.MEDIA_ERROR_FETCH_ITEM_FAILED -> R.string.media_error_fetch_item_failed
        MediaMessage.MEDIA_ERROR_FETCH_ITEMS_BY_GENRE_FAILED -> R.string.media_error_fetch_items_by_genre_failed
        MediaMessage.MEDIA_ERROR_FETCH_LATEST_ITEMS_FAILED -> R.string.media_error_fetch_latest_items_failed
        MediaMessage.MEDIA_ERROR_FETCH_MOVIE_RECOMMENDATIONS_FAILED -> R.string.media_error_fetch_movie_recommendations_failed
        MediaMessage.MEDIA_ERROR_FETCH_NEXT_UP_ITEMS_FAILED -> R.string.media_error_fetch_next_up_items_failed
        MediaMessage.MEDIA_ERROR_FETCH_RESUME_ITEMS_FAILED -> R.string.media_error_fetch_resume_items_failed
        MediaMessage.MEDIA_ERROR_FETCH_SEASONS_FAILED -> R.string.media_error_fetch_seasons_failed
        MediaMessage.MEDIA_ERROR_FETCH_SIMILAR_ITEMS_FAILED -> R.string.media_error_fetch_similar_items_failed
        MediaMessage.MEDIA_ERROR_FETCH_SUGGESTIONS_FAILED -> R.string.media_error_fetch_suggestions_failed
        MediaMessage.MEDIA_ERROR_FETCH_USER_INFO_FAILED -> R.string.media_error_fetch_user_info_failed
        MediaMessage.MEDIA_ERROR_FETCH_USER_ITEMS_FAILED -> R.string.media_error_fetch_user_items_failed
        MediaMessage.MEDIA_ERROR_FETCH_USER_VIEWS_FAILED -> R.string.media_error_fetch_user_views_failed
        MediaMessage.MEDIA_ERROR_REPORT_PLAYBACK_PROGRESS_FAILED -> R.string.media_error_report_playback_progress_failed
        MediaMessage.MEDIA_ERROR_REPORT_PLAYBACK_START_FAILED -> R.string.media_error_report_playback_start_failed
        MediaMessage.MEDIA_ERROR_REPORT_PLAYBACK_STOPPED_FAILED -> R.string.media_error_report_playback_stopped_failed
        MediaMessage.MEDIA_ERROR_SEARCH_ITEMS_FAILED -> R.string.media_error_search_items_failed
    }
}
