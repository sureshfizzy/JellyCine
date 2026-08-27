package com.vela.data.repository

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vela.data.datastore.DataStoreProvider
import com.vela.data.preferences.NetworkPreferences
import com.vela.data.security.SecureSessionStore

class MediaRepository {
    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_TYPE_KEY = stringPreferencesKey("server_type")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val PROVIDER_KEYS = listOf("Imdb", "Tmdb", "Tvdb")
    }

    private val dataStore = DataStoreProvider.getDataStore()
    private val networkPreferences = NetworkPreferences()
    private val secureSessionStore = SecureSessionStore()
}