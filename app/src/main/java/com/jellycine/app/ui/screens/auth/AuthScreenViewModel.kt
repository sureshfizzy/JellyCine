package com.jellycine.app.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.repository.AuthRepositoryProvider
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
                        serverErrorMessage = error.message ?: "Connection failed"
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
        
        if (currentState.password.isBlank()) {
            _uiState.value = currentState.copy(
                loginErrorMessage = "Please enter your password"
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
                        loginErrorMessage = error.message ?: "Login failed"
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
            // Reset UI state
            _uiState.value = AuthScreenUiState()
        }
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
