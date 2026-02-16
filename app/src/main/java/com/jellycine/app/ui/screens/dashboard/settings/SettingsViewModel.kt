package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.app.preferences.DownloadPreferences
import com.jellycine.data.model.UserDto
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
    val isLoading: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(private val context: Context) : ViewModel() {
    
    private val authRepository = AuthRepositoryProvider.getInstance(context)
    private val mediaRepository = MediaRepository(context)
    private val downloadPreferences = DownloadPreferences(context)
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadDownloadPreferences()
        loadUserData()
    }

    private fun loadDownloadPreferences() {
        _uiState.value = _uiState.value.copy(
            wifiOnlyDownloads = downloadPreferences.isWifiOnlyDownloadsEnabled()
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
    
    suspend fun getUserProfileImageUrl(): String? {
        return try {
            mediaRepository.getUserProfileImageUrl()
        } catch (e: Exception) {
            null
        }
    }
}
