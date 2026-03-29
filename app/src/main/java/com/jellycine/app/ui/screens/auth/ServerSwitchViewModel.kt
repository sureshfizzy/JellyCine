package com.jellycine.app.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.app.ui.screens.dashboard.home.CachedData
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServerSwitchUiState(
    val isSwitching: Boolean = false,
    val isRemoving: Boolean = false
) {
    val isBusy: Boolean get() = isSwitching || isRemoving
}

class ServerSwitchViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepositoryProvider.getInstance(application)
    private val mediaRepository = MediaRepositoryProvider.getInstance(application)

    private val _uiState = MutableStateFlow(ServerSwitchUiState())
    val uiState: StateFlow<ServerSwitchUiState> = _uiState.asStateFlow()

    fun switchServer(
        serverId: String,
        activeServerId: String?,
        onSwitchComplete: () -> Unit = {},
        onSwitchFailed: (Throwable) -> Unit = {}
    ) {
        if (serverId.isBlank()) return
        if (_uiState.value.isBusy) return
        if (activeServerId == serverId) {
            onSwitchComplete()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSwitching = true
            )

            val switchResult = try {
                authRepository.savedServer()
                authRepository.switchServer(serverId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }

            switchResult.fold(
                onSuccess = {
                    mediaRepository.clearPersistedHomeSnapshot()
                    CachedData.clearAllCache()
                    _uiState.value = ServerSwitchUiState()
                    onSwitchComplete()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSwitching = false
                    )
                    onSwitchFailed(error)
                }
            )
        }
    }

    fun removeServer(
        serverId: String,
        onRemoveComplete: () -> Unit = {},
        onRemoveFailed: (Throwable) -> Unit = {}
    ) {
        if (serverId.isBlank()) return
        if (_uiState.value.isBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRemoving = true
            )

            val removeResult = try {
                authRepository.savedServer()
                authRepository.removeSavedServer(serverId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }

            removeResult.fold(
                onSuccess = {
                    _uiState.value = ServerSwitchUiState()
                    onRemoveComplete()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRemoving = false
                    )
                    onRemoveFailed(error)
                }
            )
        }
    }
}
