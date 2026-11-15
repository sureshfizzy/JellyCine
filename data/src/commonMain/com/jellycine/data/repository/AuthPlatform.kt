package com.jellycine.data.repository

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.network.ServerEndpoint

enum class AuthMessage {
    INVALID_SERVER_ID,
    SAVED_SERVER_NOT_FOUND,
    SAVED_SESSION_EXPIRED,
    REMOVE_ACTIVE_SERVER,
    INVALID_URL_SCHEME,
    UNABLE_TO_CONNECT,
    CANNOT_REACH_SERVER,
    CONNECTION_REFUSED,
    CONNECTION_TIMEOUT,
    CONNECTION_TIMEOUT_UNAVAILABLE,
    CONNECT_FAILED,
    CONNECTION_REFUSED_SHORT,
    UNKNOWN_CONNECTION,
    SSL_FAILED,
    CERTIFICATE_FAILED,
    NETWORK_ERROR,
    AUTHENTICATION_FAILED,
    QUICK_CONNECT_START_FAILED,
    QUICK_CONNECT_SECRET_MISSING,
    ENDPOINT_UNRESOLVED,
    ENDPOINT_HTTP
}

interface AuthPlatform {
    suspend fun resolveEndpoint(serverUrl: String): Result<ServerEndpoint>

    fun createApi(endpoint: ServerEndpoint): MediaServerApi

    suspend fun text(message: AuthMessage, vararg args: Any): String

    suspend fun connectionErrorMessage(error: Throwable): String?

    suspend fun onSessionEnded(serverId: String?)
}
