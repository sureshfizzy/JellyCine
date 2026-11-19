package com.jellycine.ui.screens.dashboard.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.preferences.Preferences
import com.jellycine.data.model.*
import com.jellycine.data.network.trimTrailingSlash
import com.jellycine.data.preferences.NetworkSettingsStore
import com.jellycine.data.repository.*
import com.jellycine.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class SeerrConnectionStatus {
    DISCONNECTED,
    CHECKING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class SeerrUiState(
    val serverUrl: String? = null,
    val serverVersion: String? = null,
    val requestLimits: SeerrUserRequestLimits? = null,
    val status: SeerrConnectionStatus = SeerrConnectionStatus.DISCONNECTED,
    val hasSavedConnection: Boolean = false,
    val message: String? = null
)

data class SeerrRequestedItemsUiState(
    val mediaType: String? = null,
    val items: List<SeerrRequestedItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class SettingsUiState(
    val user: UserDto? = null,
    val serverName: String? = null,
    val serverUrl: String? = null,
    val username: String? = null,
    val profileImageUrl: String? = null,
    val isAdministrator: Boolean? = null,
    val savedServers: List<SavedServer> = emptyList(),
    val activeServerId: String? = null,
    val wifiOnlyDownloads: Boolean = false,
    val forceOffline: Boolean = false,
    val requestTimeoutMs: Int = NetworkSettingsStore.DEFAULT_REQUEST_TIMEOUT_MS,
    val connectionTimeoutMs: Int = NetworkSettingsStore.DEFAULT_CONNECTION_TIMEOUT_MS,
    val socketTimeoutMs: Int = NetworkSettingsStore.DEFAULT_SOCKET_TIMEOUT_MS,
    val imageMemoryCacheMb: Int = NetworkSettingsStore.DEFAULT_IMAGE_MEMORY_CACHE_MB,
    val imageCachingEnabled: Boolean = NetworkSettingsStore.DEFAULT_IMAGE_CACHING_ENABLED,
    val seerr: SeerrUiState = SeerrUiState(),
    val seerrRequestedItems: SeerrRequestedItemsUiState = SeerrRequestedItemsUiState(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Optional Seerr hooks. Platforms with no Seerr support pass `null`, which leaves Seerr disconnected.
 */
interface SettingsSeerrSource {
    fun getSavedConnectionInfo(scopeId: String?): SeerrConnectionInfo?

    suspend fun connect(
        scopeId: String,
        serverUrl: String,
        username: String,
        password: String,
        mediaServerUrl: String
    ): Result<SeerrConnectionInfo>

    suspend fun refreshConnection(scopeId: String): Result<SeerrConnectionInfo>

    fun disconnect(scopeId: String?)

    suspend fun getRequestedItems(scopeId: String, mediaType: String): Result<List<SeerrRequestedItem>>

    suspend fun errorMessage(error: Throwable): String?
}

class RepositorySettingsSeerrSource(
    private val repository: SeerrRepository
) : SettingsSeerrSource {
    override fun getSavedConnectionInfo(scopeId: String?): SeerrConnectionInfo? =
        repository.getSavedConnectionInfo(scopeId)

    override suspend fun connect(
        scopeId: String,
        serverUrl: String,
        username: String,
        password: String,
        mediaServerUrl: String
    ): Result<SeerrConnectionInfo> = repository.connect(
        scopeId = scopeId,
        serverUrl = serverUrl,
        username = username,
        password = password,
        mediaServerUrl = mediaServerUrl
    )

    override suspend fun refreshConnection(scopeId: String): Result<SeerrConnectionInfo> =
        repository.refreshConnection(scopeId)

    override fun disconnect(scopeId: String?) = repository.disconnect(scopeId)

    override suspend fun getRequestedItems(
        scopeId: String,
        mediaType: String
    ): Result<List<SeerrRequestedItem>> = repository.getRequestedItems(
        scopeId = scopeId,
        mediaType = mediaType
    )

    override suspend fun errorMessage(error: Throwable): String? = when (error) {
        is SeerrHostnameAlreadyConfiguredException ->
            getString(Res.string.settings_seerr_hostname_already_configured)
        is SeerrSessionExpiredException ->
            getString(Res.string.settings_seerr_session_expired)
        else -> error.message
    }
}

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val preferences: Preferences,
    private val networkPreferences: NetworkSettingsStore,
    private val seerr: SettingsSeerrSource? = null,
    private val includeProfileData: Boolean = true,
    private val includeLocalSettings: Boolean = true
) : ViewModel() {
    private val initialSessionSnapshot = authRepository.getActiveSessionSnapshot()
    private var activeUserSessionKey: String? = null

    private val _uiState = MutableStateFlow(initialUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        if (includeLocalSettings) {
            loadPreferences()
            loadNetworkPreferences()
        }
        activeUserSessionKey = buildSessionKey(
            initialSessionSnapshot.serverUrl,
            initialSessionSnapshot.username
        )
        loadUserData()
    }

    private fun initialUiState(): SettingsUiState {
        val activeSavedServer = initialSessionSnapshot.savedServers.firstOrNull { savedServer ->
            savedServer.id == initialSessionSnapshot.activeServerId
        }
        return SettingsUiState(
            serverName = initialSessionSnapshot.serverName,
            serverUrl = initialSessionSnapshot.serverUrl,
            username = initialSessionSnapshot.username,
            profileImageUrl = activeSavedServer?.profileImageUrl,
            savedServers = initialSessionSnapshot.savedServers,
            activeServerId = initialSessionSnapshot.activeServerId
        )
    }

    private fun loadPreferences() {
        _uiState.value = _uiState.value.copy(
            wifiOnlyDownloads = preferences.isWifiOnlyDownloadsEnabled(),
            forceOffline = preferences.isForceOfflineEnabled()
        )
    }

    private fun loadNetworkPreferences() {
        val networkConfig = networkPreferences.getTimeoutConfig()
        _uiState.value = _uiState.value.copy(
            requestTimeoutMs = networkConfig.requestTimeoutMs,
            connectionTimeoutMs = networkConfig.connectionTimeoutMs,
            socketTimeoutMs = networkConfig.socketTimeoutMs,
            imageMemoryCacheMb = networkPreferences.getImageMemoryCacheMb(),
            imageCachingEnabled = networkPreferences.isImageCachingEnabled()
        )
    }

    private fun loadUserData() {
        viewModelScope.launch {
            authRepository.observeActiveSession()
                .distinctUntilChanged()
                .collectLatest { snapshot ->
                    val sessionKey = buildSessionKey(snapshot.serverUrl, snapshot.username)
                    val currentState = _uiState.value
                    val isSameSession = activeUserSessionKey == sessionKey
                    val activeSavedServer = snapshot.savedServers.firstOrNull { savedServer ->
                        savedServer.id == snapshot.activeServerId
                    }
                    val availableServers = snapshot.savedServers.map { savedServer ->
                        val isActiveServer = savedServer.id == snapshot.activeServerId
                        val displayProfileImageUrl = if (isActiveServer) {
                            if (isSameSession) {
                                currentState.profileImageUrl ?: savedServer.profileImageUrl
                            } else {
                                savedServer.profileImageUrl
                            }
                        } else {
                            savedServer.profileImageUrl
                        }
                        savedServer.copy(profileImageUrl = displayProfileImageUrl)
                    }

                    _uiState.value = _uiState.value.copy(
                        serverName = snapshot.serverName ?: currentState.serverName,
                        serverUrl = snapshot.serverUrl ?: currentState.serverUrl,
                        username = snapshot.username ?: currentState.username,
                        profileImageUrl = when {
                            isSameSession && !currentState.profileImageUrl.isNullOrBlank() -> currentState.profileImageUrl
                            else -> activeSavedServer?.profileImageUrl
                        },
                        isAdministrator = if (isSameSession) currentState.isAdministrator else null,
                        savedServers = availableServers,
                        activeServerId = snapshot.activeServerId,
                        user = if (includeProfileData && isSameSession) currentState.user else null,
                        isLoading = if (includeProfileData) {
                            !isSameSession || currentState.user == null
                        } else {
                            false
                        },
                        error = null
                    )
                    if (!includeProfileData) {
                        activeUserSessionKey = sessionKey
                        return@collectLatest
                    }

                    if (activeUserSessionKey == sessionKey && _uiState.value.user != null) {
                        return@collectLatest
                    }

                    activeUserSessionKey = sessionKey
                    refreshCurrentUserAndProfile(sessionKey, snapshot.username)
                }
        }
    }

    private fun buildSessionKey(serverUrl: String?, username: String?): String {
        return "${serverUrl?.let(::trimTrailingSlash).orEmpty()}|${username.orEmpty()}"
    }

    private suspend fun refreshCurrentUserAndProfile(sessionKey: String, username: String?) {
        val userResult = try {
            mediaRepository.getCurrentUser()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
        currentCoroutineContext().ensureActive()
        if (activeUserSessionKey != sessionKey) return

        val user = userResult.getOrNull()
        val profileUrl = try {
            mediaRepository.getUserProfileImageUrl(user?.primaryImageTag)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        currentCoroutineContext().ensureActive()
        if (activeUserSessionKey != sessionKey) return

        val latestState = _uiState.value
        val fallbackProfileUrl = latestState.savedServers
            .firstOrNull { savedServer -> savedServer.id == latestState.activeServerId }
            ?.profileImageUrl
            ?: latestState.profileImageUrl
        val updatedProfileUrl = profileUrl ?: fallbackProfileUrl

        if (userResult.isSuccess) {
            val isVideoTranscodingEnabled = user?.policy?.enableVideoPlaybackTranscoding ?: user?.let { true }
            val isAudioTranscodingEnabled = user?.policy?.enableAudioPlaybackTranscoding ?: user?.let { true }
            val isSyncTranscodingEnabled = user?.policy?.enableSyncTranscoding ?: user?.let { true }
            authRepository.updateActiveServerProfileImage(updatedProfileUrl)
            val updatedState = _uiState.value
            _uiState.value = updatedState.copy(
                user = user,
                profileImageUrl = updatedProfileUrl,
                savedServers = updatedState.savedServers.map { savedServer ->
                    if (savedServer.id == updatedState.activeServerId) {
                        savedServer.copy(profileImageUrl = updatedProfileUrl)
                    } else {
                        savedServer
                    }
                },
                isAdministrator = user?.policy?.isAdministrator,
                isLoading = false,
                error = null
            )
            val activeUsername = username ?: _uiState.value.username
            mediaRepository.persistHomeSnapshot(
                username = activeUsername,
                serverName = _uiState.value.serverName,
                serverUrl = _uiState.value.serverUrl,
                profileImageUrl = updatedProfileUrl,
                isAdministrator = user?.policy?.isAdministrator,
                isVideoTranscodingAllowed = isVideoTranscodingEnabled,
                isAudioTranscodingAllowed = isAudioTranscodingEnabled,
                isSyncTranscodingAllowed = isSyncTranscodingEnabled
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = userResult.exceptionOrNull()?.message
            )
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.logout()
                mediaRepository.clearPersistedHomeSnapshot()
                onLogoutComplete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        preferences.setWifiOnlyDownloadsEnabled(enabled)
        _uiState.value = _uiState.value.copy(wifiOnlyDownloads = enabled)
    }

    fun setForceOffline(enabled: Boolean) {
        preferences.setForceOfflineEnabled(enabled)
        _uiState.value = _uiState.value.copy(forceOffline = enabled)
    }

    fun setRequestTimeoutMs(milliseconds: Int) {
        networkPreferences.setRequestTimeoutMs(milliseconds)
        _uiState.value = _uiState.value.copy(
            requestTimeoutMs = networkPreferences.getTimeoutConfig().requestTimeoutMs
        )
    }

    fun setConnectionTimeoutMs(milliseconds: Int) {
        networkPreferences.setConnectionTimeoutMs(milliseconds)
        _uiState.value = _uiState.value.copy(
            connectionTimeoutMs = networkPreferences.getTimeoutConfig().connectionTimeoutMs
        )
    }

    fun setSocketTimeoutMs(milliseconds: Int) {
        networkPreferences.setSocketTimeoutMs(milliseconds)
        _uiState.value = _uiState.value.copy(
            socketTimeoutMs = networkPreferences.getTimeoutConfig().socketTimeoutMs
        )
    }

    fun setImageMemoryCacheMb(megabytes: Int) {
        networkPreferences.setImageMemoryCacheMb(megabytes)
        _uiState.value = _uiState.value.copy(
            imageMemoryCacheMb = networkPreferences.getImageMemoryCacheMb()
        )
    }

    fun setImageCachingEnabled(enabled: Boolean) {
        networkPreferences.setImageCachingEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            imageCachingEnabled = networkPreferences.isImageCachingEnabled()
        )
    }

    fun connectSeerr(
        serverUrl: String,
        username: String,
        password: String,
        onComplete: (Result<SeerrConnectionInfo>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val scopeId = _uiState.value.activeServerId
            if (scopeId.isNullOrBlank()) {
                val result = Result.failure<SeerrConnectionInfo>(
                    Exception("No active JellyCine session is available.")
                )
                setSeerrState(
                    SeerrUiState(
                        serverUrl = serverUrl.trim().ifBlank { null },
                        status = SeerrConnectionStatus.ERROR,
                        hasSavedConnection = false,
                        message = result.exceptionOrNull()?.message
                    )
                )
                onComplete(result)
                return@launch
            }

            val currentConnection = seerr?.getSavedConnectionInfo(scopeId)
            val hasSavedConnection = currentConnection != null
            val displayUrl = serverUrl.trim().ifBlank { currentConnection?.serverUrl }
            _uiState.value = _uiState.value.copy(
                seerr = _uiState.value.seerr.copy(
                    serverUrl = displayUrl,
                    serverVersion = currentConnection?.serverVersion,
                    hasSavedConnection = hasSavedConnection,
                    status = SeerrConnectionStatus.CONNECTING,
                    message = null
                )
            )

            val result = try {
                seerr?.connect(
                    scopeId = scopeId,
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    mediaServerUrl = _uiState.value.serverUrl.orEmpty()
                ) ?: Result.failure(Exception("Seerr is not available on this platform."))
            } catch (error: Exception) {
                Result.failure(error)
            }

            if (_uiState.value.activeServerId != scopeId) {
                onComplete(result)
                return@launch
            }

            result.onSuccess(::setSeerrConnected).onFailure { error ->
                setSeerrState(
                    savedSeerrState(
                        scopeId = scopeId,
                        verifiedStatus = SeerrConnectionStatus.CONNECTED,
                        unverifiedStatus = SeerrConnectionStatus.ERROR,
                        message = error.seerrMessage()
                    ) ?: SeerrUiState(
                        serverUrl = displayUrl,
                        status = SeerrConnectionStatus.ERROR,
                        hasSavedConnection = false,
                        message = error.seerrMessage()
                    )
                )
            }

            onComplete(result)
        }
    }

    fun refreshSeerrConnection() {
        val scopeId = _uiState.value.activeServerId
        val savedConnection = seerr?.getSavedConnectionInfo(scopeId) ?: run {
            setSeerrState()
            return
        }

        setSeerrState(
            savedConnection.toUiState(
                status = SeerrConnectionStatus.CHECKING,
                hasSavedConnection = true,
                message = null
            )
        )

        viewModelScope.launch {
            if (scopeId.isNullOrBlank()) {
                setSeerrState()
                return@launch
            }

            val result = seerr.refreshConnection(scopeId)
            if (_uiState.value.activeServerId != scopeId) {
                return@launch
            }
            result.onSuccess(::setSeerrConnected).onFailure { error ->
                setSeerrState(
                    savedSeerrState(
                        scopeId = scopeId,
                        verifiedStatus = SeerrConnectionStatus.ERROR,
                        message = error.seerrMessage()
                    ) ?: SeerrUiState(
                        status = SeerrConnectionStatus.DISCONNECTED,
                        hasSavedConnection = false,
                        message = null
                    )
                )
            }
        }
    }

    fun disconnectSeerr() {
        seerr?.disconnect(_uiState.value.activeServerId)
        _uiState.value = _uiState.value.copy(
            seerr = SeerrUiState(),
            seerrRequestedItems = SeerrRequestedItemsUiState()
        )
    }

    fun loadSeerrRequestedItems(mediaType: String) {
        val scopeId = _uiState.value.activeServerId?.takeIf { it.isNotBlank() } ?: return
        val normalizedMediaType = if (mediaType.equals("tv", ignoreCase = true)) "tv" else "movie"
        _uiState.value = _uiState.value.copy(
            seerrRequestedItems = SeerrRequestedItemsUiState(
                mediaType = normalizedMediaType,
                isLoading = true
            )
        )

        viewModelScope.launch {
            val result = seerr?.getRequestedItems(
                scopeId = scopeId,
                mediaType = normalizedMediaType
            ) ?: return@launch
            if (
                _uiState.value.activeServerId != scopeId ||
                _uiState.value.seerrRequestedItems.mediaType != normalizedMediaType
            ) {
                return@launch
            }
            result.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    seerrRequestedItems = SeerrRequestedItemsUiState(
                        mediaType = normalizedMediaType,
                        items = items
                    )
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    seerrRequestedItems = SeerrRequestedItemsUiState(
                        mediaType = normalizedMediaType,
                        error = error.seerrMessage()
                    )
                )
            }
        }
    }

    fun clearSeerrRequestedItems() {
        _uiState.value = _uiState.value.copy(
            seerrRequestedItems = SeerrRequestedItemsUiState()
        )
    }

    fun reloadSeerrConnection(scopeId: String? = _uiState.value.activeServerId) {
        val savedConnection = seerr?.getSavedConnectionInfo(scopeId) ?: run {
            setSeerrState()
            return
        }
        val status = if (savedConnection.isVerified) {
            SeerrConnectionStatus.CONNECTED
        } else {
            SeerrConnectionStatus.ERROR
        }
        setSeerrState(
            savedConnection.toUiState(
                status = status,
                hasSavedConnection = true,
                message = null
            )
        )
    }

    private fun setSeerrConnected(connection: SeerrConnectionInfo) {
        setSeerrState(
            connection.toUiState(
                status = SeerrConnectionStatus.CONNECTED,
                hasSavedConnection = true,
                message = null
            )
        )
    }

    private fun setSeerrState(seerrState: SeerrUiState = SeerrUiState()) {
        _uiState.value = _uiState.value.copy(seerr = seerrState)
    }

    private suspend fun Throwable.seerrMessage(): String? = seerr?.errorMessage(this) ?: message

    private fun savedSeerrState(
        scopeId: String?,
        verifiedStatus: SeerrConnectionStatus,
        unverifiedStatus: SeerrConnectionStatus = verifiedStatus,
        message: String?
    ): SeerrUiState? {
        val savedConnection = seerr?.getSavedConnectionInfo(scopeId) ?: return null
        return savedConnection.toUiState(
            status = if (savedConnection.isVerified) verifiedStatus else unverifiedStatus,
            hasSavedConnection = true,
            message = message
        )
    }

    private fun SeerrConnectionInfo.toUiState(
        status: SeerrConnectionStatus,
        hasSavedConnection: Boolean,
        message: String?
    ): SeerrUiState {
        return SeerrUiState(
            serverUrl = serverUrl,
            serverVersion = serverVersion,
            requestLimits = requestLimits,
            status = status,
            hasSavedConnection = hasSavedConnection,
            message = message
        )
    }
}