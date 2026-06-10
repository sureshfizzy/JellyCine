package com.jellycine.data.repository

import android.content.Context

object AwardsRepositoryProvider {
    @Volatile
    private var INSTANCE: AwardsRepository? = null

    fun getInstance(context: Context): AwardsRepository {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: AwardsRepository(
                context.applicationContext,
                MediaRepositoryProvider.getInstance(context)
            ).also { INSTANCE = it }
        }
    }
}