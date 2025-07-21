package com.jellycine.data.api

import com.jellycine.data.model.AuthenticationRequest
import com.jellycine.data.model.AuthenticationResult
import com.jellycine.data.model.ServerInfo
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JellyfinApi {

    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): Response<ServerInfo>

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(@Body request: AuthenticationRequest): Response<AuthenticationResult>

    @GET("Users/{userId}/Items/Latest")
    suspend fun getLatestItems(
        @Path("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("fields") fields: String? = null
    ): Response<List<BaseItemDto>>

    @GET("Users/{userId}/Items")
    suspend fun getUserItems(
        @Path("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("recursive") recursive: Boolean? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("startIndex") startIndex: Int? = null,
        @Query("fields") fields: String? = null
    ): Response<QueryResult<BaseItemDto>>

    @GET("Users/{userId}/Views")
    suspend fun getUserViews(@Path("userId") userId: String): Response<QueryResult<BaseItemDto>>

    @GET("Users/{userId}/Items")
    suspend fun getResumeItems(
        @Path("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("filters") filters: String = "IsResumable",
        @Query("recursive") recursive: Boolean = true,
        @Query("sortBy") sortBy: String = "DatePlayed",
        @Query("sortOrder") sortOrder: String = "Descending",
        @Query("fields") fields: String? = null
    ): Response<QueryResult<BaseItemDto>>

    @GET("Users/{userId}")
    suspend fun getUserById(@Path("userId") userId: String): Response<UserDto>

    @GET("Genres")
    suspend fun getGenres(
        @Query("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null
    ): Response<QueryResult<BaseItemDto>>

    @GET("Items")
    suspend fun getItemsByGenre(
        @Query("userId") userId: String,
        @Query("genreIds") genreIds: String,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("recursive") recursive: Boolean? = true,
        @Query("limit") limit: Int? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("fields") fields: String? = null
    ): Response<QueryResult<BaseItemDto>>
}
