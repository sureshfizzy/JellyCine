package dev.cinestream.jellycine.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cinestream.jellycine.api.JellyfinApi
import dev.cinestream.jellycine.database.Server
import dev.cinestream.jellycine.database.ServerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import java.lang.Exception

class LoginViewModel(application: Application) : ViewModel() {
    private val jellyfinApi = JellyfinApi.getInstance(application, "")
    private val database = ServerDatabase.getInstance(application).serverDatabaseDao

    private val _navigateToMain = MutableLiveData<Boolean>()
    val navigateToMain: LiveData<Boolean> = _navigateToMain

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                val authenticationResult by jellyfinApi.userApi.authenticateUserByName(
                    data = AuthenticateUserByName(
                        username = username,
                        pw = password
                    )
                )
                _error.value = null
                val serverInfo by jellyfinApi.systemApi.getPublicSystemInfo()
                val server = Server(
                    serverInfo.id!!,
                    serverInfo.serverName!!,
                    jellyfinApi.api.baseUrl!!,
                    authenticationResult.user?.id.toString(),
                    authenticationResult.user?.name!!,
                    authenticationResult.accessToken!!
                )
                insert(server)
                jellyfinApi.apply {
                    api.accessToken = authenticationResult.accessToken
                    userId = authenticationResult.user?.id
                }
                _navigateToMain.value = true
            } catch (e: Exception) {
                Log.e("LoginViewModel", "${e.message}")
                _error.value = e.message
            }
        }
    }

    private suspend fun insert(server: Server) {
        withContext(Dispatchers.IO) {
            database.insert(server)
        }
    }

    fun doneNavigatingToMain() {
        _navigateToMain.value = false
    }
}