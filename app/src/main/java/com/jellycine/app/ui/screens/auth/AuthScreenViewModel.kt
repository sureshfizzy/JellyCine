package com.jellycine.app.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.app.ui.screens.dashboard.home.CachedData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AuthScreenViewModel(application: Application) : AndroidViewModel(application) {
    
    private val authRepository = AuthRepositoryProvider.getInstance(application)
    
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
    
    fun connectToServer(onSuccess: (serverUrl: String, serverName: String?) -> Unit) {
        val currentState = _uiState.value
        
        if (currentState.serverUrl.isBlank()) {
            _uiState.value = currentState.copy(
                serverErrorMessage = "Please enter a server URL"
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
                loginErrorMessage = "Please enter your username"
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
    
    fun clearServerError() {
        _uiState.value = _uiState.value.copy(serverErrorMessage = null)
    }
    
    fun clearLoginError() {
        _uiState.value = _uiState.value.copy(loginErrorMessage = null)
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            CachedData.clearAllCache()
            // Reset UI state
            _uiState.value = AuthScreenUiState()
        }
    }

    private fun mapLoginError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val code = extractHttpCode(message)

        return when (code) {
            400 -> "Invalid request. Please check your username and password."
            401 -> "Incorrect username or password."
            403 -> "Access denied. Your account does not have permission to sign in."
            404 -> "Sign-in service not found. Please check your server URL."
            429 -> "Too many attempts. Please wait a moment and try again."
            500, 502, 503, 504 -> "Server error while signing in. Please try again in a moment."
            else -> when {
                message.equals("401") -> "Incorrect username or password."
                message.contains("authentication failed", ignoreCase = true) -> "Incorrect username or password."
                message.contains("timeout", ignoreCase = true) -> "Login timed out. Please try again."
                message.contains("unable to resolve host", ignoreCase = true) -> "Cannot reach server. Check your URL and network connection."
                message.contains("failed to connect", ignoreCase = true) -> "Cannot connect to the server. Please check if it is online."
                message.isBlank() -> "Login failed. Please try again."
                else -> message
            }
        }
    }

    private fun mapServerError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val code = extractHttpCode(message)

        return when (code) {
            400 -> "Invalid server request. Please check the server URL."
            401, 403 -> "The server rejected the request. Please verify server access settings."
            404 -> "Server endpoint not found. Please verify the server URL."
            500, 502, 503, 504 -> "Server is temporarily unavailable. Please try again."
            else -> when {
                message.equals("401") -> "The server rejected the request. Please verify server access settings."
                message.contains("timeout", ignoreCase = true) -> "Connection timed out. Please try again."
                message.contains("unable to resolve host", ignoreCase = true) -> "Cannot find this server. Check the URL and your network."
                message.contains("failed to connect", ignoreCase = true) -> "Cannot connect to the server. Check if it is online."
                message.isBlank() -> "Connection failed. Please try again."
                else -> message
            }
        }
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
    val loginErrorMessage: String? = null
)
