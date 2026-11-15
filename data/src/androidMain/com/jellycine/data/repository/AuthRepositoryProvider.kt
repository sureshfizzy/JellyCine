package com.jellycine.data.repository

import android.content.Context
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.security.SecureSessionStore

object AuthRepositoryProvider {
    @Volatile
    private var INSTANCE: AuthRepository? = null

    fun getInstance(context: Context): AuthRepository {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: createRepository(context.applicationContext).also { INSTANCE = it }
        }
    }

    private fun createRepository(context: Context): AuthRepository {
        return AuthRepository(
            dataStore = DataStoreProvider.getDataStore(context),
            secureSessionStore = SecureSessionStore(context),
            platform = AndroidAuthPlatform(context)
        )
    }
}
