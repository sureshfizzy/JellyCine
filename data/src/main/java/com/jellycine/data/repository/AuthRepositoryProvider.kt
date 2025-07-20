package com.jellycine.data.repository

import android.content.Context

object AuthRepositoryProvider {
    @Volatile
    private var INSTANCE: AuthRepository? = null
    
    fun getInstance(context: Context): AuthRepository {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: AuthRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
