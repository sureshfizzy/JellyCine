package com.jellycine.data.repository

import android.content.Context
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.security.SecureSessionStore

fun MediaRepository(context: Context): MediaRepository {
    val appContext = context.applicationContext
    return MediaRepository(
        dataStore = DataStoreProvider.getDataStore(appContext),
        secureSessionStore = SecureSessionStore(appContext),
        platform = AndroidMediaPlatform(appContext)
    )
}

object MediaRepositoryProvider {
    @Volatile
    private var INSTANCE: MediaRepository? = null

    fun getInstance(context: Context): MediaRepository {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: MediaRepository(context).also { INSTANCE = it }
        }
    }
}
