package com.jellycine.data.repository

import android.content.Context

object MediaRepositoryProvider {
    @Volatile
    private var INSTANCE: MediaRepository? = null
    
    fun getInstance(context: Context): MediaRepository {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: MediaRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
