package com.jellycine.data.repository

import com.jellycine.data.network.HttpStatusException
import kotlinx.coroutines.flow.first

class AuthFailure(
    val message: String,
    val httpCode: Int?
)

class ServerConnectResult(
    val serverName: String?,
    val failure: AuthFailure?
)

class LoginResult(val failure: AuthFailure?) {
    val isSuccess: Boolean get() = failure == null
}

class QuickConnectStartResult(
    val code: String?,
    val secret: String?,
    val failure: AuthFailure?
)

object AuthFacade {

    private val repository: AuthRepository
        get() = AuthRepositoryProvider.getInstance()

    fun setStringResolver(resolver: (String, String?) -> String) = IosStrings.setResolver(resolver)

    suspend fun isAuthenticated(): Boolean = repository.isAuthenticated.first()

    suspend fun getActiveSession(): ActiveSessionSnapshot = repository.observeActiveSession().first()

    suspend fun testServerConnection(serverUrl: String): ServerConnectResult {
        return repository.testServerConnection(serverUrl).fold(
            onSuccess = { ServerConnectResult(it.serverName, null) },
            onFailure = { ServerConnectResult(null, failureOf(it)) }
        )
    }

    suspend fun authenticateUser(serverUrl: String, username: String, password: String): LoginResult {
        return repository.authenticateUser(serverUrl, username, password).toLoginResult()
    }

    suspend fun isQuickConnectSupported(serverUrl: String): Boolean {
        return repository.isQuickConnectSupported(serverUrl)
    }

    suspend fun initiateQuickConnect(serverUrl: String): QuickConnectStartResult {
        return repository.initiateQuickConnect(serverUrl).fold(
            onSuccess = { QuickConnectStartResult(it.code, it.secret, null) },
            onFailure = { QuickConnectStartResult(null, null, failureOf(it)) }
        )
    }

    suspend fun authenticateWithQuickConnect(serverUrl: String, secret: String): LoginResult {
        return repository.authenticateWithQuickConnect(serverUrl, secret).toLoginResult()
    }

    suspend fun switchServer(serverId: String): LoginResult {
        return repository.switchServer(serverId).toLoginResult()
    }

    suspend fun removeSavedServer(serverId: String): LoginResult {
        return repository.removeSavedServer(serverId).toLoginResult()
    }

    suspend fun logout() = repository.logout()

    private fun <T> Result<T>.toLoginResult(): LoginResult {
        return LoginResult(exceptionOrNull()?.let(::failureOf))
    }

    private fun failureOf(error: Throwable): AuthFailure {
        val message = error.message.orEmpty()
        return AuthFailure(message, (error as? HttpStatusException)?.statusCode)
    }
}
