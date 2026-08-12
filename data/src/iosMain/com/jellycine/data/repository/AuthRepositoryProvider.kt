package com.jellycine.data.repository

private var authRepositoryInstance: AuthRepository? = null

object AuthRepositoryProvider {
    fun getInstance(): AuthRepository {
        return authRepositoryInstance ?: AuthRepository().also { authRepositoryInstance = it }
    }
}