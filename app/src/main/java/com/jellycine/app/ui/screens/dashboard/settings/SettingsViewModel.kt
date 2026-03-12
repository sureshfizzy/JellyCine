package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.UserDto
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.app.ui.screens.dashboard.home.CachedData
import com.jellycine.app.preferences.Preferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SavedServerUiModel(
    val id: String,
    val serverName: String,
    val serverUrl: String,
    val username: String,
    val isActive: Boolean
)

data class SettingsUiState(
    val user: UserDto? = null,
    val serverName: String? = null,
    val serverUrl: String? = null,
    val username: String? = CachedData.username,
    val profileImageUrl: String? = CachedData.userImageUrl,
    val savedServers: List<SavedServerUiModel> = emptyList(),
    val activeServerId: String? = null,
    val isSwitchingServer: Boolean = false,
    val isRemovingServer: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val requestTimeoutMs: Int = NetworkPreferences.DEFAULT_REQUEST_TIMEOUT_MS,
    val connectionTimeoutMs: Int = NetworkPreferences.DEFAULT_CONNECTION_TIMEOUT_MS,
    val socketTimeoutMs: Int = NetworkPreferences.DEFAULT_SOCKET_TIMEOUT_MS,
    val imageMemoryCacheMb: Int = NetworkPreferences.DEFAULT_IMAGE_MEMORY_CACHE_MB,
    val imageCachingEnabled: Boolean = NetworkPreferences.DEFAULT_IMAGE_CACHING_ENABLED,
    val isLoading: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(
    private val context: Context,
    private val includeProfileData: Boolean = true,
    private val includeLocalSettings: Boolean = true
) : ViewModel() {
    
    private val authRepository = AuthRepositoryProvider.getInstance(context)
    private val mediaRepository = MediaRepository(context)
    private val preferences = Preferences(context)
    private val networkPreferences = NetworkPreferences(context)
    private var activeUserSessionKey: String? = null
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            authRepository.savedServer()
        }
        if (includeLocalSettings) {
            loadPreferences()
            loadNetworkPreferences()
        }
        loadUserData()
    }

    private fun loadPreferences() {
        _uiState.value = _uiState.value.copy(
            wifiOnlyDownloads = preferences.isWifiOnlyDownloadsEnabled()
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
            combine(
                authRepository.getServerName(),
                authRepository.getServerUrl(),
                authRepository.getUsername(),
                authRepository.getSavedServers(),
                authRepository.getActiveServerId()
            ) { serverName, serverUrl, username, savedServers, activeServerId ->
                SessionSnapshot(
                    serverName = serverName,
                    serverUrl = serverUrl,
                    username = username,
                    savedServers = savedServers,
                    activeServerId = activeServerId
                )
            }.distinctUntilChanged()
                .collectLatest { snapshot ->
                    val sessionKey = buildSessionKey(snapshot.serverUrl, snapshot.username)
                    val currentState = _uiState.value
                    val isSameSession = activeUserSessionKey == sessionKey
                    val cachedProfileUrl = if (
                        CachedData.userSessionKey == sessionKey &&
                        CachedData.username == snapshot.username
                    ) {
                        CachedData.userImageUrl
                    } else {
                        null
                    }
                    val activeServerId = snapshot.activeServerId
                        ?: snapshot.savedServers.firstOrNull { savedServer ->
                            NetworkModule.trimTrailingSlash(savedServer.serverUrl)
                                .equals(snapshot.serverUrl?.let(NetworkModule::trimTrailingSlash), ignoreCase = true) &&
                                savedServer.username == snapshot.username
                        }?.id
                    val serverUiModels = snapshot.savedServers.map { savedServer ->
                        SavedServerUiModel(
                            id = savedServer.id,
                            serverName = savedServer.serverName,
                            serverUrl = savedServer.serverUrl,
                            username = savedServer.username,
                            isActive = savedServer.id == activeServerId
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        serverName = snapshot.serverName,
                        serverUrl = snapshot.serverUrl,
                        username = snapshot.username ?: currentState.username ?: CachedData.username,
                        profileImageUrl = when {
                            !cachedProfileUrl.isNullOrBlank() -> cachedProfileUrl
                            isSameSession -> currentState.profileImageUrl
                            else -> null
                        },
                        savedServers = serverUiModels,
                        activeServerId = activeServerId,
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

    private data class SessionSnapshot(
        val serverName: String?,
        val serverUrl: String?,
        val username: String?,
        val savedServers: List<com.jellycine.data.repository.AuthRepository.SavedServer>,
        val activeServerId: String?
    )

    private fun buildSessionKey(serverUrl: String?, username: String?): String {
        return "${serverUrl?.let(NetworkModule::trimTrailingSlash).orEmpty()}|${username.orEmpty()}"
    }

    private suspend fun refreshCurrentUserAndProfile(sessionKey: String, username: String?) {
        val profileUrl = runCatching { mediaRepository.getUserProfileImageUrl() }.getOrNull()
        if (!profileUrl.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(profileImageUrl = profileUrl)
            CachedData.updateUserData(
                name = username ?: CachedData.username,
                imageUrl = profileUrl,
                sessionKey = sessionKey
            )
        }

        val userResult = runCatching { mediaRepository.getCurrentUser() }
            .getOrElse { Result.failure(it) }

        if (userResult.isSuccess) {
            _uiState.value = _uiState.value.copy(
                user = userResult.getOrNull(),
                isLoading = false,
                error = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = userResult.exceptionOrNull()?.message
            )
        }
    }

    fun switchServer(serverId: String, onSwitchComplete: () -> Unit = {}) {
        if (serverId.isBlank()) return
        val currentState = _uiState.value
        if (currentState.isSwitchingServer || currentState.isRemovingServer) return
        if (currentState.activeServerId == serverId) {
            onSwitchComplete()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSwitchingServer = true,
                error = null
            )
            authRepository.savedServer()
            val switchResult = authRepository.switchServer(serverId)
            switchResult.fold(
                onSuccess = {
                    mediaRepository.clearPersistedHomeSnapshot()
                    CachedData.clearAllCache()
                    activeUserSessionKey = null
                    _uiState.value = _uiState.value.copy(
                        isSwitchingServer = false,
                        error = null
                    )
                    onSwitchComplete()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSwitchingServer = false,
                        error = error.message ?: "Failed to switch server"
                    )
                }
            )
        }
    }

    fun removeServer(serverId: String, onRemoveComplete: () -> Unit = {}) {
        if (serverId.isBlank()) return
        val currentState = _uiState.value
        if (currentState.isSwitchingServer || currentState.isRemovingServer) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRemovingServer = true,
                error = null
            )
            authRepository.savedServer()
            val removeResult = authRepository.removeSavedServer(serverId)
            removeResult.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isRemovingServer = false,
                        error = null
                    )
                    onRemoveComplete()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRemovingServer = false,
                        error = error.message ?: "Failed to remove server"
                    )
                }
            )
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.logout()
                mediaRepository.clearPersistedHomeSnapshot()
                CachedData.clearAllCache()
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
}
