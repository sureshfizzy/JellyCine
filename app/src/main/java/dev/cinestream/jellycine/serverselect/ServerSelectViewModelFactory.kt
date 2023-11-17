package dev.cinestream.jellycine.serverselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.cinestream.jellycine.database.ServerDatabaseDao
import java.lang.IllegalArgumentException

class ServerSelectViewModelFactory(
    private val dataSource: ServerDatabaseDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(ServerSelectViewModel::class.java)) {
            return ServerSelectViewModel(dataSource) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}