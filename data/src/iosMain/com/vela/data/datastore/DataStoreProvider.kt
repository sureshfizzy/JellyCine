package com.vela.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun createDataStore(): DataStore<Preferences> {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    val dataStoreFile = requireNotNull(documentDirectory?.path) + "/vela_auth_prefs.preferences_pb"

    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { dataStoreFile.toPath() }
    )
}

object DataStoreProvider {
    private val dataStore: DataStore<Preferences> by lazy { createDataStore() }

    fun getDataStore(): DataStore<Preferences> {
        return dataStore
    }
}
