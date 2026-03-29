package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.app.preferences.Preferences
import com.jellycine.data.model.UserDto
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.app.ui.screens.dashboard.home.CachedData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val user: UserDto? = null,
    val serverName: String? = null,
    val serverUrl: String? = null,
    val username: String? = null,
    val profileImageUrl: String? = null,
    val isAdministrator: Boolean? = null,
    val savedServers: List<AuthRepository.SavedServer> = emptyList(),
    val activeServerId: String? = null,
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
    private val initialSessionSnapshot = authRepository.getActiveSessionSnapshot()
    private var activeUserSessionKey: String? = null

    private val _uiState = MutableStateFlow(initialUiState())
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
        return "${serverUrl?.let(NetworkModule::trimTrailingSlash).orEmpty()}|${username.orEmpty()}"
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
                isAudioTranscodingAllowed = isAudioTranscodingEnabled
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
