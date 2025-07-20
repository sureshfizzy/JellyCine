package com.jellycine.data.network

import com.jellycine.data.api.JellyfinApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    
    private const val CLIENT_NAME = "JellyCine"
    private const val CLIENT_VERSION = "1.0.0"
    private const val DEVICE_NAME = "Android"
    
    fun createJellyfinApi(baseUrl: String, accessToken: String? = null): JellyfinApi {
        val okHttpClient = createOkHttpClient(accessToken)
        
        val retrofit = Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(JellyfinApi::class.java)
    }
    
    private fun createOkHttpClient(accessToken: String? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        builder.addInterceptor(loggingInterceptor)
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val deviceId = generateDeviceId()
            
            val authHeader = buildString {
                append("MediaBrowser ")
                append("Client=\"$CLIENT_NAME\", ")
                append("Device=\"$DEVICE_NAME\", ")
                append("DeviceId=\"$deviceId\", ")
                append("Version=\"$CLIENT_VERSION\"")
                
                if (!accessToken.isNullOrEmpty()) {
                    append(", Token=\"$accessToken\"")
                }
            }
            
            val newRequest = originalRequest.newBuilder()
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .build()
            
            chain.proceed(newRequest)
        }
        
        builder.addInterceptor(authInterceptor)
        
        return builder.build()
    }
    
    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
    
    private fun generateDeviceId(): String {
        return "jellycine-android-${System.currentTimeMillis()}"
    }
}
