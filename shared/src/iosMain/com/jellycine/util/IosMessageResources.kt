package com.jellycine.util

import com.jellycine.data.repository.AuthMessage
import com.jellycine.data.repository.MediaMessage
import com.jellycine.data.repository.SeerrMessage
import com.jellycine.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

internal suspend fun AuthMessage.text(args: Array<out Any>): String = getString(resource(), *args)

internal suspend fun MediaMessage.text(args: Array<out Any>): String = getString(resource(), *args)

internal suspend fun SeerrMessage.text(args: Array<out Any>): String = getString(resource(), *args)

private fun SeerrMessage.resource(): StringResource = when (this) {
    SeerrMessage.CREDIT_ROLE_ACTOR -> Res.string.data_seerr_credit_role_actor
    SeerrMessage.CREDIT_ROLE_CREATOR -> Res.string.data_seerr_credit_role_creator
    SeerrMessage.CREDIT_ROLE_DIRECTOR -> Res.string.data_seerr_credit_role_director
    SeerrMessage.CREDIT_ROLE_OTHER -> Res.string.data_seerr_credit_role_other
    SeerrMessage.CREDIT_ROLE_PRODUCER -> Res.string.data_seerr_credit_role_producer
    SeerrMessage.CREDIT_ROLE_WRITER -> Res.string.data_seerr_credit_role_writer
    SeerrMessage.ERROR_CERTIFICATE -> Res.string.data_seerr_error_certificate
    SeerrMessage.ERROR_CONNECT -> Res.string.data_seerr_error_connect
    SeerrMessage.ERROR_CONNECT_GENERIC -> Res.string.data_seerr_error_connect_generic
    SeerrMessage.ERROR_CREDENTIALS_REJECTED -> Res.string.data_seerr_error_credentials_rejected
    SeerrMessage.ERROR_CURRENT_USER_LOAD -> Res.string.data_seerr_error_current_user_load
    SeerrMessage.ERROR_CURRENT_USER_PARSE -> Res.string.data_seerr_error_current_user_parse
    SeerrMessage.ERROR_DISCOVERY_LOAD -> Res.string.data_seerr_error_discovery_load
    SeerrMessage.ERROR_DISCOVERY_PARSE -> Res.string.data_seerr_error_discovery_parse
    SeerrMessage.ERROR_HTTP_RESPONSE -> Res.string.data_seerr_error_http_response
    SeerrMessage.ERROR_MEDIA_SERVER_URL_INVALID -> Res.string.data_seerr_error_media_server_url_invalid
    SeerrMessage.ERROR_MEDIA_SERVER_URL_MISSING -> Res.string.data_seerr_error_media_server_url_missing
    SeerrMessage.ERROR_MEDIA_TYPE_UNSUPPORTED -> Res.string.data_seerr_error_media_type_unsupported
    SeerrMessage.ERROR_MISSING_SESSION_COOKIE -> Res.string.data_seerr_error_missing_session_cookie
    SeerrMessage.ERROR_NETWORK_SHOWS_LOAD -> Res.string.data_seerr_error_network_shows_load
    SeerrMessage.ERROR_NETWORK_SHOWS_PARSE -> Res.string.data_seerr_error_network_shows_parse
    SeerrMessage.ERROR_NOT_CONNECTED -> Res.string.data_seerr_error_not_connected
    SeerrMessage.ERROR_NOT_FOUND_AT_URL -> Res.string.data_seerr_error_not_found_at_url
    SeerrMessage.ERROR_PASSWORD_REQUIRED -> Res.string.data_seerr_error_password_required
    SeerrMessage.ERROR_PERSON_CREDITS_FETCH -> Res.string.data_seerr_error_person_credits_fetch
    SeerrMessage.ERROR_PERSON_CREDITS_PARSE -> Res.string.data_seerr_error_person_credits_parse
    SeerrMessage.ERROR_PERSON_DETAILS_INCOMPLETE -> Res.string.data_seerr_error_person_details_incomplete
    SeerrMessage.ERROR_PERSON_DETAILS_LOAD -> Res.string.data_seerr_error_person_details_load
    SeerrMessage.ERROR_PERSON_DETAILS_PARSE -> Res.string.data_seerr_error_person_details_parse
    SeerrMessage.ERROR_PERSON_ID_MISSING -> Res.string.data_seerr_error_person_id_missing
    SeerrMessage.ERROR_PERSON_NOT_FOUND -> Res.string.data_seerr_error_person_not_found
    SeerrMessage.ERROR_RELATED_TITLES_LOAD -> Res.string.data_seerr_error_related_titles_load
    SeerrMessage.ERROR_RELATED_TITLES_PARSE -> Res.string.data_seerr_error_related_titles_parse
    SeerrMessage.ERROR_REQUEST_OPTIONS_LOAD -> Res.string.data_seerr_error_request_options_load
    SeerrMessage.ERROR_REQUEST_OPTIONS_PARSE -> Res.string.data_seerr_error_request_options_parse
    SeerrMessage.ERROR_REQUEST_TITLE -> Res.string.data_seerr_error_request_title
    SeerrMessage.ERROR_REQUEST_TITLE_PARSE -> Res.string.data_seerr_error_request_title_parse
    SeerrMessage.ERROR_REQUESTS_LOAD -> Res.string.data_seerr_error_requests_load
    SeerrMessage.ERROR_REQUESTS_PARSE -> Res.string.data_seerr_error_requests_parse
    SeerrMessage.ERROR_RETURNED_HTTP_VERIFY_URL -> Res.string.data_seerr_error_returned_http_verify_url
    SeerrMessage.ERROR_SEARCH_RESULTS_PARSE -> Res.string.data_seerr_error_search_results_parse
    SeerrMessage.ERROR_SEARCH_TITLES -> Res.string.data_seerr_error_search_titles
    SeerrMessage.ERROR_SIGN_IN_HTTP -> Res.string.data_seerr_error_sign_in_http
    SeerrMessage.ERROR_SSL -> Res.string.data_seerr_error_ssl
    SeerrMessage.ERROR_STUDIO_MOVIES_LOAD -> Res.string.data_seerr_error_studio_movies_load
    SeerrMessage.ERROR_STUDIO_MOVIES_PARSE -> Res.string.data_seerr_error_studio_movies_parse
    SeerrMessage.ERROR_TIMEOUT -> Res.string.data_seerr_error_timeout
    SeerrMessage.ERROR_TITLE_DETAILS_INCOMPLETE -> Res.string.data_seerr_error_title_details_incomplete
    SeerrMessage.ERROR_TITLE_DETAILS_LOAD -> Res.string.data_seerr_error_title_details_load
    SeerrMessage.ERROR_TITLE_DETAILS_PARSE -> Res.string.data_seerr_error_title_details_parse
    SeerrMessage.ERROR_TITLE_ID_MISSING -> Res.string.data_seerr_error_title_id_missing
    SeerrMessage.ERROR_TITLE_NOT_FOUND -> Res.string.data_seerr_error_title_not_found
    SeerrMessage.ERROR_UNKNOWN_HOST -> Res.string.data_seerr_error_unknown_host
    SeerrMessage.ERROR_UNREACHABLE -> Res.string.data_seerr_error_unreachable
    SeerrMessage.ERROR_URL_INVALID -> Res.string.data_seerr_error_url_invalid
    SeerrMessage.ERROR_URL_REQUIRED -> Res.string.data_seerr_error_url_required
    SeerrMessage.ERROR_URL_SCHEME_REQUIRED -> Res.string.data_seerr_error_url_scheme_required
    SeerrMessage.ERROR_USERNAME_REQUIRED -> Res.string.data_seerr_error_username_required
    SeerrMessage.ERROR_VERIFY_SESSION_HTTP -> Res.string.data_seerr_error_verify_session_http
}

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
