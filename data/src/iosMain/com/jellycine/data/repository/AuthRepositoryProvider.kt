package com.jellycine.data.repository

import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.security.SecureSessionStore

object AuthRepositoryProvider {
    private val instance: AuthRepository by lazy {
        AuthRepository(
            dataStore = DataStoreProvider.getDataStore(),
            secureSessionStore = SecureSessionStore(),
            platform = IosAuthPlatform()
        )
    }

    fun getInstance(): AuthRepository = instance
}
