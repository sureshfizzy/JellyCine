package com.jellycine.data.repository

import android.content.Context
import com.jellycine.data.R
import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.network.ServerEndpoint
import com.jellycine.data.preferences.NetworkPreferences
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

class AndroidAuthPlatform(private val context: Context) : AuthPlatform {

    private val networkPreferences = NetworkPreferences(context)
    private val seerrRepository = SeerrRepository(context)

    override suspend fun resolveEndpoint(serverUrl: String): Result<ServerEndpoint> {
        return NetworkModule.serverEndpoint(
            context = context,
            serverUrl = serverUrl,
            storageDir = context.filesDir,
            timeoutConfig = networkPreferences.getTimeoutConfig()
        )
    }

    override fun createApi(endpoint: ServerEndpoint): MediaServerApi {
        return NetworkModule.createMediaServerApi(
            baseUrl = endpoint.baseUrl,
            serverType = endpoint.serverType,
            storageDir = context.filesDir,
            timeoutConfig = networkPreferences.getTimeoutConfig()
        )
    }

    override fun text(message: AuthMessage, vararg args: Any): String {
        return context.getString(stringRes(message), *args)
    }

    override fun connectionErrorMessage(error: Throwable): String? {
        return when (error) {
            is UnknownHostException -> text(AuthMessage.CANNOT_REACH_SERVER)
            is ConnectException -> text(AuthMessage.CONNECTION_REFUSED)
            is SocketTimeoutException -> text(AuthMessage.CONNECTION_TIMEOUT)
            is SSLException -> text(AuthMessage.SSL_FAILED)
            is CertificateException -> text(AuthMessage.CERTIFICATE_FAILED)
            is IOException -> text(
                AuthMessage.NETWORK_ERROR,
                error.message ?: text(AuthMessage.UNABLE_TO_CONNECT)
            )
            else -> null
        }
    }

    override suspend fun onSessionEnded(serverId: String?) {
        seerrRepository.disconnect(serverId)
    }

    private fun stringRes(message: AuthMessage): Int = when (message) {
        AuthMessage.INVALID_SERVER_ID -> R.string.auth_error_invalid_server_id
        AuthMessage.SAVED_SERVER_NOT_FOUND -> R.string.auth_error_saved_server_not_found
        AuthMessage.SAVED_SESSION_EXPIRED -> R.string.auth_error_saved_session_expired
        AuthMessage.REMOVE_ACTIVE_SERVER -> R.string.auth_error_remove_active_server
        AuthMessage.INVALID_URL_SCHEME -> R.string.auth_error_invalid_url_scheme
        AuthMessage.UNABLE_TO_CONNECT -> R.string.auth_error_unable_to_connect
        AuthMessage.CANNOT_REACH_SERVER -> R.string.auth_error_cannot_reach_server
        AuthMessage.CONNECTION_REFUSED -> R.string.auth_error_connection_refused
        AuthMessage.CONNECTION_TIMEOUT -> R.string.auth_error_connection_timeout
        AuthMessage.CONNECTION_TIMEOUT_UNAVAILABLE -> R.string.auth_error_connection_timeout_unavailable
        AuthMessage.CONNECT_FAILED -> R.string.auth_error_connect_failed
        AuthMessage.CONNECTION_REFUSED_SHORT -> R.string.auth_error_connection_refused_short
        AuthMessage.UNKNOWN_CONNECTION -> R.string.auth_error_unknown_connection
        AuthMessage.SSL_FAILED -> R.string.auth_error_ssl_failed
        AuthMessage.CERTIFICATE_FAILED -> R.string.auth_error_certificate_failed
        AuthMessage.NETWORK_ERROR -> R.string.auth_error_network
        AuthMessage.AUTHENTICATION_FAILED -> R.string.auth_error_authentication_failed
        AuthMessage.QUICK_CONNECT_START_FAILED -> R.string.auth_error_quick_connect_start_failed
        AuthMessage.QUICK_CONNECT_SECRET_MISSING -> R.string.auth_error_quick_connect_secret_missing
        AuthMessage.ENDPOINT_UNRESOLVED -> R.string.data_error_server_endpoint_unresolved
        AuthMessage.ENDPOINT_HTTP -> R.string.data_error_server_endpoint_http
    }
}
