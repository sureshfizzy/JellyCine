package com.jellycine.data.repository

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.network.HttpStatusException
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.network.ServerEndpoint
import com.jellycine.data.preferences.NetworkPreferences

class IosAuthPlatform(
    private val messages: suspend (AuthMessage, Array<out Any>) -> String
) : AuthPlatform {

    private val networkPreferences = NetworkPreferences()

    override suspend fun resolveEndpoint(serverUrl: String): Result<ServerEndpoint> {
        val result = NetworkModule.serverEndpoint(
            serverUrl = serverUrl,
            timeoutConfig = networkPreferences.getTimeoutConfig()
        )
        val error = result.exceptionOrNull() ?: return result
        return if (error is HttpStatusException) {
            Result.failure(
                HttpStatusException(error.statusCode, text(AuthMessage.ENDPOINT_HTTP, error.statusCode))
            )
        } else {
            Result.failure(Exception(connectionErrorMessage(error) ?: error.message))
        }
    }

    override fun createApi(endpoint: ServerEndpoint): MediaServerApi {
        return NetworkModule.createMediaServerApi(
            baseUrl = endpoint.baseUrl,
            serverType = endpoint.serverType,
            timeoutConfig = networkPreferences.getTimeoutConfig()
        )
    }

    override suspend fun text(message: AuthMessage, vararg args: Any): String {
        return messages(message, args)
    }

    override suspend fun connectionErrorMessage(error: Throwable): String? {
        val lower = error.message.orEmpty().lowercase()
        return when {
            "timed out" in lower || "code=-1001" in lower ->
                text(AuthMessage.CONNECTION_TIMEOUT)
            "hostname could not be found" in lower || "code=-1003" in lower || "code=-1009" in lower ->
                text(AuthMessage.CANNOT_REACH_SERVER)
            "could not connect" in lower || "code=-1004" in lower ->
                text(AuthMessage.CONNECTION_REFUSED)
            "certificate" in lower || "code=-1202" in lower ->
                text(AuthMessage.CERTIFICATE_FAILED)
            "secure connection" in lower || "code=-1200" in lower ->
                text(AuthMessage.SSL_FAILED)
            else -> null
        }
    }

    override suspend fun onSessionEnded(serverId: String?) = Unit
}
