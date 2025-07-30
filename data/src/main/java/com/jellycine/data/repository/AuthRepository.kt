package com.jellycine.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jellycine.data.api.JellyfinApi
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.model.AuthenticationRequest
import com.jellycine.data.model.AuthenticationResult
import com.jellycine.data.model.ServerInfo
import com.jellycine.data.network.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepository(private val context: Context) {

    private val dataStore: DataStore<Preferences> = DataStoreProvider.getDataStore(context)
    

    
    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_NAME_KEY = stringPreferencesKey("server_name")
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val IS_AUTHENTICATED_KEY = booleanPreferencesKey("is_authenticated")
    }
    
    val isAuthenticated: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_AUTHENTICATED_KEY] ?: false
    }

    fun getServerUrl(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[SERVER_URL_KEY]
    }

    fun getServerName(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[SERVER_NAME_KEY]
    }

    fun getUsername(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[USERNAME_KEY]
    }

    fun getAccessToken(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY]
    }
    
    // Remove the sync method - not needed anymore
    
    suspend fun testServerConnection(serverUrl: String): Result<ServerInfo> {
        return try {
            // Validate URL format first
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                return Result.failure(Exception("Invalid URL format. URL must start with http:// or https://"))
            }

            val api = NetworkModule.createJellyfinApi(serverUrl)
            val response = api.getPublicSystemInfo()

            if (response.isSuccessful && response.body() != null) {
                val serverInfo = response.body()!!
                // Save server info
                dataStore.edit { preferences ->
                    preferences[SERVER_URL_KEY] = serverUrl
                    preferences[SERVER_NAME_KEY] = serverInfo.serverName
                }
                Result.success(serverInfo)
            } else {
                val errorMessage = when (response.code()) {
                    404 -> "Server not found. Please check the URL."
                    403 -> "Access forbidden. Server may not allow connections."
                    500 -> "Server internal error. Please try again later."
                    else -> "Server connection failed with HTTP ${response.code()}"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Cannot reach server. Please check your internet connection and server URL."))
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("Connection refused. Please check if the server is running and the URL is correct."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Connection timeout. Please check your internet connection."))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception("SSL/TLS error. Please check if the server supports HTTPS properly."))
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown connection error"
            Result.failure(Exception("Connection failed: $errorMessage"))
        }
    }
    
    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String
    ): Result<AuthenticationResult> {
        return try {
            val api = NetworkModule.createJellyfinApi(serverUrl)
            val request = AuthenticationRequest(username, password)
            val response = api.authenticateByName(request)
            
            if (response.isSuccessful && response.body() != null) {
                val authResult = response.body()!!
                

                dataStore.edit { preferences ->
                    preferences[ACCESS_TOKEN_KEY] = authResult.accessToken
                    preferences[USER_ID_KEY] = authResult.user.id
                    preferences[USERNAME_KEY] = authResult.user.name
                    preferences[IS_AUTHENTICATED_KEY] = true
                }
                
                Result.success(authResult)
            } else {
                Result.failure(Exception("Authentication failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = ""
            preferences[USER_ID_KEY] = ""
            preferences[IS_AUTHENTICATED_KEY] = false
        }
    }
}
