package com.jellycine.app.ui.screens.auth

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.app.R
import com.jellycine.app.ui.screens.dashboard.home.CachedData
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AuthScreenViewModel(application: Application) : AndroidViewModel(application) {
    
    private val authRepository = AuthRepositoryProvider.getInstance(application)
    private val mediaRepository = MediaRepositoryProvider.getInstance(application)
    private var quickConnectPollingJob: Job? = null
    
    private val _uiState = MutableStateFlow(AuthScreenUiState())
    val uiState: StateFlow<AuthScreenUiState> = _uiState.asStateFlow()

    // Authentication state flow
    val isAuthenticated: Flow<Boolean> = authRepository.isAuthenticated
    
    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            serverUrl = url,
            serverErrorMessage = null
        )
    }
    
    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            loginErrorMessage = null
        )
    }
    
    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            loginErrorMessage = null
        )
    }

    fun setLoginError(error: Throwable) {
        _uiState.value = _uiState.value.copy(loginErrorMessage = mapLoginError(error))
    }
    
    fun connectToServer(onSuccess: (serverUrl: String, serverName: String?) -> Unit) {
        val currentState = _uiState.value
        
        if (currentState.serverUrl.isBlank()) {
            _uiState.value = currentState.copy(
                serverErrorMessage = string(R.string.auth_error_enter_server_url)
            )
            return
        }
        
        _uiState.value = currentState.copy(
            isServerLoading = true,
            serverErrorMessage = null
        )
        
        viewModelScope.launch {
            val result = authRepository.testServerConnection(currentState.serverUrl)
            
            result.fold(
                onSuccess = { serverInfo ->
                    _uiState.value = _uiState.value.copy(
                        isServerLoading = false,
                        serverInfo = serverInfo,
                        isServerConnected = true
                    )
                    onSuccess(currentState.serverUrl, serverInfo.serverName)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isServerLoading = false,
                        serverErrorMessage = mapServerError(error)
                    )
                }
            )
        }
    }
    
    fun login(serverUrl: String, onSuccess: () -> Unit) {
        val currentState = _uiState.value
        
        if (currentState.username.isBlank()) {
            _uiState.value = currentState.copy(
                loginErrorMessage = string(R.string.auth_error_enter_username)
            )
            return
        }
        
        _uiState.value = currentState.copy(
            isLoginLoading = true,
            loginErrorMessage = null
        )
        
        viewModelScope.launch {
            val result = authRepository.authenticateUser(
                serverUrl = serverUrl,
                username = currentState.username,
                password = currentState.password
            )
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoginLoading = false
                    )
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoginLoading = false,
                        loginErrorMessage = mapLoginError(error)
                    )
                }
            )
        }
    }

    fun refreshQuickConnectVisibility(serverUrl: String) {
        val normalizedUrl = serverUrl.trim()
        if (normalizedUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(showQuickConnect = false)
            return
        }

        viewModelScope.launch {
            val supported = authRepository.isQuickConnectSupported(normalizedUrl)
            _uiState.value = _uiState.value.copy(
                showQuickConnect = supported,
                quickConnectCode = if (supported) _uiState.value.quickConnectCode else null,
                isQuickConnectLoading = if (supported) _uiState.value.isQuickConnectLoading else false
            )
        }
    }

    fun loginWithQuickConnect(serverUrl: String, onSuccess: () -> Unit) {
        if (!_uiState.value.showQuickConnect) return
        if (_uiState.value.isQuickConnectLoading) return

        val normalizedUrl = serverUrl.trim()
        if (normalizedUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(
                loginErrorMessage = string(R.string.auth_error_missing_server_url_quick_connect)
            )
            return
        }

        quickConnectPollingJob?.cancel()
        quickConnectPollingJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isQuickConnectLoading = true,
                loginErrorMessage = null,
                quickConnectCode = null
            )

            val initiated = authRepository.initiateQuickConnect(normalizedUrl).getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    isQuickConnectLoading = false,
                    quickConnectCode = null,
                    loginErrorMessage = mapLoginError(error)
                )
                return@launch
            }

            var currentSecret = initiated.secret.orEmpty()
            val initialCode = initiated.code.orEmpty()
            if (currentSecret.isBlank() || initialCode.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isQuickConnectLoading = false,
                    quickConnectCode = null,
                    loginErrorMessage = string(R.string.auth_error_quick_connect_start_failed)
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(quickConnectCode = initialCode)

            var pollCount = 0
            while (pollCount < 40) {
                delay(3000L)
                val quickConnectLogin = authRepository.authenticateWithQuickConnect(
                    serverUrl = normalizedUrl,
                    secret = currentSecret
                )
                if (quickConnectLogin.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isQuickConnectLoading = false,
                        quickConnectCode = null
                    )
                    onSuccess()
                    return@launch
                }
                pollCount += 1
            }

            _uiState.value = _uiState.value.copy(
                isQuickConnectLoading = false,
                quickConnectCode = null,
                loginErrorMessage = string(R.string.auth_error_quick_connect_timed_out)
            )
        }
    }

    fun clearServerError() {
        _uiState.value = _uiState.value.copy(serverErrorMessage = null)
    }
    
    fun clearLoginError() {
        _uiState.value = _uiState.value.copy(loginErrorMessage = null)
    }

    fun logout() {
        viewModelScope.launch {
            quickConnectPollingJob?.cancel()
            authRepository.logout()
            mediaRepository.clearPersistedHomeSnapshot()
            CachedData.clearAllCache()
            // Reset UI state
            _uiState.value = AuthScreenUiState()
        }
    }

    private fun mapLoginError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val code = extractHttpCode(message)

        return when (code) {
            400 -> string(R.string.auth_error_login_invalid_request)
            401 -> string(R.string.auth_error_login_invalid_credentials)
            403 -> string(R.string.auth_error_login_access_denied)
            404 -> string(R.string.auth_error_login_service_not_found)
            429 -> string(R.string.auth_error_login_too_many_attempts)
            500, 502, 503, 504 -> string(R.string.auth_error_login_server_error)
            else -> when {
                message.equals("401") -> string(R.string.auth_error_login_invalid_credentials)
                message.contains("authentication failed", ignoreCase = true) -> string(R.string.auth_error_login_invalid_credentials)
                message.contains("timeout", ignoreCase = true) -> string(R.string.auth_error_login_timeout)
                message.contains("unable to resolve host", ignoreCase = true) -> string(R.string.auth_error_login_cannot_reach_server)
                message.contains("failed to connect", ignoreCase = true) -> string(R.string.auth_error_login_cannot_connect)
                else -> string(R.string.auth_error_login_generic)
            }
        }
    }

    private fun mapServerError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val code = extractHttpCode(message)

        return when (code) {
            400 -> string(R.string.auth_error_server_invalid_request)
            401, 403 -> string(R.string.auth_error_server_access_rejected)
            404 -> string(R.string.auth_error_server_endpoint_not_found)
            500, 502, 503, 504 -> string(R.string.auth_error_server_unavailable)
            else -> when {
                message.equals("401") -> string(R.string.auth_error_server_access_rejected)
                message.contains("timeout", ignoreCase = true) -> string(R.string.auth_error_server_timeout)
                message.contains("unable to resolve host", ignoreCase = true) -> string(R.string.auth_error_server_cannot_find)
                message.contains("failed to connect", ignoreCase = true) -> string(R.string.auth_error_server_cannot_connect)
                message.isBlank() -> string(R.string.auth_error_server_generic)
                else -> message
            }
        }
    }

    private fun string(@StringRes resId: Int): String {
        return getApplication<Application>().getString(resId)
    }

    private fun extractHttpCode(message: String): Int? {
        val match = """\b([1-5]\d{2})\b""".toRegex().find(message) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }
}

data class AuthScreenUiState(
    // Server connection state
    val serverUrl: String = "",
    val isServerLoading: Boolean = false,
    val isServerConnected: Boolean = false,
    val serverInfo: com.jellycine.data.model.ServerInfo? = null,
    val serverErrorMessage: String? = null,
    
    // Login state
    val username: String = "",
    val password: String = "",
    val isLoginLoading: Boolean = false,
    val loginErrorMessage: String? = null,
    val showQuickConnect: Boolean = true,
    val isQuickConnectLoading: Boolean = false,
    val quickConnectCode: String? = null
)
