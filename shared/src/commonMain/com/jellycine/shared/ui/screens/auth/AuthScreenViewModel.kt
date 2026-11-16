package com.jellycine.shared.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.ServerInfo
import com.jellycine.data.network.DiscoveredServer
import com.jellycine.data.network.HttpStatusException
import com.jellycine.data.repository.AuthRepository
import com.jellycine.shared.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

class AuthScreenViewModel(
    private val authRepository: AuthRepository,
    private val serverDiscovery: (() -> Flow<DiscoveredServer>)? = null
) : ViewModel() {

    private var quickConnectPollingJob: Job? = null
    private var discoveryJob: Job? = null

    private val _uiState = MutableStateFlow(AuthScreenUiState())
    val uiState: StateFlow<AuthScreenUiState> = _uiState.asStateFlow()

    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    val supportsDiscovery: Boolean = serverDiscovery != null

    init {
        discoverServers()
    }

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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loginErrorMessage = mapLoginError(error))
        }
    }

    fun connectToServer(onSuccess: (serverUrl: String, serverName: String?) -> Unit) {
        val currentState = _uiState.value

        if (currentState.serverUrl.isBlank()) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    serverErrorMessage = string(Res.string.auth_error_enter_server_url)
                )
            }
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
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    loginErrorMessage = string(Res.string.auth_error_enter_username)
                )
            }
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
                    _uiState.value = _uiState.value.copy(isLoginLoading = false)
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
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    loginErrorMessage = string(Res.string.auth_error_missing_server_url_quick_connect)
                )
            }
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

            val currentSecret = initiated.secret.orEmpty()
            val initialCode = initiated.code.orEmpty()
            if (currentSecret.isBlank() || initialCode.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isQuickConnectLoading = false,
                    quickConnectCode = null,
                    loginErrorMessage = string(Res.string.auth_error_quick_connect_start_failed)
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
                loginErrorMessage = string(Res.string.auth_error_quick_connect_timed_out)
            )
        }
    }

    fun cancelQuickConnect() {
        quickConnectPollingJob?.cancel()
        quickConnectPollingJob = null
        _uiState.value = _uiState.value.copy(
            isQuickConnectLoading = false,
            quickConnectCode = null
        )
    }

    fun discoverServers() {
        val discovery = serverDiscovery ?: return
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _isDiscovering.value = true
            _discoveredServers.value = emptyList()
            val servers = mutableListOf<DiscoveredServer>()
            try {
                discovery().collect { server ->
                    servers.add(server)
                    _discoveredServers.value = servers.toList()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                _isDiscovering.value = false
            }
        }
    }

    fun clearLoginError() {
        _uiState.value = _uiState.value.copy(loginErrorMessage = null)
    }

    private suspend fun mapLoginError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val code = (error as? HttpStatusException)?.statusCode ?: extractHttpCode(message)

        return when (code) {
            400 -> string(Res.string.auth_error_login_invalid_request)
            401 -> string(Res.string.auth_error_login_invalid_credentials)
            403 -> string(Res.string.auth_error_login_access_denied)
            404 -> string(Res.string.auth_error_login_service_not_found)
            429 -> string(Res.string.auth_error_login_too_many_attempts)
            500, 502, 503, 504 -> string(Res.string.auth_error_login_server_error)
            else -> when {
                message == "401" -> string(Res.string.auth_error_login_invalid_credentials)
                message.contains("authentication failed", ignoreCase = true) ->
                    string(Res.string.auth_error_login_invalid_credentials)
                message.contains("timeout", ignoreCase = true) -> string(Res.string.auth_error_login_timeout)
                message.contains("unable to resolve host", ignoreCase = true) ->
                    string(Res.string.auth_error_login_cannot_reach_server)
                message.contains("failed to connect", ignoreCase = true) ->
                    string(Res.string.auth_error_login_cannot_connect)
                else -> string(Res.string.auth_error_login_generic)
            }
        }
    }

    private suspend fun mapServerError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val code = (error as? HttpStatusException)?.statusCode ?: extractHttpCode(message)

        return when (code) {
            400 -> string(Res.string.auth_error_server_invalid_request)
            401, 403 -> string(Res.string.auth_error_server_access_rejected)
            404 -> string(Res.string.auth_error_server_endpoint_not_found)
            500, 502, 503, 504 -> string(Res.string.auth_error_server_unavailable)
            else -> when {
                message == "401" -> string(Res.string.auth_error_server_access_rejected)
                message.contains("timeout", ignoreCase = true) -> string(Res.string.auth_error_server_timeout)
                message.contains("unable to resolve host", ignoreCase = true) ->
                    string(Res.string.auth_error_server_cannot_find)
                message.contains("failed to connect", ignoreCase = true) ->
                    string(Res.string.auth_error_server_cannot_connect)
                message.isBlank() -> string(Res.string.auth_error_server_generic)
                else -> message
            }
        }
    }

    private suspend fun string(resource: StringResource): String = getString(resource)

    private fun extractHttpCode(message: String): Int? {
        val match = Regex("""\b([1-5]\d{2})\b""").find(message) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }
}

data class AuthScreenUiState(
    val serverUrl: String = "",
    val isServerLoading: Boolean = false,
    val isServerConnected: Boolean = false,
    val serverInfo: ServerInfo? = null,
    val serverErrorMessage: String? = null,

    val username: String = "",
    val password: String = "",
    val isLoginLoading: Boolean = false,
    val loginErrorMessage: String? = null,
    val showQuickConnect: Boolean = true,
    val isQuickConnectLoading: Boolean = false,
    val quickConnectCode: String? = null
)
