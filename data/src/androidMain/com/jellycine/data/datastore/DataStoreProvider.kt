package com.jellycine.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

object DataStoreProvider {
    fun getDataStore(context: Context): DataStore<Preferences> {
        return context.authDataStore
    }
}
