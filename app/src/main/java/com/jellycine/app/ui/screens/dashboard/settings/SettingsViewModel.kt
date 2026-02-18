package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.app.preferences.DownloadPreferences
import com.jellycine.data.model.UserDto
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.app.ui.screens.dashboard.home.CachedData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val user: UserDto? = null,
    val serverName: String? = null,
    val serverUrl: String? = null,
    val username: String? = null,
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
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                combine(
                    authRepository.getServerName(),
                    authRepository.getServerUrl(),
                    authRepository.getUsername()
                ) { serverName, serverUrl, username ->
                    Triple(serverName, serverUrl, username)
                }.collect { (serverName, serverUrl, username) ->
                    _uiState.value = _uiState.value.copy(
                        serverName = serverName,
                        serverUrl = serverUrl,
                        username = username
                    )
                }

                val userResult = mediaRepository.getCurrentUser()
                if (userResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        user = userResult.getOrNull(),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = userResult.exceptionOrNull()?.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
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
    
    suspend fun getUserProfileImageUrl(): String? {
        return try {
            mediaRepository.getUserProfileImageUrl()
        } catch (e: Exception) {
            null
        }
    }
}
