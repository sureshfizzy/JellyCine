package com.vela.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vela.data.R
import com.vela.data.datastore.DataStoreProvider
import com.vela.data.model.AuthenticationRequest
import com.vela.data.model.AuthenticationResult
import com.vela.data.model.QuickConnectDto
import com.vela.data.model.QuickConnectResult
import com.vela.data.model.ServerInfo
import com.vela.data.network.ServerEndpoint
import com.vela.data.network.ServerType
import com.vela.data.network.NetworkAccess
import com.vela.data.network.NetworkModule
import com.vela.data.network.RoutableLine
import com.vela.data.network.ServerLineSwitchEvent
import com.vela.data.network.ServerLineSwitchReason
import com.vela.data.network.VelaJson
import com.vela.data.network.canonicalServerUrl
import com.vela.data.network.canonicalServerUrlKey
import com.vela.data.network.hostFromUrl
import com.vela.data.network.isLanHost
import com.vela.data.network.pickPreferredReachableLine
import com.vela.data.network.preferLan
import com.vela.data.network.requestMatchesServerUrl
import com.vela.data.network.sameServerUrl
import com.vela.data.preferences.NetworkPreferences
import com.vela.data.preferences.NetworkTimeoutConfig
import com.vela.data.security.AuthSessionIds
import com.vela.data.security.LEGACY_ACCESS_TOKEN_KEY
import com.vela.data.security.SecureSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AuthRepository(private val context: Context) {

    private val dataStore: DataStore<Preferences> = DataStoreProvider.getDataStore(context)
    private val networkPreferences = NetworkPreferences(context)
    private val secureSessionStore = SecureSessionStore(context)
    private val seerrRepository = SeerrRepository(context)
    private val legacyMigrationMutex = Mutex()
    private val lineRoutingMutex = Mutex()
    private val lastFailoverAtByServer = ConcurrentHashMap<String, Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lineSwitchEventsFlow = MutableSharedFlow<ServerLineSwitchEvent>(extraBufferCapacity = 1)

    @Volatile
    private var cachedSnapshot: ActiveSessionSnapshot? = null

    @Volatile
    private var migrationExecuted = false

    val lineSwitchEvents: SharedFlow<ServerLineSwitchEvent> = lineSwitchEventsFlow.asSharedFlow()

    init {
        observeActiveSession()
            .onEach { cachedSnapshot = it }
            .launchIn(scope)
        startAutoRouting()
    }

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_NAME_KEY = stringPreferencesKey("server_name")
        private val SERVER_TYPE_KEY = stringPreferencesKey("server_type")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val IS_AUTHENTICATED_KEY = booleanPreferencesKey("is_authenticated")
        private val SAVED_SERVERS_KEY = stringPreferencesKey("saved_servers_v1")
        private val ACTIVE_SERVER_ID_KEY = stringPreferencesKey("active_server_id")
        private const val PRIMARY_LINE_ID = "primary"
        private const val LINE_PROBE_TIMEOUT_MS = 4_000L
        private const val NETWORK_ROUTE_DEBOUNCE_MS = 1_000L
        private const val FAILOVER_COOLDOWN_MS = 15_000L
        private const val LINE_NAME_LAN = "LAN"
        private const val LINE_NAME_WAN = "WAN"
        private const val LINE_NAME_PRIMARY = "Primary"
    }

    @Serializable
    data class ServerLine(
        @SerialName("id")
        val id: String,
        @SerialName("name")
        val name: String = "",
        @SerialName("url")
        val url: String
    ) {
        fun isLan(): Boolean = isLanHost(hostFromUrl(url))
    }

    @Serializable
    data class SavedServer(
        @SerialName("id")
        val id: String,
        @SerialName("serverUrl")
        val serverUrl: String,
        @SerialName("serverName")
        val serverName: String,
        @SerialName("serverTypeRaw")
        val serverTypeRaw: String,
        @SerialName("username")
        val username: String,
        @SerialName("userId")
        val userId: String,
        @SerialName("profileImageUrl")
        val profileImageUrl: String? = null,
        @SerialName("lastUsedAt")
        val lastUsedAt: Long,
        @SerialName("lines")
        val lines: List<ServerLine> = emptyList(),
        @SerialName("activeLineId")
        val activeLineId: String? = null,
        @SerialName("serverInstanceId")
        val serverInstanceId: String? = null,
        @SerialName("note")
        val note: String? = null,
        @SerialName("preferStrmOriginalPath")
        val preferStrmOriginalPath: Boolean = true,
        @SerialName("autoRouteEnabled")
        val autoRouteEnabled: Boolean = true
    ) {
        fun resolvedLines(): List<ServerLine> {
            if (lines.isNotEmpty()) {
                return lines.distinctBy { canonicalServerUrlKey(it.url) }
            }
            val url = serverUrl.takeIf { it.isNotBlank() } ?: return emptyList()
            return listOf(
                ServerLine(
                    id = activeLineId?.takeIf { it.isNotBlank() } ?: PRIMARY_LINE_ID,
                    name = "",
                    url = url
                )
            )
        }

        fun activeLine(): ServerLine? {
            val all = resolvedLines()
            return all.firstOrNull { line -> line.id == activeLineId }
                ?: all.firstOrNull { line -> sameServerUrl(line.url, serverUrl) }
                ?: all.firstOrNull()
        }

        fun displayName(): String {
            return note?.trim()?.takeIf { it.isNotBlank() } ?: serverName
        }

        fun groupingKey(): String {
            return serverInstanceId?.takeIf { it.isNotBlank() }
                ?: canonicalServerUrlKey(serverUrl)
        }
    }

    @Serializable
    private data class StoredSavedServer(
        @SerialName("id")
        val id: String,
        @SerialName("serverUrl")
        val serverUrl: String,
        @SerialName("serverName")
        val serverName: String,
        @SerialName("serverTypeRaw")
        val serverTypeRaw: String,
        @SerialName("username")
        val username: String,
        @SerialName("userId")
        val userId: String,
        @SerialName("profileImageUrl")
        val profileImageUrl: String? = null,
        @SerialName("lastUsedAt")
        val lastUsedAt: Long,
        @SerialName("accessToken")
        val accessToken: String? = null,
        @SerialName("lines")
        val lines: List<ServerLine> = emptyList(),
        @SerialName("activeLineId")
        val activeLineId: String? = null,
        @SerialName("serverInstanceId")
        val serverInstanceId: String? = null,
        @SerialName("note")
        val note: String? = null,
        @SerialName("preferStrmOriginalPath")
        val preferStrmOriginalPath: Boolean = true,
        @SerialName("autoRouteEnabled")
        val autoRouteEnabled: Boolean = true
    )

    data class ActiveSessionSnapshot(
        val serverName: String?,
        val serverUrl: String?,
        val serverType: String?,
        val username: String?,
        val savedServers: List<SavedServer>,
        val activeServerId: String?
    )

    private fun defaultServerName(serverType: ServerType): String {
        return when (serverType) {
            ServerType.EMBY -> "Emby Server"
            ServerType.JELLYFIN -> "Jellyfin Server"
            ServerType.UNKNOWN -> "Media Server"
        }
    }

    private fun serverName(
        serverInfo: ServerInfo,
        serverType: ServerType
    ): String {
        return serverInfo.serverName
            ?.takeIf { it.isNotBlank() }
            ?: serverInfo.productName?.takeIf { it.isNotBlank() }
            ?: defaultServerName(serverType)
    }

    private fun buildServerId(serverUrl: String, userId: String): String {
        return AuthSessionIds.buildServerId(serverUrl, userId)
    }

    private fun currentServerId(preferences: Preferences): String? {
        val explicitId = preferences[ACTIVE_SERVER_ID_KEY]?.takeIf { it.isNotBlank() }
        if (explicitId != null) return explicitId

        val serverUrl = preferences[SERVER_URL_KEY]?.takeIf { it.isNotBlank() } ?: return null
        val userId = preferences[USER_ID_KEY]?.takeIf { it.isNotBlank() } ?: return null
        return buildServerId(serverUrl = serverUrl, userId = userId)
    }

    private fun persistedSavedServers(raw: String?): List<StoredSavedServer> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            VelaJson.decodeFromString<List<StoredSavedServer>>(raw)
                ?.filter {
                    it.id.isNotBlank() &&
                        it.serverUrl.isNotBlank() &&
                        it.userId.isNotBlank()
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun savedServers(raw: String?): List<SavedServer> {
        return persistedSavedServers(raw)
            .mapNotNull { storedServer ->
                storedServer.toSavedServerOrNull()
                    ?.takeIf { savedServer -> secureSessionStore.hasToken(savedServer.id) }
            }
    }

    private fun serializeSavedServers(savedServers: List<SavedServer>): String {
        return VelaJson.encodeToString(savedServers)
    }

    private fun upsertSavedServer(
        existing: List<SavedServer>,
        incoming: SavedServer
    ): List<SavedServer> {
        val match = existing.firstOrNull { it.id == incoming.id }
        val merged = if (match == null) {
            incoming
        } else {
            incoming.copy(
                lines = incoming.lines.ifEmpty { match.lines },
                activeLineId = incoming.activeLineId ?: match.activeLineId,
                serverInstanceId = incoming.serverInstanceId ?: match.serverInstanceId,
                profileImageUrl = incoming.profileImageUrl ?: match.profileImageUrl,
                note = incoming.note ?: match.note
            )
        }
        return (existing.filterNot { it.id == merged.id } + merged)
            .sortedByDescending { it.lastUsedAt }
    }

    private fun activeServer(preferences: Preferences): SavedServer? {
        val serverUrl = preferences[SERVER_URL_KEY]?.takeIf { it.isNotBlank() } ?: return null
        val userId = preferences[USER_ID_KEY]?.takeIf { it.isNotBlank() } ?: return null
        val storedServers = savedServers(preferences[SAVED_SERVERS_KEY])
        val serverId = preferences[ACTIVE_SERVER_ID_KEY]?.takeIf { it.isNotBlank() }
            ?: storedServers.firstOrNull { savedServer ->
                savedServer.userId == userId &&
                    savedServer.resolvedLines().any { line -> sameServerUrl(line.url, serverUrl) }
            }?.id
            ?: buildServerId(serverUrl = serverUrl, userId = userId)
        if (!secureSessionStore.hasToken(serverId)) return null
        val existingSavedServer = storedServers.firstOrNull { savedServer -> savedServer.id == serverId }
        val serverTypeRaw = preferences[SERVER_TYPE_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: existingSavedServer?.serverTypeRaw
            ?: ServerType.UNKNOWN.name
        val serverType = runCatching { ServerType.valueOf(serverTypeRaw) }
            .getOrDefault(ServerType.UNKNOWN)
        val serverName = preferences[SERVER_NAME_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: existingSavedServer?.serverName
            ?: defaultServerName(serverType)
        val username = preferences[USERNAME_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: existingSavedServer?.username
            ?: ""

        return SavedServer(
            id = serverId,
            serverUrl = serverUrl,
            serverName = serverName,
            serverTypeRaw = serverTypeRaw,
            username = username,
            userId = userId,
            profileImageUrl = existingSavedServer?.profileImageUrl,
            lastUsedAt = existingSavedServer?.lastUsedAt ?: System.currentTimeMillis(),
            lines = existingSavedServer?.lines.orEmpty(),
            activeLineId = existingSavedServer?.activeLineId,
            serverInstanceId = existingSavedServer?.serverInstanceId,
            note = existingSavedServer?.note,
            preferStrmOriginalPath = existingSavedServer?.preferStrmOriginalPath ?: true,
            autoRouteEnabled = existingSavedServer?.autoRouteEnabled != false
        )
    }

    val isAuthenticated: Flow<Boolean> = dataStore.data.map { preferences ->
        legacyStorageMigrated()
        (preferences[IS_AUTHENTICATED_KEY] ?: false) &&
            secureSessionStore.hasToken(currentServerId(preferences))
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

    fun observeActiveSession(): Flow<ActiveSessionSnapshot> = dataStore.data.map { preferences ->
        legacyStorageMigrated()
        val storedServers = savedServers(preferences[SAVED_SERVERS_KEY])
        val activeServer = activeServer(preferences)
        val currentSavedServers = if (activeServer != null && storedServers.none { it.id == activeServer.id }) {
            upsertSavedServer(storedServers, activeServer)
        } else {
            storedServers.sortedByDescending { it.lastUsedAt }
        }
        val selectedServerId = preferences[ACTIVE_SERVER_ID_KEY]
            ?.takeIf { candidateId ->
                candidateId.isNotBlank() && currentSavedServers.any { savedServer -> savedServer.id == candidateId }
            }
            ?: activeServer?.id
        val resolvedActiveServer = selectedServerId
            ?.let { candidateId ->
                currentSavedServers.firstOrNull { savedServer -> savedServer.id == candidateId }
            }
            ?: activeServer

        ActiveSessionSnapshot(
            serverName = preferences[SERVER_NAME_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: resolvedActiveServer?.serverName,
            serverUrl = preferences[SERVER_URL_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: resolvedActiveServer?.serverUrl,
            serverType = preferences[SERVER_TYPE_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: resolvedActiveServer?.serverTypeRaw,
            username = preferences[USERNAME_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: resolvedActiveServer?.username,
            savedServers = currentSavedServers,
            activeServerId = selectedServerId
        )
    }

    fun getActiveSessionSnapshot(): ActiveSessionSnapshot {
        return cachedSnapshot ?: ActiveSessionSnapshot(
            serverName = null,
            serverUrl = null,
            serverType = null,
            username = null,
            savedServers = emptyList(),
            activeServerId = null
        )
    }

    fun getAccessToken(): Flow<String?> = dataStore.data.map { preferences ->
        legacyStorageMigrated()
        currentServerId(preferences)?.let(secureSessionStore::getToken)
    }

    fun getSavedServers(): Flow<List<SavedServer>> = dataStore.data.map { preferences ->
        legacyStorageMigrated()
        val storedServers = savedServers(preferences[SAVED_SERVERS_KEY])
        val activeServer = activeServer(preferences)
        val currentSavedServers = if (activeServer != null && storedServers.none { it.id == activeServer.id }) {
            upsertSavedServer(storedServers, activeServer)
        } else {
            storedServers.sortedByDescending { it.lastUsedAt }
        }
        currentSavedServers
    }

    fun getActiveServerId(): Flow<String?> = dataStore.data.map { preferences ->
        legacyStorageMigrated()
        activeServer(preferences)?.id
            ?: preferences[ACTIVE_SERVER_ID_KEY]
                ?.takeIf { candidateId ->
                    candidateId.isNotBlank() && savedServers(preferences[SAVED_SERVERS_KEY]).any { it.id == candidateId }
                }
    }

    suspend fun savedServer() {
        legacyStorageMigrated()
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
            return Result.failure(Exception(string(R.string.auth_error_invalid_server_id)))
        }

        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val targetServer = existingServers.firstOrNull { it.id == serverId }
                ?: activeServer(preferences)?.takeIf { it.id == serverId }
                ?: return Result.failure(Exception(string(R.string.auth_error_saved_server_not_found)))
            val accessToken = secureSessionStore.getToken(targetServer.id)
                ?: return Result.failure(Exception(string(R.string.auth_error_saved_session_expired)))

            val switchedServer = targetServer.copy(lastUsedAt = System.currentTimeMillis())

            dataStore.edit { prefs ->
                val latestServers = savedServers(prefs[SAVED_SERVERS_KEY])
                val updatedServers = upsertSavedServer(latestServers, switchedServer)
                prefs[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
                prefs[ACTIVE_SERVER_ID_KEY] = switchedServer.id
                prefs[SERVER_URL_KEY] = switchedServer.serverUrl
                prefs[SERVER_NAME_KEY] = switchedServer.serverName
                prefs[SERVER_TYPE_KEY] = switchedServer.serverTypeRaw
                prefs[LEGACY_ACCESS_TOKEN_KEY] = ""
                prefs[USER_ID_KEY] = switchedServer.userId
                prefs[USERNAME_KEY] = switchedServer.username
                prefs[IS_AUTHENTICATED_KEY] = accessToken.isNotBlank() &&
                    switchedServer.userId.isNotBlank()
            }

            val routedServer = if (
                switchedServer.autoRouteEnabled &&
                switchedServer.resolvedLines().size > 1
            ) {
                autoRouteServer(
                    serverId = switchedServer.id,
                    access = NetworkModule.currentNetworkAccess(context),
                    reason = ServerLineSwitchReason.NETWORK,
                    notify = true
                ).getOrDefault(switchedServer)
            } else {
                switchedServer
            }
            Result.success(routedServer)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeSavedServer(serverId: String): Result<Unit> {
        if (serverId.isBlank()) {
            return Result.failure(Exception(string(R.string.auth_error_invalid_server_id)))
        }

        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val activeServerId = preferences[ACTIVE_SERVER_ID_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: activeServer(preferences)?.id

            val removeServer = existingServers.firstOrNull { it.id == serverId }
                ?: return Result.failure(Exception(string(R.string.auth_error_saved_server_not_found)))

            if (removeServer.id == activeServerId) {
                return Result.failure(
                    Exception(string(R.string.auth_error_remove_active_server))
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
            secureSessionStore.removeToken(removeServer.id)
            seerrRepository.disconnect(removeServer.id)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addServerLine(
        serverId: String,
        url: String,
        name: String? = null
    ): Result<SavedServer> {
        val trimmedUrl = url.trim()
        val trimmedName = name?.trim().orEmpty()
        if (serverId.isBlank()) {
            return Result.failure(Exception(string(R.string.auth_error_invalid_server_id)))
        }
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return Result.failure(Exception(string(R.string.auth_error_invalid_url_scheme)))
        }

        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val target = existingServers.firstOrNull { it.id == serverId }
                ?: return Result.failure(Exception(string(R.string.auth_error_saved_server_not_found)))
            val currentLines = target.resolvedLines()
            if (currentLines.any { sameServerUrl(it.url, trimmedUrl) }) {
                return Result.failure(Exception(string(R.string.auth_error_server_line_duplicate)))
            }

            val endpoint = probeServerEndpoint(trimmedUrl).getOrElse { error ->
                return Result.failure(error)
            }
            val instanceId = endpoint.serverInfo.id?.takeIf { it.isNotBlank() }
            if (
                !target.serverInstanceId.isNullOrBlank() &&
                !instanceId.isNullOrBlank() &&
                target.serverInstanceId != instanceId
            ) {
                return Result.failure(Exception(string(R.string.auth_error_server_line_mismatch)))
            }

            val lineUrl = canonicalServerUrl(endpoint.baseUrl)
            if (currentLines.any { sameServerUrl(it.url, lineUrl) }) {
                return Result.failure(Exception(string(R.string.auth_error_server_line_duplicate)))
            }
            val line = ServerLine(
                id = UUID.randomUUID().toString(),
                name = trimmedName.ifBlank { defaultLineName(lineUrl, currentLines.size + 1) },
                url = lineUrl
            )
            val updated = target.copy(
                lines = currentLines + line,
                serverInstanceId = target.serverInstanceId ?: instanceId,
                lastUsedAt = System.currentTimeMillis()
            )
            persistSavedServer(updated, activate = false)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun switchServerLine(
        serverId: String,
        lineId: String,
        force: Boolean = false,
        probe: Boolean = true
    ): Result<SavedServer> {
        if (serverId.isBlank()) {
            return Result.failure(Exception(string(R.string.auth_error_invalid_server_id)))
        }
        if (lineId.isBlank()) {
            return Result.failure(Exception(string(R.string.auth_error_server_line_not_found)))
        }

        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val target = existingServers.firstOrNull { it.id == serverId }
                ?: return Result.failure(Exception(string(R.string.auth_error_saved_server_not_found)))
            if (!secureSessionStore.hasToken(target.id)) {
                return Result.failure(Exception(string(R.string.auth_error_saved_session_expired)))
            }
            val line = target.resolvedLines().firstOrNull { it.id == lineId }
                ?: return Result.failure(Exception(string(R.string.auth_error_server_line_not_found)))
            val alreadyActive = target.activeLine()?.id == line.id &&
                sameServerUrl(target.serverUrl, line.url)
            if (alreadyActive && (!force || !target.autoRouteEnabled)) {
                return Result.success(target)
            }
            if (!alreadyActive && probe) {
                probeServerEndpoint(line.url).getOrElse { error ->
                    return Result.failure(error)
                }
            }
            val updated = target.copy(
                serverUrl = canonicalServerUrl(line.url),
                lines = target.resolvedLines(),
                activeLineId = line.id,
                autoRouteEnabled = if (force) false else target.autoRouteEnabled,
                lastUsedAt = if (force) System.currentTimeMillis() else target.lastUsedAt
            )
            persistSavedServer(updated, activate = target.id == currentServerId(preferences) ||
                preferences[ACTIVE_SERVER_ID_KEY] == target.id)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeServerLine(
        serverId: String,
        lineId: String
    ): Result<SavedServer> {
        if (serverId.isBlank()) {
            return Result.failure(Exception(string(R.string.auth_error_invalid_server_id)))
        }

        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val target = existingServers.firstOrNull { it.id == serverId }
                ?: return Result.failure(Exception(string(R.string.auth_error_saved_server_not_found)))
            val currentLines = target.resolvedLines()
            if (currentLines.size <= 1) {
                return Result.failure(Exception(string(R.string.auth_error_server_line_remove_last)))
            }
            val remaining = currentLines.filterNot { it.id == lineId }
            if (remaining.size == currentLines.size) {
                return Result.failure(Exception(string(R.string.auth_error_server_line_not_found)))
            }
            val nextActive = remaining.firstOrNull { it.id == target.activeLineId }
                ?: remaining.firstOrNull { sameServerUrl(it.url, target.serverUrl) }
                ?: remaining.first()
            val updated = target.copy(
                serverUrl = canonicalServerUrl(nextActive.url),
                lines = remaining,
                activeLineId = nextActive.id,
                lastUsedAt = System.currentTimeMillis()
            )
            persistSavedServer(
                updated,
                activate = target.id == currentServerId(preferences) ||
                    preferences[ACTIVE_SERVER_ID_KEY] == target.id
            )
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun autoSelectServerLine(serverId: String): Result<SavedServer> {
        return autoRouteServer(
            serverId = serverId,
            access = NetworkModule.currentNetworkAccess(context),
            reason = ServerLineSwitchReason.NETWORK,
            notify = false
        )
    }

    suspend fun setAutoRouteEnabled(serverId: String, enabled: Boolean): Result<SavedServer> {
        if (serverId.isBlank()) {
            return Result.failure(Exception(string(R.string.auth_error_invalid_server_id)))
        }
        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val target = existingServers.firstOrNull { it.id == serverId }
                ?: return Result.failure(Exception(string(R.string.auth_error_saved_server_not_found)))
            val updated = target.copy(autoRouteEnabled = enabled)
            persistSavedServer(
                updated,
                activate = target.id == currentServerId(preferences) ||
                    preferences[ACTIVE_SERVER_ID_KEY] == target.id
            )
            if (enabled) {
                autoRouteServer(
                    serverId = serverId,
                    access = NetworkModule.currentNetworkAccess(context),
                    reason = ServerLineSwitchReason.NETWORK,
                    notify = true
                )
            } else {
                Result.success(updated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSavedServerConfig(
        serverId: String,
        note: String?,
        preferStrmOriginalPath: Boolean,
        serverUrl: String? = null
    ): Result<SavedServer> {
        if (serverId.isBlank()) {
            return Result.failure(Exception(string(R.string.auth_error_invalid_server_id)))
        }

        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
            val target = existingServers.firstOrNull { it.id == serverId }
                ?: return Result.failure(Exception(string(R.string.auth_error_saved_server_not_found)))
            val trimmedNote = note?.trim()?.takeIf { it.isNotBlank() }
            var nextLines = target.resolvedLines()
            var nextActiveLineId = target.activeLineId
            var nextUrl = target.serverUrl

            if (!serverUrl.isNullOrBlank()) {
                val canonical = canonicalServerUrl(serverUrl)
                if (!sameServerUrl(canonical, target.serverUrl)) {
                    val existingLine = nextLines.firstOrNull { line -> sameServerUrl(line.url, canonical) }
                    if (existingLine != null) {
                        probeServerEndpoint(existingLine.url).getOrElse { error ->
                            return Result.failure(error)
                        }
                        nextUrl = canonicalServerUrl(existingLine.url)
                        nextActiveLineId = existingLine.id
                    } else {
                        val endpoint = probeServerEndpoint(canonical).getOrElse { error ->
                            return Result.failure(error)
                        }
                        val instanceId = endpoint.serverInfo.id?.takeIf { it.isNotBlank() }
                        if (
                            !target.serverInstanceId.isNullOrBlank() &&
                            !instanceId.isNullOrBlank() &&
                            target.serverInstanceId != instanceId
                        ) {
                            return Result.failure(Exception(string(R.string.auth_error_server_line_mismatch)))
                        }
                        val lineUrl = canonicalServerUrl(endpoint.baseUrl)
                        val active = nextLines.firstOrNull { line -> line.id == target.activeLineId }
                            ?: nextLines.firstOrNull()
                            ?: return Result.failure(Exception(string(R.string.auth_error_server_line_not_found)))
                        nextLines = nextLines.map { line ->
                            if (line.id == active.id) line.copy(url = lineUrl) else line
                        }
                        nextUrl = lineUrl
                        nextActiveLineId = active.id
                    }
                }
            }

            val updated = target.copy(
                serverUrl = nextUrl,
                lines = nextLines,
                activeLineId = nextActiveLineId,
                note = trimmedNote,
                preferStrmOriginalPath = preferStrmOriginalPath,
                lastUsedAt = System.currentTimeMillis()
            )
            persistSavedServer(
                updated,
                activate = target.id == currentServerId(preferences) ||
                    preferences[ACTIVE_SERVER_ID_KEY] == target.id
            )
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSavedServerProfileImage(
        serverId: String,
        profileImageUrl: String?
    ) {
        if (serverId.isBlank()) return

        legacyStorageMigrated()
        dataStore.edit { prefs ->
            val existingServers = savedServers(prefs[SAVED_SERVERS_KEY])
            val targetServer = existingServers.firstOrNull { savedServer -> savedServer.id == serverId }
                ?: return@edit
            val updatedServers = upsertSavedServer(
                existing = existingServers,
                incoming = targetServer.copy(profileImageUrl = profileImageUrl)
            )
            prefs[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
        }
    }

    suspend fun updateActiveServerProfileImage(profileImageUrl: String?) {
        legacyStorageMigrated()
        val preferences = dataStore.data.first()
        val activeServerId = preferences[ACTIVE_SERVER_ID_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: activeServer(preferences)?.id
            ?: return
        updateSavedServerProfileImage(
            serverId = activeServerId,
            profileImageUrl = profileImageUrl
        )
    }

    suspend fun testServerConnection(serverUrl: String): Result<ServerInfo> {
        legacyStorageMigrated()
        return try {
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                return Result.failure(Exception(string(R.string.auth_error_invalid_url_scheme)))
            }

            val resolved = NetworkModule.serverEndpoint(
                    context = context,
                    serverUrl = serverUrl,
                    storageDir = context.filesDir,
                    timeoutConfig = networkPreferences.getTimeoutConfig()
                ).getOrElse { error ->
                return Result.failure(Exception(error.message ?: string(R.string.auth_error_unable_to_connect)))
            }

            val normalizedServerInfo = resolved.serverInfo.copy(
                serverName = serverName(
                    serverInfo = resolved.serverInfo,
                    serverType = resolved.serverType
                )
            )

            Result.success(normalizedServerInfo)
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception(string(R.string.auth_error_cannot_reach_server)))
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception(string(R.string.auth_error_connection_refused)))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception(string(R.string.auth_error_connection_timeout)))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception(string(R.string.auth_error_ssl_failed)))
        } catch (e: java.security.cert.CertificateException) {
            Result.failure(Exception(string(R.string.auth_error_certificate_failed)))
        } catch (e: java.io.IOException) {
            Result.failure(Exception(string(R.string.auth_error_network, e.message ?: string(R.string.auth_error_unable_to_connect))))
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("Failed to connect", ignoreCase = true) == true ->
                    string(R.string.auth_error_connect_failed)
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    string(R.string.auth_error_connection_timeout_unavailable)
                e.message?.contains("refused", ignoreCase = true) == true ->
                    string(R.string.auth_error_connection_refused_short)
                else -> e.message ?: string(R.string.auth_error_unknown_connection)
            }
            Result.failure(Exception(errorMessage))
        }
    }

    private suspend fun authEndpoint(
        serverUrl: String,
        preferences: Preferences
    ): Result<ServerEndpoint> {
        legacyStorageMigrated()
        val savedServerUrl = preferences[SERVER_URL_KEY]
        val savedServerType = preferences[SERVER_TYPE_KEY]?.let {
            runCatching { ServerType.valueOf(it) }.getOrNull()
        }
        if (isSameServer(serverUrl, savedServerUrl) && savedServerUrl != null && savedServerType != null) {
            return Result.success(
                ServerEndpoint(
                    baseUrl = savedServerUrl,
                    serverType = savedServerType,
                    serverInfo = ServerInfo(
                        serverName = preferences[SERVER_NAME_KEY]
                    )
                )
            )
        }

        return NetworkModule.serverEndpoint(
            context = context,
            serverUrl = serverUrl,
            storageDir = context.filesDir,
            timeoutConfig = networkPreferences.getTimeoutConfig()
        ).fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(Exception(error.message ?: string(R.string.data_error_server_endpoint_unresolved)))
            }
        )
    }

    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String,
        note: String? = null
    ): Result<AuthenticationResult> {
        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val savedServerUrl = preferences[SERVER_URL_KEY]
            val savedServerType = preferences[SERVER_TYPE_KEY]?.let {
                runCatching { ServerType.valueOf(it) }.getOrNull()
            }

            val endpoint = if (isSameServer(serverUrl, savedServerUrl) && savedServerUrl != null && savedServerType != null) {
                ServerEndpoint(
                    baseUrl = savedServerUrl,
                    serverType = savedServerType,
                    serverInfo = ServerInfo(
                        serverName = preferences[SERVER_NAME_KEY] ?: "",
                        productName = when (savedServerType) {
                            ServerType.EMBY -> "Emby"
                            ServerType.JELLYFIN -> "Jellyfin"
                            ServerType.UNKNOWN -> "Media Server"
                        }
                    )
                )
            } else {
                NetworkModule.serverEndpoint(
                    context = context,
                    serverUrl = serverUrl,
                    storageDir = context.filesDir,
                    timeoutConfig = networkPreferences.getTimeoutConfig()
                ).getOrElse { error ->
                    return Result.failure(Exception(error.message ?: string(R.string.data_error_server_endpoint_unresolved)))
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
                val savedServer = buildAuthenticatedServer(
                    existingServers = savedServers(preferences[SAVED_SERVERS_KEY]),
                    endpoint = endpoint,
                    userId = authResult.user.id,
                    username = username,
                    note = note
                )

                secureSessionStore.putToken(savedServer.id, authResult.accessToken)
                try {
                    dataStore.edit { prefs ->
                        val existingServers = savedServers(prefs[SAVED_SERVERS_KEY])
                        val updatedServers = upsertSavedServer(existingServers, savedServer)
                        prefs[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
                        prefs[ACTIVE_SERVER_ID_KEY] = savedServer.id
                        prefs[SERVER_URL_KEY] = endpoint.baseUrl
                        prefs[SERVER_NAME_KEY] = serverName
                        prefs[SERVER_TYPE_KEY] = endpoint.serverType.name
                        prefs[LEGACY_ACCESS_TOKEN_KEY] = ""
                        prefs[USER_ID_KEY] = authResult.user.id
                        prefs[USERNAME_KEY] = username
                        prefs[IS_AUTHENTICATED_KEY] = true
                    }
                } catch (error: Exception) {
                    secureSessionStore.removeToken(savedServer.id)
                    throw error
                }
                Result.success(authResult)
            } else {
                Result.failure(Exception(string(R.string.auth_error_authentication_failed, response.code())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initiateQuickConnect(serverUrl: String): Result<QuickConnectResult> {
        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val endpoint = authEndpoint(serverUrl, preferences).getOrElse { error ->
                return Result.failure(error)
            }

            val api = NetworkModule.createMediaServerApi(
                baseUrl = endpoint.baseUrl,
                serverType = endpoint.serverType,
                storageDir = context.filesDir,
                timeoutConfig = networkPreferences.getTimeoutConfig()
            )
            val response = api.initiateQuickConnect()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(string(R.string.auth_error_quick_connect_start_failed, response.code())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isQuickConnectSupported(serverUrl: String): Boolean {
        if (serverUrl.isBlank()) return false
        return runCatching {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val endpoint = authEndpoint(serverUrl, preferences).getOrNull()
            endpoint?.serverType != ServerType.EMBY
        }.getOrDefault(true)
    }

    suspend fun authenticateWithQuickConnect(
        serverUrl: String,
        secret: String
    ): Result<AuthenticationResult> {
        if (secret.isBlank()) {
            return Result.failure(Exception(string(R.string.auth_error_quick_connect_secret_missing)))
        }

        return try {
            legacyStorageMigrated()
            val preferences = dataStore.data.first()
            val endpoint = authEndpoint(serverUrl, preferences).getOrElse { error ->
                return Result.failure(error)
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

            val response = api.authenticateWithQuickConnect(QuickConnectDto(secret = secret))
            if (response.isSuccessful && response.body() != null) {
                val authResult = response.body()!!
                val persistedUsername = authResult.user.name.trim().ifBlank { authResult.user.id }
                val savedServer = buildAuthenticatedServer(
                    existingServers = savedServers(preferences[SAVED_SERVERS_KEY]),
                    endpoint = endpoint,
                    userId = authResult.user.id,
                    username = persistedUsername
                )

                secureSessionStore.putToken(savedServer.id, authResult.accessToken)
                try {
                    dataStore.edit { prefs ->
                        val existingServers = savedServers(prefs[SAVED_SERVERS_KEY])
                        val updatedServers = upsertSavedServer(existingServers, savedServer)
                        prefs[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
                        prefs[ACTIVE_SERVER_ID_KEY] = savedServer.id
                        prefs[SERVER_URL_KEY] = endpoint.baseUrl
                        prefs[SERVER_NAME_KEY] = serverName
                        prefs[SERVER_TYPE_KEY] = endpoint.serverType.name
                        prefs[LEGACY_ACCESS_TOKEN_KEY] = ""
                        prefs[USER_ID_KEY] = authResult.user.id
                        prefs[USERNAME_KEY] = persistedUsername
                        prefs[IS_AUTHENTICATED_KEY] = true
                    }
                } catch (error: Exception) {
                    secureSessionStore.removeToken(savedServer.id)
                    throw error
                }
                Result.success(authResult)
            } else {
                Result.failure(Exception(string(R.string.auth_error_authentication_failed, response.code())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        legacyStorageMigrated()
        var loggedOutServerId: String? = null
        dataStore.edit { preferences ->
            val activeServerId = currentServerId(preferences)
            if (activeServerId != null) {
                loggedOutServerId = activeServerId
                val updatedServers = savedServers(preferences[SAVED_SERVERS_KEY])
                    .filterNot { it.id == activeServerId }
                preferences[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
                secureSessionStore.removeToken(activeServerId)
            }
            preferences[LEGACY_ACCESS_TOKEN_KEY] = ""
            preferences[USER_ID_KEY] = ""
            preferences[USERNAME_KEY] = ""
            preferences[SERVER_URL_KEY] = ""
            preferences[SERVER_NAME_KEY] = ""
            preferences[SERVER_TYPE_KEY] = ""
            preferences[ACTIVE_SERVER_ID_KEY] = ""
            preferences[IS_AUTHENTICATED_KEY] = false
        }
        seerrRepository.disconnect(loggedOutServerId)
    }

    private suspend fun legacyStorageMigrated() {
        if (migrationExecuted) return

        legacyMigrationMutex.withLock {
            if (migrationExecuted) return

            val preferences = dataStore.data.first()
            val storedServers = persistedSavedServers(preferences[SAVED_SERVERS_KEY])
            val activeServerId = currentServerId(preferences)
            val legacyAccessToken = preferences[LEGACY_ACCESS_TOKEN_KEY]?.takeIf { it.isNotBlank() }

            storedServers.forEach { storedServer ->
                storedServer.accessToken
                    ?.takeIf { it.isNotBlank() }
                    ?.let { secureSessionStore.putToken(storedServer.id, it) }
            }

            if (activeServerId != null && !legacyAccessToken.isNullOrBlank()) {
                secureSessionStore.putToken(activeServerId, legacyAccessToken)
            }

            val authenticatedServers = storedServers
                .mapNotNull { storedServer ->
                    storedServer.toSavedServerOrNull()
                        ?.takeIf { savedServer -> secureSessionStore.hasToken(savedServer.id) }
                }
                .sortedByDescending { it.lastUsedAt }

            val serializedServers = serializeSavedServers(authenticatedServers)
            if (
                preferences[LEGACY_ACCESS_TOKEN_KEY].orEmpty().isNotBlank() ||
                preferences[SAVED_SERVERS_KEY] != serializedServers
            ) {
                dataStore.edit { prefs ->
                    prefs[LEGACY_ACCESS_TOKEN_KEY] = ""
                    prefs[SAVED_SERVERS_KEY] = serializedServers
                }
            }

            migrationExecuted = true
        }
    }

    private fun StoredSavedServer.toSavedServerOrNull(): SavedServer? {
        if (
            id.isBlank() ||
            serverUrl.isBlank() ||
            userId.isBlank()
        ) {
            return null
        }

        return SavedServer(
            id = id,
            serverUrl = serverUrl,
            serverName = serverName,
            serverTypeRaw = serverTypeRaw,
            username = username,
            userId = userId,
            profileImageUrl = profileImageUrl,
            lastUsedAt = lastUsedAt,
            lines = lines,
            activeLineId = activeLineId,
            serverInstanceId = serverInstanceId,
            note = note,
            preferStrmOriginalPath = preferStrmOriginalPath,
            autoRouteEnabled = autoRouteEnabled
        )
    }

    private suspend fun persistSavedServer(
        savedServer: SavedServer,
        activate: Boolean
    ) {
        dataStore.edit { prefs ->
            val updatedServers = upsertSavedServer(
                existing = savedServers(prefs[SAVED_SERVERS_KEY]),
                incoming = savedServer
            )
            prefs[SAVED_SERVERS_KEY] = serializeSavedServers(updatedServers)
            if (activate) {
                prefs[ACTIVE_SERVER_ID_KEY] = savedServer.id
                prefs[SERVER_URL_KEY] = savedServer.serverUrl
                prefs[SERVER_NAME_KEY] = savedServer.serverName
                prefs[SERVER_TYPE_KEY] = savedServer.serverTypeRaw
                prefs[USER_ID_KEY] = savedServer.userId
                prefs[USERNAME_KEY] = savedServer.username
                prefs[LEGACY_ACCESS_TOKEN_KEY] = ""
                prefs[IS_AUTHENTICATED_KEY] = secureSessionStore.hasToken(savedServer.id) &&
                    savedServer.userId.isNotBlank()
            }
        }
    }

    private fun buildAuthenticatedServer(
        existingServers: List<SavedServer>,
        endpoint: ServerEndpoint,
        userId: String,
        username: String,
        note: String? = null
    ): SavedServer {
        val instanceId = endpoint.serverInfo.id?.takeIf { it.isNotBlank() }
        val baseUrl = canonicalServerUrl(endpoint.baseUrl)
        val matched = findMatchingSavedServer(
            existing = existingServers,
            userId = userId,
            url = baseUrl,
            instanceId = instanceId
        )
        val mergedLines = mergeLines(
            existing = matched?.resolvedLines().orEmpty(),
            incoming = seedLines(baseUrl, endpoint.serverInfo)
        )
        val active = mergedLines.firstOrNull { sameServerUrl(it.url, baseUrl) }
            ?: mergedLines.first()
        return SavedServer(
            id = matched?.id ?: buildServerId(serverUrl = baseUrl, userId = userId),
            serverUrl = baseUrl,
            serverName = serverName(
                serverInfo = endpoint.serverInfo,
                serverType = endpoint.serverType
            ),
            serverTypeRaw = endpoint.serverType.name,
            username = username,
            userId = userId,
            profileImageUrl = matched?.profileImageUrl,
            lastUsedAt = System.currentTimeMillis(),
            lines = mergedLines,
            activeLineId = active.id,
            serverInstanceId = instanceId ?: matched?.serverInstanceId,
            note = note?.trim()?.takeIf { it.isNotBlank() } ?: matched?.note,
            preferStrmOriginalPath = matched?.preferStrmOriginalPath ?: true,
            autoRouteEnabled = matched?.autoRouteEnabled != false
        )
    }

    private fun findMatchingSavedServer(
        existing: List<SavedServer>,
        userId: String,
        url: String,
        instanceId: String?
    ): SavedServer? {
        val sameUser = existing.filter { it.userId == userId }
        if (!instanceId.isNullOrBlank()) {
            sameUser.firstOrNull { it.serverInstanceId == instanceId }?.let { return it }
        }
        return sameUser.firstOrNull { server ->
            sameServerUrl(server.serverUrl, url) ||
                server.resolvedLines().any { line -> sameServerUrl(line.url, url) }
        }
    }

    private fun seedLines(primaryUrl: String, serverInfo: ServerInfo): List<ServerLine> {
        val discovered = linkedMapOf<String, String>()
        val primary = canonicalServerUrl(primaryUrl)
        discovered[canonicalServerUrlKey(primary)] = primary
        listOf(serverInfo.localAddress, serverInfo.wanAddress).forEach { raw ->
            normalizeDiscoveredUrl(raw, primary)?.let { url ->
                discovered.putIfAbsent(canonicalServerUrlKey(url), url)
            }
        }
        return discovered.values.mapIndexed { index, url ->
            ServerLine(
                id = if (index == 0) PRIMARY_LINE_ID else UUID.randomUUID().toString(),
                name = defaultLineName(url, index + 1),
                url = url
            )
        }
    }

    private fun mergeLines(
        existing: List<ServerLine>,
        incoming: List<ServerLine>
    ): List<ServerLine> {
        if (existing.isEmpty()) return incoming
        val merged = existing.toMutableList()
        incoming.forEach { line ->
            if (merged.none { sameServerUrl(it.url, line.url) }) {
                merged += line
            }
        }
        return merged
    }

    private fun normalizeDiscoveredUrl(raw: String?, primaryUrl: String): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val withScheme = when {
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> value
            else -> {
                val scheme = primaryUrl.substringBefore("://").ifBlank { "http" }
                "$scheme://$value"
            }
        }
        return canonicalServerUrl(withScheme).takeIf { it.startsWith("http") }
    }

    private fun defaultLineName(url: String, index: Int): String {
        val host = hostFromUrl(url)
        return when {
            isLanHost(host) -> LINE_NAME_LAN
            host.isNotBlank() -> LINE_NAME_WAN
            else -> LINE_NAME_PRIMARY.takeIf { index == 1 } ?: "Line $index"
        }
    }

    private fun startAutoRouting() {
        NetworkModule.setLineFailureReporter { requestUrl ->
            scope.launch {
                failoverOnLineFailure(requestUrl)
            }
        }
        NetworkModule.observeNetworkAccess(context)
            .distinctUntilChanged()
            .debounce(NETWORK_ROUTE_DEBOUNCE_MS)
            .onEach { access ->
                if (access == NetworkAccess.OFFLINE) return@onEach
                autoRouteEligibleServers(access)
            }
            .launchIn(scope)
    }

    private suspend fun autoRouteEligibleServers(access: NetworkAccess) {
        legacyStorageMigrated()
        val preferences = dataStore.data.first()
        val servers = savedServers(preferences[SAVED_SERVERS_KEY]).filter { server ->
            server.autoRouteEnabled && server.resolvedLines().size > 1
        }
        servers.forEach { server ->
            autoRouteServer(
                serverId = server.id,
                access = access,
                reason = ServerLineSwitchReason.NETWORK,
                notify = true
            )
        }
    }

    private suspend fun autoRouteServer(
        serverId: String,
        access: NetworkAccess,
        reason: ServerLineSwitchReason,
        notify: Boolean,
        excludeLineId: String? = null
    ): Result<SavedServer> {
        if (serverId.isBlank()) {
            return Result.failure(Exception(string(R.string.auth_error_invalid_server_id)))
        }
        if (access == NetworkAccess.OFFLINE) {
            return Result.failure(Exception(string(R.string.auth_error_cannot_reach_server)))
        }

        return try {
            lineRoutingMutex.withLock {
                legacyStorageMigrated()
                val preferences = dataStore.data.first()
                val existingServers = savedServers(preferences[SAVED_SERVERS_KEY])
                val target = existingServers.firstOrNull { it.id == serverId }
                    ?: return Result.failure(Exception(string(R.string.auth_error_saved_server_not_found)))
                val lines = target.resolvedLines()
                if (lines.isEmpty()) {
                    return Result.failure(Exception(string(R.string.auth_error_server_line_not_found)))
                }
                val candidates = if (excludeLineId.isNullOrBlank()) {
                    lines
                } else {
                    lines.filterNot { it.id == excludeLineId }
                }
                if (candidates.isEmpty()) {
                    return Result.failure(Exception(string(R.string.auth_error_server_line_none_reachable)))
                }

                val reachableIds = probeReachableLineIds(candidates)
                val selected = pickPreferredReachableLine(
                    lines = candidates.map { line ->
                        RoutableLine(id = line.id, isLan = line.isLan())
                    },
                    reachableIds = reachableIds,
                    preferLan = preferLan(access),
                    currentId = target.activeLine()?.id
                ) ?: return Result.failure(Exception(string(R.string.auth_error_server_line_none_reachable)))

                val currentId = target.activeLine()?.id
                if (selected.id == currentId && sameServerUrl(target.serverUrl, candidates.first { it.id == selected.id }.url)) {
                    return Result.success(target)
                }

                val switched = switchServerLine(
                    serverId = serverId,
                    lineId = selected.id,
                    force = false,
                    probe = false
                )
                if (notify && switched.isSuccess) {
                    val line = candidates.first { it.id == selected.id }
                    lineSwitchEventsFlow.tryEmit(
                        ServerLineSwitchEvent(
                            serverId = serverId,
                            customName = line.name.trim(),
                            isLan = line.isLan(),
                            reason = reason
                        )
                    )
                }
                switched
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun failoverOnLineFailure(requestUrl: String) {
        val snapshot = getActiveSessionSnapshot()
        val target = snapshot.savedServers.firstOrNull { server ->
            server.autoRouteEnabled &&
                server.resolvedLines().size > 1 &&
                requestMatchesServerUrl(requestUrl, server.serverUrl)
        } ?: return

        val now = System.currentTimeMillis()
        val lastFailoverAt = lastFailoverAtByServer[target.id] ?: 0L
        if (now - lastFailoverAt < FAILOVER_COOLDOWN_MS) return
        lastFailoverAtByServer[target.id] = now

        autoRouteServer(
            serverId = target.id,
            access = NetworkModule.currentNetworkAccess(context),
            reason = ServerLineSwitchReason.FAILOVER,
            notify = true,
            excludeLineId = target.activeLine()?.id
        )
    }

    private suspend fun probeReachableLineIds(lines: List<ServerLine>): Set<String> {
        return NetworkModule.withoutLineFailover {
            coroutineScope {
                lines.map { line ->
                    async {
                        val ok = withTimeoutOrNull(LINE_PROBE_TIMEOUT_MS) {
                            probeServerEndpoint(line.url).isSuccess
                        } == true
                        line.id.takeIf { ok }
                    }
                }.awaitAll()
            }.filterNotNull().toSet()
        }
    }

    private suspend fun probeServerEndpoint(serverUrl: String): Result<ServerEndpoint> {
        val current = networkPreferences.getTimeoutConfig()
        val probeTimeout = NetworkTimeoutConfig(
            requestTimeoutMs = minOf(current.requestTimeoutMs, 4_000),
            connectionTimeoutMs = minOf(current.connectionTimeoutMs, 3_000),
            socketTimeoutMs = minOf(current.socketTimeoutMs, 4_000)
        )
        return NetworkModule.withoutLineFailover {
            NetworkModule.serverEndpoint(
                context = context,
                serverUrl = serverUrl,
                storageDir = context.filesDir,
                timeoutConfig = probeTimeout
            ).fold(
                onSuccess = { Result.success(it) },
                onFailure = { error ->
                    Result.failure(
                        Exception(error.message ?: string(R.string.data_error_server_endpoint_unresolved))
                    )
                }
            )
        }
    }


    private fun isSameServer(inputUrl: String, savedUrl: String?): Boolean {
        if (savedUrl.isNullOrBlank()) return false

        val normalizedInput = canonicalServerUrl(inputUrl)
        val normalizedSaved = canonicalServerUrl(savedUrl)
        val normalizedSavedWithoutEmby = normalizedSaved.removeSuffix("/emby")

        return normalizedInput.equals(normalizedSaved, ignoreCase = true) ||
            normalizedInput.equals(normalizedSavedWithoutEmby, ignoreCase = true)
    }

    private fun string(resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)
}