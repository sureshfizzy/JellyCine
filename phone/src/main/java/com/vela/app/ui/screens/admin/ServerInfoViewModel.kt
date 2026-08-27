package com.vela.app.ui.screens.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.data.model.ActivityLogEntry
import com.vela.data.model.AdminSessionInfo
import com.vela.data.model.SystemInfoFull
import com.vela.data.network.HttpStatusException
import com.vela.data.repository.MediaRepository
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

    fun retry() {
        loadSystemInfo()
        sessionsPollingJob?.cancel()
        activityPollingJob?.cancel()
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
                        error = mapErrorMessage(e)
                    )
                }
        }
    }

    suspend fun getItemImageUrl(itemId: String, seriesId: String? = null, type: String? = null): String? {
        val targetId = if (type.equals("Episode", ignoreCase = true) && !seriesId.isNullOrBlank()) seriesId else itemId
        return mediaRepository.getImageUrlString(
            itemId = targetId,
            imageType = "Primary",
            width = 280,
            quality = 90,
            enableImageEnhancers = false
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
                                    error = mapErrorMessage(e)
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
                                    error = mapErrorMessage(e)
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

    private fun mapErrorMessage(e: Throwable): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            e is HttpStatusException -> when (e.statusCode) {
                401, 403 -> "Access denied. Admin privileges required."
                404 -> "Server endpoint not found. Check server version."
                500, 502, 503 -> "Server error. Try again later."
                else -> "Server returned error ${e.statusCode}"
            }
            msg.contains("unable to resolve host") || msg.contains("no address associated") ->
                "No internet connection"
            msg.contains("timeout") || msg.contains("timed out") ->
                "Connection timed out. Server may be unreachable."
            msg.contains("failed to connect") || msg.contains("connection refused") ->
                "Cannot reach server. Check if it's running."
            msg.contains("network") || msg.contains("socket") ->
                "Network error. Check your connection."
            msg.contains("ssl") || msg.contains("certificate") ->
                "Secure connection failed. Check server certificate."
            else -> "Something went wrong. Please try again."
        }
    }
}