package com.jellycine.app.ui.screens.dashboard.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.app.ui.screens.dashboard.home.CachedData
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.shared.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

data class ServersUiState(
    val servers: List<AuthRepository.SavedServer> = emptyList(),
    val activeServerId: String? = null,
    val reachableIds: Set<String> = emptySet(),
    val isSwitching: Boolean = false,
    val isRemoving: Boolean = false,
    val isConnecting: Boolean = false,
    val connectError: String? = null,
    val actionError: String? = null
) {
    val isBusy: Boolean get() = isSwitching || isRemoving || isConnecting
}

class ServersViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepositoryProvider.getInstance(application)
    private val mediaRepository = MediaRepositoryProvider.getInstance(application)

    private val _uiState = MutableStateFlow(
        ServersUiState(
            servers = authRepository.getActiveSessionSnapshot().savedServers,
            activeServerId = authRepository.getActiveSessionSnapshot().activeServerId
        )
    )
    val uiState: StateFlow<ServersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeActiveSession().collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        servers = snapshot.savedServers,
                        activeServerId = snapshot.activeServerId
                    )
                }
                probeReachability(snapshot.savedServers)
            }
        }
    }

    fun clearConnectError() {
        _uiState.update { it.copy(connectError = null) }
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    fun addServer(
        host: String,
        https: Boolean,
        port: String,
        path: String,
        username: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isBusy) return
        val trimmedHost = host.trim()
        val trimmedUsername = username.trim()
        if (trimmedHost.isBlank()) {
            _uiState.update { it.copy(connectError = string(R.string.auth_error_enter_server_url)) }
            return
        }
        if (trimmedUsername.isBlank()) {
            _uiState.update { it.copy(connectError = string(R.string.auth_error_enter_username)) }
            return
        }

        val serverUrl = composeServerUrl(
            host = trimmedHost,
            https = https,
            port = port,
            path = path
        )
        _uiState.update { it.copy(isConnecting = true, connectError = null) }

        viewModelScope.launch {
            val result = try {
                authRepository.authenticateUser(
                    serverUrl = serverUrl,
                    username = trimmedUsername,
                    password = password
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }

            result.fold(
                onSuccess = {
                    mediaRepository.clearPersistedHomeSnapshot()
                    CachedData.clearAllCache()
                    _uiState.update { it.copy(isConnecting = false, connectError = null) }
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connectError = error.message ?: string(R.string.invalid_credentials)
                        )
                    }
                }
            )
        }
    }

    fun switchServer(serverId: String, onSwitched: () -> Unit) {
        val current = _uiState.value
        if (serverId.isBlank() || current.isBusy) return
        if (current.activeServerId == serverId) {
            onSwitched()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSwitching = true, actionError = null) }
            val result = try {
                authRepository.savedServer()
                authRepository.switchServer(serverId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }

            result.fold(
                onSuccess = {
                    mediaRepository.clearPersistedHomeSnapshot()
                    CachedData.clearAllCache()
                    _uiState.update { it.copy(isSwitching = false) }
                    onSwitched()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSwitching = false,
                            actionError = error.message
                        )
                    }
                }
            )
        }
    }

    fun removeServer(serverId: String) {
        if (serverId.isBlank() || _uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRemoving = true, actionError = null) }
            val result = try {
                authRepository.savedServer()
                authRepository.removeSavedServer(serverId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isRemoving = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRemoving = false,
                            actionError = error.message
                        )
                    }
                }
            )
        }
    }

    private suspend fun probeReachability(servers: List<AuthRepository.SavedServer>) {
        if (servers.isEmpty()) {
            _uiState.update { it.copy(reachableIds = emptySet()) }
            return
        }
        val semaphore = Semaphore(3)
        val reachable = coroutineScope {
            servers.map { server ->
                async {
                    semaphore.withPermit {
                        val ok = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                            authRepository.testServerConnection(server.serverUrl).isSuccess
                        } == true
                        server.id.takeIf { ok }
                    }
                }
            }.awaitAll()
        }.filterNotNull().toSet()
        _uiState.update { it.copy(reachableIds = reachable) }
    }

    private fun string(resId: Int, vararg formatArgs: Any): String {
        return getApplication<Application>().getString(resId, *formatArgs)
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 4_000L
    }
}

internal data class ServerAddressDraft(
    val host: String,
    val https: Boolean,
    val port: String,
    val path: String
)

internal fun defaultPort(https: Boolean): String = if (https) "443" else "8096"

internal fun composeServerUrl(
    host: String,
    https: Boolean,
    port: String,
    path: String
): String {
    val scheme = if (https) "https" else "http"
    val stripped = host.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trim()
    val hostOnly = stripped.substringBefore('/').substringBefore(':').trim()
    val trimmedPort = port.trim()
    val portSuffix = when {
        trimmedPort.isBlank() -> ""
        https && trimmedPort == "443" -> ""
        !https && trimmedPort == "80" -> ""
        else -> ":$trimmedPort"
    }
    val trimmedPath = path.trim().trimEnd('/')
    val pathSuffix = when {
        trimmedPath.isBlank() -> ""
        trimmedPath.startsWith("/") -> trimmedPath
        else -> "/$trimmedPath"
    }
    return "$scheme://$hostOnly$portSuffix$pathSuffix"
}

internal fun parseServerAddressInput(
    raw: String,
    currentHttps: Boolean,
    currentPort: String,
    currentPath: String
): ServerAddressDraft {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return ServerAddressDraft("", currentHttps, currentPort, currentPath)
    }

    val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    if (hasScheme || trimmed.contains('/')) {
        val withScheme = if (hasScheme) trimmed else {
            val scheme = if (currentHttps) "https" else "http"
            "$scheme://$trimmed"
        }
        val uri = Uri.parse(withScheme)
        val https = uri.scheme.equals("https", ignoreCase = true)
        val host = uri.host.orEmpty().ifBlank {
            trimmed.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':')
        }
        val port = when {
            uri.port > 0 -> uri.port.toString()
            else -> defaultPort(https)
        }
        val path = uri.path.orEmpty().trimEnd('/').takeIf { it.isNotBlank() && it != "/" }.orEmpty()
        return ServerAddressDraft(host = host, https = https, port = port, path = path)
    }

    if (':' in trimmed) {
        val host = trimmed.substringBefore(':').trim()
        val maybePort = trimmed.substringAfter(':').substringBefore('/').trim()
        val port = maybePort.takeIf { it.all(Char::isDigit) && it.isNotBlank() } ?: currentPort
        return ServerAddressDraft(host = host, https = currentHttps, port = port, path = currentPath)
    }

    return ServerAddressDraft(host = trimmed, https = currentHttps, port = currentPort, path = currentPath)
}
