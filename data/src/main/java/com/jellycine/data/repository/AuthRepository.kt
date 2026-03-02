package com.jellycine.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private val gson = Gson()
    private val savedServerListType = object : TypeToken<List<SavedServer>>() {}.type

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_NAME_KEY = stringPreferencesKey("server_name")
        private val SERVER_TYPE_KEY = stringPreferencesKey("server_type")
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val IS_AUTHENTICATED_KEY = booleanPreferencesKey("is_authenticated")
        private val SAVED_SERVERS_KEY = stringPreferencesKey("saved_servers_v1")
        private val ACTIVE_SERVER_ID_KEY = stringPreferencesKey("active_server_id")
    }

    data class SavedServer(
        val id: String,
        val serverUrl: String,
        val serverName: String,
        val serverTypeRaw: String,
        val username: String,
        val userId: String,
        val accessToken: String,
        val lastUsedAt: Long
    )

    private fun defaultServerName(serverType: NetworkModule.ServerType): String {
        return when (serverType) {
            NetworkModule.ServerType.EMBY -> "Emby Server"
            NetworkModule.ServerType.JELLYFIN -> "Jellyfin Server"
            NetworkModule.ServerType.UNKNOWN -> "Media Server"
        }
    }

    private fun serverName(
        serverInfo: ServerInfo,
        serverType: NetworkModule.ServerType
    ): String {
        return serverInfo.serverName
            ?.takeIf { it.isNotBlank() }
            ?: serverInfo.productName?.takeIf { it.isNotBlank() }
            ?: defaultServerName(serverType)
    }

    private fun normalizeServerUrlForId(serverUrl: String): String {
        return serverUrl.trim().trimEnd('/').lowercase()
    }

    private fun buildServerId(serverUrl: String, userId: String): String {
        return "${normalizeServerUrlForId(serverUrl)}|${userId.trim()}"
    }

    private fun savedServers(raw: String?): List<SavedServer> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<SavedServer>>(raw, savedServerListType)
                ?.filter {
                    it.id.isNotBlank() &&
                        it.serverUrl.isNotBlank() &&
                        it.userId.isNotBlank() &&
                        it.accessToken.isNotBlank()
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun serializeSavedServers(savedServers: List<SavedServer>): String {
        return gson.toJson(savedServers)
    }

    private fun upsertSavedServer(
        existing: List<SavedServer>,
        incoming: SavedServer
    ): List<SavedServer> {
        val withoutMatch = existing.filterNot { it.id == incoming.id }
        return (withoutMatch + incoming)
            .sortedByDescending { it.lastUsedAt }
    }

    private fun activeServer(preferences: Preferences): SavedServer? {
        val serverUrl = preferences[SERVER_URL_KEY]?.takeIf { it.isNotBlank() } ?: return null
        val userId = preferences[USER_ID_KEY]?.takeIf { it.isNotBlank() } ?: return null
        val accessToken = preferences[ACCESS_TOKEN_KEY]?.takeIf { it.isNotBlank() } ?: return null
        val serverTypeRaw = preferences[SERVER_TYPE_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: NetworkModule.ServerType.UNKNOWN.name
        val serverType = runCatching { NetworkModule.ServerType.valueOf(serverTypeRaw) }
            .getOrDefault(NetworkModule.ServerType.UNKNOWN)
        val serverName = preferences[SERVER_NAME_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: defaultServerName(serverType)
        val username = preferences[USERNAME_KEY].orEmpty()

        return SavedServer(
            id = buildServerId(serverUrl = serverUrl, userId = userId),
            serverUrl = serverUrl,
            serverName = serverName,
            serverTypeRaw = serverTypeRaw,
            username = username,
            userId = userId,
            accessToken = accessToken,
            lastUsedAt = System.currentTimeMillis()
        )
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

    fun getSavedServers(): Flow<List<SavedServer>> = dataStore.data.map { preferences ->
        val storedServers = savedServers(preferences[SAVED_SERVERS_KEY])
        val activeServer = activeServer(preferences)
        val mergedServers = if (activeServer != null && storedServers.none { it.id == activeServer.id }) {
            upsertSavedServer(storedServers, activeServer)
        } else {
            storedServers.sortedByDescending { it.lastUsedAt }
        }
        mergedServers
    }

    fun getActiveServerId(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_SERVER_ID_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: activeServer(preferences)?.id
    }

    suspend fun savedServer() {
        dataStore.edit { preferences ->
            val activeServer = activeServer(preferences) ?: return@edit
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val updatedServers = upsertSavedServer(existingServers, activeServer)
            preferences[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
            if (preferences[ACTIVE_SERVER_ID_KEY].isNullOrBlank()) {
                preferences[ACTIVE_SERVER_ID_KEY] = activeServer.id
            }
        }
    }

    suspend fun switchServer(serverId: String): Result<SavedServer> {
        if (serverId.isBlank()) {
            return Result.failure(Exception("Invalid server id"))
        }

        return try {
            val preferences = dataStore.data.first()
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val targetServer = existingServers.firstOrNull { it.id == serverId }
                ?: activeServer(preferences)?.takeIf { it.id == serverId }
                ?: return Result.failure(Exception("Saved server not found"))

            val switchedServer = targetServer.copy(lastUsedAt = System.currentTimeMillis())

            dataStore.edit { prefs ->
                val latestServers = savedServers(prefs[SAVED_SERVERS_KEY])
                val updatedServers = upsertSavedServer(latestServers, switchedServer)
                prefs[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
                prefs[ACTIVE_SERVER_ID_KEY] = switchedServer.id
                prefs[SERVER_URL_KEY] = switchedServer.serverUrl
                prefs[SERVER_NAME_KEY] = switchedServer.serverName
                prefs[SERVER_TYPE_KEY] = switchedServer.serverTypeRaw
                prefs[ACCESS_TOKEN_KEY] = switchedServer.accessToken
                prefs[USER_ID_KEY] = switchedServer.userId
                prefs[USERNAME_KEY] = switchedServer.username
                prefs[IS_AUTHENTICATED_KEY] = switchedServer.accessToken.isNotBlank() &&
                    switchedServer.userId.isNotBlank()
            }

            Result.success(switchedServer)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeSavedServer(serverId: String): Result<Unit> {
        if (serverId.isBlank()) {
            return Result.failure(Exception("Invalid server id"))
        }

        return try {
            val preferences = dataStore.data.first()
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val activeServerId = preferences[ACTIVE_SERVER_ID_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: activeServer(preferences)?.id

            val removeServer = existingServers.firstOrNull { it.id == serverId }
                ?: return Result.failure(Exception("Saved server not found"))

            if (removeServer.id == activeServerId) {
                return Result.failure(
                    Exception("Switch to another server before removing the active one.")
                )
            }

            val updatedServers = existingServers
                .filterNot { it.id == removeServer.id }
                .sortedByDescending { it.lastUsedAt }

            dataStore.edit { prefs ->
                prefs[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
                if (prefs[ACTIVE_SERVER_ID_KEY] == removeServer.id) {
                    prefs[ACTIVE_SERVER_ID_KEY] = ""
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

            val normalizedServerInfo = resolved.serverInfo.copy(
                serverName = serverName(
                    serverInfo = resolved.serverInfo,
                    serverType = resolved.serverType
                )
            )

            Result.success(normalizedServerInfo)
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
                        productName = when (savedServerType) {
                            NetworkModule.ServerType.EMBY -> "Emby"
                            NetworkModule.ServerType.JELLYFIN -> "Jellyfin"
                            NetworkModule.ServerType.UNKNOWN -> "Media Server"
                        }
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

            val serverName = serverName(
                serverInfo = endpoint.serverInfo,
                serverType = endpoint.serverType
            )
            val api = NetworkModule.createMediaServerApi(
                baseUrl = endpoint.baseUrl,
                serverType = endpoint.serverType,
                storageDir = context.filesDir,
                timeoutConfig = networkPreferences.getTimeoutConfig()
            )

            val response = api.authenticateByName(AuthenticationRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val authResult = response.body()!!
                val savedServer = SavedServer(
                    id = buildServerId(serverUrl = endpoint.baseUrl, userId = authResult.user.id),
                    serverUrl = endpoint.baseUrl,
                    serverName = serverName,
                    serverTypeRaw = endpoint.serverType.name,
                    username = username,
                    userId = authResult.user.id,
                    accessToken = authResult.accessToken,
                    lastUsedAt = System.currentTimeMillis()
                )

                dataStore.edit { prefs ->
                    val existingServers = savedServers(prefs[SAVED_SERVERS_KEY])
                    val updatedServers = upsertSavedServer(existingServers, savedServer)
                    prefs[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
                    prefs[ACTIVE_SERVER_ID_KEY] = savedServer.id
                    prefs[SERVER_URL_KEY] = endpoint.baseUrl
                    prefs[SERVER_NAME_KEY] = serverName
                    prefs[SERVER_TYPE_KEY] = endpoint.serverType.name
                    prefs[ACCESS_TOKEN_KEY] = authResult.accessToken
                    prefs[USER_ID_KEY] = authResult.user.id
                    prefs[USERNAME_KEY] = username
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
