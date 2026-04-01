package com.jellycine.app.download

import android.content.Context

object DownloadRepositoryProvider {
    @Volatile
    private var instance: DownloadRepository? = null

    fun getInstance(context: Context): DownloadRepository {
        return instance ?: synchronized(this) {
            instance ?: DownloadRepository(context.applicationContext).also { instance = it }
        }
    }
}
