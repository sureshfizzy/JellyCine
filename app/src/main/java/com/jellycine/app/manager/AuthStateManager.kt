package com.jellycine.app.manager

import android.content.Context
import com.jellycine.data.repository.AuthRepositoryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AuthStateManager(private val context: Context) {
    
    private val authRepository = AuthRepositoryProvider.getInstance(context)
    
    val isAuthenticated: Flow<Boolean> = authRepository.isAuthenticated
    
    suspend fun checkAuthenticationState(): Boolean {
        return try {
            authRepository.isAuthenticated.first()
        } catch (e: Exception) {
            false
        }
    }

    // Synchronous version for navigation setup
    fun checkAuthenticationStateSync(): Boolean {
        return try {
            kotlinx.coroutines.runBlocking {
                authRepository.isAuthenticated.first()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun logout() {
        authRepository.logout()
    }
    
    companion object {
        @Volatile
        private var INSTANCE: AuthStateManager? = null
        
        fun getInstance(context: Context): AuthStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
