package com.jellycine.data.api

import com.jellycine.data.model.AuthenticationRequest
import com.jellycine.data.model.AuthenticationResult
import com.jellycine.data.model.ServerInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface JellyfinApi {
    
    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): Response<ServerInfo>
    
    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(@Body request: AuthenticationRequest): Response<AuthenticationResult>
}
