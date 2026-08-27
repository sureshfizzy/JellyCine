package com.vela.data.repository

private var mediaRepositoryInstance: MediaRepository? = null

object MediaRepositoryProvider {
    fun getInstance(): MediaRepository {
        return mediaRepositoryInstance ?: MediaRepository().also { mediaRepositoryInstance = it }
    }
}