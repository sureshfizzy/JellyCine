package com.jellycine.data.repository

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.network.ServerEndpoint

enum class AuthMessage(val resourceName: String) {
    INVALID_SERVER_ID("auth_error_invalid_server_id"),
    SAVED_SERVER_NOT_FOUND("auth_error_saved_server_not_found"),
    SAVED_SESSION_EXPIRED("auth_error_saved_session_expired"),
    REMOVE_ACTIVE_SERVER("auth_error_remove_active_server"),
    INVALID_URL_SCHEME("auth_error_invalid_url_scheme"),
    UNABLE_TO_CONNECT("auth_error_unable_to_connect"),
    CANNOT_REACH_SERVER("auth_error_cannot_reach_server"),
    CONNECTION_REFUSED("auth_error_connection_refused"),
    CONNECTION_TIMEOUT("auth_error_connection_timeout"),
    CONNECTION_TIMEOUT_UNAVAILABLE("auth_error_connection_timeout_unavailable"),
    CONNECT_FAILED("auth_error_connect_failed"),
    CONNECTION_REFUSED_SHORT("auth_error_connection_refused_short"),
    UNKNOWN_CONNECTION("auth_error_unknown_connection"),
    SSL_FAILED("auth_error_ssl_failed"),
    CERTIFICATE_FAILED("auth_error_certificate_failed"),
    NETWORK_ERROR("auth_error_network"),
    AUTHENTICATION_FAILED("auth_error_authentication_failed"),
    QUICK_CONNECT_START_FAILED("auth_error_quick_connect_start_failed"),
    QUICK_CONNECT_SECRET_MISSING("auth_error_quick_connect_secret_missing"),
    ENDPOINT_UNRESOLVED("data_error_server_endpoint_unresolved"),
    ENDPOINT_HTTP("data_error_server_endpoint_http")
}

interface AuthPlatform {
    suspend fun resolveEndpoint(serverUrl: String): Result<ServerEndpoint>

    fun createApi(endpoint: ServerEndpoint): MediaServerApi

    fun text(message: AuthMessage, vararg args: Any): String

    fun connectionErrorMessage(error: Throwable): String?

    suspend fun onSessionEnded(serverId: String?)
}
