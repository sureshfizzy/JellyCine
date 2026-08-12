package com.jellycine.data.repository

import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.security.SecureSessionStore

class AuthRepository {

    private val dataStore = DataStoreProvider.getDataStore()
    private val networkPreferences = NetworkPreferences()
    private val secureSessionStore = SecureSessionStore()
}