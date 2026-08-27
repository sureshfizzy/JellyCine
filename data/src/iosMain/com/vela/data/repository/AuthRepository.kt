package com.vela.data.repository

import com.vela.data.datastore.DataStoreProvider
import com.vela.data.preferences.NetworkPreferences
import com.vela.data.security.SecureSessionStore

class AuthRepository {

    private val dataStore = DataStoreProvider.getDataStore()
    private val networkPreferences = NetworkPreferences()
    private val secureSessionStore = SecureSessionStore()
}