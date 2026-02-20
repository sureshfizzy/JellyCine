package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.app.preferences.DownloadPreferences
import com.jellycine.data.model.UserDto
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.app.ui.screens.dashboard.home.CachedData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val user: UserDto? = null,
    val serverName: String? = null,
    val serverUrl: String? = null,
    val username: String? = CachedData.username,
    val profileImageUrl: String? = CachedData.userImageUrl,
    val wifiOnlyDownloads: Boolean = true,
    val requestTimeoutMs: Int = NetworkPreferences.DEFAULT_REQUEST_TIMEOUT_MS,
    val connectionTimeoutMs: Int = NetworkPreferences.DEFAULT_CONNECTION_TIMEOUT_MS,
    val socketTimeoutMs: Int = NetworkPreferences.DEFAULT_SOCKET_TIMEOUT_MS,
    val imageMemoryCacheMb: Int = NetworkPreferences.DEFAULT_IMAGE_MEMORY_CACHE_MB,
    val imageCachingEnabled: Boolean = NetworkPreferences.DEFAULT_IMAGE_CACHING_ENABLED,
    val isLoading: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(private val context: Context) : ViewModel() {
    
    private val authRepository = AuthRepositoryProvider.getInstance(context)
    private val mediaRepository = MediaRepository(context)
    private val downloadPreferences = DownloadPreferences(context)
    private val networkPreferences = NetworkPreferences(context)
    private var activeUserSessionKey: String? = null
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadDownloadPreferences()
        loadNetworkPreferences()
        loadUserData()
    }

    private fun loadDownloadPreferences() {
        _uiState.value = _uiState.value.copy(
            wifiOnlyDownloads = downloadPreferences.isWifiOnlyDownloadsEnabled()
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
                authRepository.getUsername()
            ) { serverName, serverUrl, username ->
                Triple(serverName, serverUrl, username)
            }.distinctUntilChanged()
                .collectLatest { (serverName, serverUrl, username) ->
                    val sessionKey = buildSessionKey(serverUrl, username)
                    val currentState = _uiState.value
                    val isSameSession = activeUserSessionKey == sessionKey
                    val cachedProfileUrl = if (
                        CachedData.userSessionKey == sessionKey &&
                        CachedData.username == username
                    ) {
                        CachedData.userImageUrl
                    } else {
                        null
                    }

                    _uiState.value = _uiState.value.copy(
                        serverName = serverName,
                        serverUrl = serverUrl,
                        username = username ?: currentState.username ?: CachedData.username,
                        profileImageUrl = when {
                            !cachedProfileUrl.isNullOrBlank() -> cachedProfileUrl
                            isSameSession -> currentState.profileImageUrl
                            else -> null
                        },
                        user = if (isSameSession) currentState.user else null,
                        isLoading = !isSameSession || currentState.user == null,
                        error = null
                    )

                    if (activeUserSessionKey == sessionKey && _uiState.value.user != null) {
                        return@collectLatest
                    }

                    activeUserSessionKey = sessionKey
                    refreshCurrentUserAndProfile(sessionKey, username)
                }
        }
    }

    private fun buildSessionKey(serverUrl: String?, username: String?): String {
        return "${serverUrl?.trimEnd('/').orEmpty()}|${username.orEmpty()}"
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
        downloadPreferences.setWifiOnlyDownloadsEnabled(enabled)
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
