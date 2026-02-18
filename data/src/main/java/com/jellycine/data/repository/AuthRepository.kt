package com.jellycine.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.model.AuthenticationRequest
import com.jellycine.data.model.AuthenticationResult
import com.jellycine.data.model.ServerInfo
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.preferences.NetworkPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AuthRepository(private val context: Context) {

    private val dataStore: DataStore<Preferences> = DataStoreProvider.getDataStore(context)
    private val networkPreferences = NetworkPreferences(context)

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_NAME_KEY = stringPreferencesKey("server_name")
        private val SERVER_TYPE_KEY = stringPreferencesKey("server_type")
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val IS_AUTHENTICATED_KEY = booleanPreferencesKey("is_authenticated")
    }

    val isAuthenticated: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_AUTHENTICATED_KEY] ?: false
    }

    fun getServerUrl(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[SERVER_URL_KEY]
    }

    fun getServerName(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[SERVER_NAME_KEY]
    }

    fun getServerType(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[SERVER_TYPE_KEY]
    }

    fun getUsername(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[USERNAME_KEY]
    }

    fun getAccessToken(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY]
    }

    suspend fun testServerConnection(serverUrl: String): Result<ServerInfo> {
        return try {
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                return Result.failure(Exception("Invalid URL format. URL must start with http:// or https://"))
            }

            val resolved = NetworkModule.resolveServerEndpoint(
                serverUrl = serverUrl,
                storageDir = context.filesDir,
                timeoutConfig = networkPreferences.getTimeoutConfig()
            ).getOrElse { error ->
                return Result.failure(Exception(error.message ?: "Unable to connect to server"))
            }

            val serverName = resolved.serverInfo.serverName.ifBlank {
                when (resolved.serverType) {
                    NetworkModule.ServerType.EMBY -> "Emby Server"
                    NetworkModule.ServerType.JELLYFIN -> "Jellyfin Server"
                }
            }

            dataStore.edit { preferences ->
                preferences[SERVER_URL_KEY] = resolved.baseUrl
                preferences[SERVER_NAME_KEY] = serverName
                preferences[SERVER_TYPE_KEY] = resolved.serverType.name
            }

            Result.success(resolved.serverInfo)
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Cannot reach server. Please check your internet connection and server URL."))
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("Connection refused. Please check if the server is running and the URL is correct."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Connection timeout. Please check your network connection and try again."))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception("SSL connection failed. Please check if the server supports HTTPS or try HTTP instead."))
        } catch (e: java.security.cert.CertificateException) {
            Result.failure(Exception("Certificate verification failed. The server's SSL certificate may be invalid."))
        } catch (e: java.io.IOException) {
            Result.failure(Exception("Network error: ${e.message ?: "Unable to connect to server"}"))
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("Failed to connect", ignoreCase = true) == true ->
                    "Failed to connect to server. Please check the URL and your network connection."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Connection timeout. Server may be slow or unavailable."
                e.message?.contains("refused", ignoreCase = true) == true ->
                    "Connection refused. Please check if the server is running."
                else -> e.message ?: "Unknown connection error"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String
    ): Result<AuthenticationResult> {
        return try {
            val preferences = dataStore.data.first()
            val savedServerUrl = preferences[SERVER_URL_KEY]
            val savedServerType = preferences[SERVER_TYPE_KEY]?.let {
                runCatching { NetworkModule.ServerType.valueOf(it) }.getOrNull()
            }

            val endpoint = if (isSameServer(serverUrl, savedServerUrl) && savedServerUrl != null && savedServerType != null) {
                NetworkModule.ResolvedServerEndpoint(
                    baseUrl = savedServerUrl,
                    serverType = savedServerType,
                    serverInfo = ServerInfo(
                        serverName = preferences[SERVER_NAME_KEY] ?: "",
                        productName = if (savedServerType == NetworkModule.ServerType.EMBY) "Emby" else "Jellyfin"
                    )
                )
            } else {
                NetworkModule.resolveServerEndpoint(
                    serverUrl = serverUrl,
                    storageDir = context.filesDir,
                    timeoutConfig = networkPreferences.getTimeoutConfig()
                ).getOrElse { error ->
                    return Result.failure(Exception(error.message ?: "Unable to resolve server endpoint"))
                }
            }

            val api = NetworkModule.createMediaServerApi(
                baseUrl = endpoint.baseUrl,
                serverType = endpoint.serverType,
                storageDir = context.filesDir,
                timeoutConfig = networkPreferences.getTimeoutConfig()
            )

            val response = api.authenticateByName(AuthenticationRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val authResult = response.body()!!

                dataStore.edit { prefs ->
                    prefs[SERVER_URL_KEY] = endpoint.baseUrl
                    prefs[SERVER_TYPE_KEY] = endpoint.serverType.name
                    prefs[ACCESS_TOKEN_KEY] = authResult.accessToken
                    prefs[USER_ID_KEY] = authResult.user.id
                    prefs[USERNAME_KEY] = authResult.user.name
                    prefs[IS_AUTHENTICATED_KEY] = true
                }

                Result.success(authResult)
            } else {
                Result.failure(Exception("Authentication failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = ""
            preferences[USER_ID_KEY] = ""
            preferences[USERNAME_KEY] = ""
            preferences[IS_AUTHENTICATED_KEY] = false
        }
    }

    private fun isSameServer(inputUrl: String, savedUrl: String?): Boolean {
        if (savedUrl.isNullOrBlank()) return false

        val normalizedInput = inputUrl.trimEnd('/')
        val normalizedSaved = savedUrl.trimEnd('/')
        val normalizedSavedWithoutEmby = normalizedSaved.removeSuffix("/emby")

        return normalizedInput.equals(normalizedSaved, ignoreCase = true) ||
            normalizedInput.equals(normalizedSavedWithoutEmby, ignoreCase = true)
    }
}
