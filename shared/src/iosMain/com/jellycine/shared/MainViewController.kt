package com.jellycine.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.repository.AuthMessage
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.IosAuthPlatform
import com.jellycine.data.security.SecureSessionStore
import com.jellycine.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

private val authRepository: AuthRepository by lazy {
    AuthRepository(
        dataStore = DataStoreProvider.getDataStore(),
        secureSessionStore = SecureSessionStore(),
        platform = IosAuthPlatform { message, args -> getString(message.resource(), *args) }
    )
}

fun MainViewController() = ComposeUIViewController {
    JellyCineApp(
        authRepository = authRepository,
        onOpenLanguageSettings = {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
                UIApplication.sharedApplication.openURL(url)
            }
        }
    )
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
