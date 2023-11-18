package dev.cinestream.jellycine.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cinestream.jellycine.database.Server
import dev.cinestream.jellycine.database.ServerDatabaseDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Application
import androidx.lifecycle.MutableLiveData
import dev.cinestream.jellycine.api.JellyfinApi
import java.util.*

class ServerSelectViewModel(
    val database: ServerDatabaseDao,
    val application: Application,
) : ViewModel() {
    private val _servers = database.getAllServers()
    val servers: LiveData<List<Server>> = _servers

    private val _navigateToMain = MutableLiveData<Boolean>()
    val navigateToMain: LiveData<Boolean> = _navigateToMain

    fun deleteServer(server: Server) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.delete(server.id)
            }
        }
    }

    fun connectToServer(server: Server) {
        JellyfinApi.newInstance(application, server.address).apply {
            api.accessToken = server.accessToken
            userId = UUID.fromString(server.userId)
        }
        _navigateToMain.value = true
    }

    fun doneNavigatingToMain() {
        _navigateToMain.value = false
    }

}