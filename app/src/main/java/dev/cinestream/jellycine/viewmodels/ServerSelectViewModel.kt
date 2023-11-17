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

class ServerSelectViewModel(
    val database: ServerDatabaseDao,
    val application: Application,
) : ViewModel() {
    private val _servers = database.getAllServers()
    val servers: LiveData<List<Server>> = _servers

    fun deleteServer(server: Server) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.delete(server.id)
            }
        }
    }
}