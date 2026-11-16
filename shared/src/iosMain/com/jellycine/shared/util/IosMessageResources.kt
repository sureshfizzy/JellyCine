package com.jellycine.shared.util

import com.jellycine.data.repository.AuthMessage
import com.jellycine.data.repository.MediaMessage
import com.jellycine.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

internal suspend fun AuthMessage.text(args: Array<out Any>): String = getString(resource(), *args)

internal suspend fun MediaMessage.text(args: Array<out Any>): String = getString(resource(), *args)

private fun AuthMessage.resource(): StringResource = when (this) {
    AuthMessage.INVALID_SERVER_ID -> Res.string.data_auth_error_invalid_server_id
    AuthMessage.SAVED_SERVER_NOT_FOUND -> Res.string.data_auth_error_saved_server_not_found
    AuthMessage.SAVED_SESSION_EXPIRED -> Res.string.data_auth_error_saved_session_expired
    AuthMessage.REMOVE_ACTIVE_SERVER -> Res.string.data_auth_error_remove_active_server
    AuthMessage.INVALID_URL_SCHEME -> Res.string.data_auth_error_invalid_url_scheme
    AuthMessage.UNABLE_TO_CONNECT -> Res.string.data_auth_error_unable_to_connect
    AuthMessage.CANNOT_REACH_SERVER -> Res.string.data_auth_error_cannot_reach_server
    AuthMessage.CONNECTION_REFUSED -> Res.string.data_auth_error_connection_refused
    AuthMessage.CONNECTION_TIMEOUT -> Res.string.data_auth_error_connection_timeout
    AuthMessage.CONNECTION_TIMEOUT_UNAVAILABLE -> Res.string.data_auth_error_connection_timeout_unavailable
    AuthMessage.CONNECT_FAILED -> Res.string.data_auth_error_connect_failed
    AuthMessage.CONNECTION_REFUSED_SHORT -> Res.string.data_auth_error_connection_refused_short
    AuthMessage.UNKNOWN_CONNECTION -> Res.string.data_auth_error_unknown_connection
    AuthMessage.SSL_FAILED -> Res.string.data_auth_error_ssl_failed
    AuthMessage.CERTIFICATE_FAILED -> Res.string.data_auth_error_certificate_failed
    AuthMessage.NETWORK_ERROR -> Res.string.data_auth_error_network
    AuthMessage.AUTHENTICATION_FAILED -> Res.string.data_auth_error_authentication_failed
    AuthMessage.QUICK_CONNECT_START_FAILED -> Res.string.data_auth_error_quick_connect_start_failed
    AuthMessage.QUICK_CONNECT_SECRET_MISSING -> Res.string.data_auth_error_quick_connect_secret_missing
    AuthMessage.ENDPOINT_UNRESOLVED -> Res.string.data_data_error_server_endpoint_unresolved
    AuthMessage.ENDPOINT_HTTP -> Res.string.data_data_error_server_endpoint_http
}

private fun MediaMessage.resource(): StringResource = when (this) {
    MediaMessage.DATA_ERROR_ACCESS_TOKEN_NOT_AVAILABLE -> Res.string.data_data_error_access_token_not_available
    MediaMessage.DATA_ERROR_API_NOT_AVAILABLE -> Res.string.data_data_error_api_not_available
    MediaMessage.DATA_ERROR_NO_MEDIA_SOURCE_AVAILABLE -> Res.string.data_data_error_no_media_source_available
    MediaMessage.DATA_ERROR_PLAYBACK_INFO_FAILED -> Res.string.data_data_error_playback_info_failed
    MediaMessage.DATA_ERROR_PLAYBACK_INFO_NOT_AVAILABLE -> Res.string.data_data_error_playback_info_not_available
    MediaMessage.DATA_ERROR_SESSION_NOT_AVAILABLE -> Res.string.data_data_error_session_not_available
    MediaMessage.DATA_ERROR_TRANSCODING_URL_UNAVAILABLE -> Res.string.data_data_error_transcoding_url_unavailable
    MediaMessage.DATA_ERROR_USER_ID_NOT_AVAILABLE -> Res.string.data_data_error_user_id_not_available
    MediaMessage.MEDIA_ERROR_FETCH_EPISODES_FAILED -> Res.string.data_media_error_fetch_episodes_failed
    MediaMessage.MEDIA_ERROR_FETCH_ITEM_FAILED -> Res.string.data_media_error_fetch_item_failed
    MediaMessage.MEDIA_ERROR_FETCH_ITEMS_BY_GENRE_FAILED -> Res.string.data_media_error_fetch_items_by_genre_failed
    MediaMessage.MEDIA_ERROR_FETCH_LATEST_ITEMS_FAILED -> Res.string.data_media_error_fetch_latest_items_failed
    MediaMessage.MEDIA_ERROR_FETCH_MOVIE_RECOMMENDATIONS_FAILED -> Res.string.data_media_error_fetch_movie_recommendations_failed
    MediaMessage.MEDIA_ERROR_FETCH_NEXT_UP_ITEMS_FAILED -> Res.string.data_media_error_fetch_next_up_items_failed
    MediaMessage.MEDIA_ERROR_FETCH_RESUME_ITEMS_FAILED -> Res.string.data_media_error_fetch_resume_items_failed
    MediaMessage.MEDIA_ERROR_FETCH_SEASONS_FAILED -> Res.string.data_media_error_fetch_seasons_failed
    MediaMessage.MEDIA_ERROR_FETCH_SIMILAR_ITEMS_FAILED -> Res.string.data_media_error_fetch_similar_items_failed
    MediaMessage.MEDIA_ERROR_FETCH_SUGGESTIONS_FAILED -> Res.string.data_media_error_fetch_suggestions_failed
    MediaMessage.MEDIA_ERROR_FETCH_USER_INFO_FAILED -> Res.string.data_media_error_fetch_user_info_failed
    MediaMessage.MEDIA_ERROR_FETCH_USER_ITEMS_FAILED -> Res.string.data_media_error_fetch_user_items_failed
    MediaMessage.MEDIA_ERROR_FETCH_USER_VIEWS_FAILED -> Res.string.data_media_error_fetch_user_views_failed
    MediaMessage.MEDIA_ERROR_REPORT_PLAYBACK_PROGRESS_FAILED -> Res.string.data_media_error_report_playback_progress_failed
    MediaMessage.MEDIA_ERROR_REPORT_PLAYBACK_START_FAILED -> Res.string.data_media_error_report_playback_start_failed
    MediaMessage.MEDIA_ERROR_REPORT_PLAYBACK_STOPPED_FAILED -> Res.string.data_media_error_report_playback_stopped_failed
    MediaMessage.MEDIA_ERROR_SEARCH_ITEMS_FAILED -> Res.string.data_media_error_search_items_failed
}
