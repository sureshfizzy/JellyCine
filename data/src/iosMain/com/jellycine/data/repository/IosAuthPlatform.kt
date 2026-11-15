package com.jellycine.data.repository

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.network.HttpStatusException
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.network.ServerEndpoint
import com.jellycine.data.preferences.NetworkPreferences

class IosAuthPlatform : AuthPlatform {

    private val networkPreferences = NetworkPreferences()

    override suspend fun resolveEndpoint(serverUrl: String): Result<ServerEndpoint> {
        return NetworkModule.serverEndpoint(
            serverUrl = serverUrl,
            timeoutConfig = networkPreferences.getTimeoutConfig()
        ).fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                if (error is HttpStatusException) {
                    Result.failure(
                        HttpStatusException(
                            error.statusCode,
                            text(AuthMessage.ENDPOINT_HTTP, error.statusCode)
                        )
                    )
                } else {
                    Result.failure(Exception(connectionErrorMessage(error) ?: error.message))
                }
            }
        )
    }

    override fun createApi(endpoint: ServerEndpoint): MediaServerApi {
        return NetworkModule.createMediaServerApi(
            baseUrl = endpoint.baseUrl,
            serverType = endpoint.serverType,
            timeoutConfig = networkPreferences.getTimeoutConfig()
        )
    }

    override fun text(message: AuthMessage, vararg args: Any): String {
        return IosStrings.resolve(message.resourceName, args.firstOrNull()?.toString())
    }

    override fun connectionErrorMessage(error: Throwable): String? {
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

object IosStrings {
    private var resolver: ((String, String?) -> String)? = null

    fun setResolver(resolver: (String, String?) -> String) {
        this.resolver = resolver
    }

    fun resolve(resourceName: String, argument: String?): String {
        return resolver?.invoke(resourceName, argument) ?: resourceName
    }
}
