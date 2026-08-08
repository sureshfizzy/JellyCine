package com.jellycine.app.ui.screens.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.ActivityLogEntry
import com.jellycine.data.model.AdminSessionInfo
import com.jellycine.data.model.SystemInfoFull
import com.jellycine.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ServerInfoUiState(
    val systemInfo: SystemInfoFull? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

data class SessionsUiState(
    val sessions: List<AdminSessionInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class ActivityLogUiState(
    val entries: List<ActivityLogEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class AdminPanelViewModel(context: Context) : ViewModel() {
    private val mediaRepository = MediaRepository(context)

    private val _serverInfoState = MutableStateFlow(ServerInfoUiState())
    val serverInfoState: StateFlow<ServerInfoUiState> = _serverInfoState.asStateFlow()

    private val _sessionsState = MutableStateFlow(SessionsUiState())
    val sessionsState: StateFlow<SessionsUiState> = _sessionsState.asStateFlow()

    private val _activityState = MutableStateFlow(ActivityLogUiState())
    val activityState: StateFlow<ActivityLogUiState> = _activityState.asStateFlow()

    private var sessionsPollingJob: Job? = null
    private var activityPollingJob: Job? = null

    init {
        loadSystemInfo()
        startSessionsPolling()
        startActivityPolling()
    }

    private fun loadSystemInfo() {
        viewModelScope.launch {
            _serverInfoState.value = _serverInfoState.value.copy(isLoading = true, error = null)
            mediaRepository.getSystemInfo()
                .onSuccess { info ->
                    _serverInfoState.value = _serverInfoState.value.copy(
                        systemInfo = info,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _serverInfoState.value = _serverInfoState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load server info"
                    )
                }
        }
    }

    suspend fun getItemImageUrl(itemId: String): String? {
        return mediaRepository.getImageUrlString(
            itemId = itemId,
            imageType = "Primary",
            width = 120,
            quality = 80
        )
    }

    private fun startSessionsPolling() {
        sessionsPollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    mediaRepository.getActiveSessions()
                        .onSuccess { sessions ->
                            _sessionsState.value = SessionsUiState(
                                sessions = sessions,
                                isLoading = false,
                                error = null
                            )
                        }
                        .onFailure { e ->
                            if (_sessionsState.value.sessions.isEmpty()) {
                                _sessionsState.value = SessionsUiState(
                                    isLoading = false,
                                    error = e.message
                                )
                            }
                        }
                } catch (_: Exception) { }
                delay(3_000L)
            }
        }
    }

    private fun startActivityPolling() {
        activityPollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    mediaRepository.getActivityLog(limit = 50)
                        .onSuccess { result ->
                            _activityState.value = ActivityLogUiState(
                                entries = result.items,
                                isLoading = false,
                                error = null
                            )
                        }
                        .onFailure { e ->
                            if (_activityState.value.entries.isEmpty()) {
                                _activityState.value = ActivityLogUiState(
                                    isLoading = false,
                                    error = e.message
                                )
                            }
                        }
                } catch (_: Exception) { }
                delay(5_000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionsPollingJob?.cancel()
        activityPollingJob?.cancel()
    }
}