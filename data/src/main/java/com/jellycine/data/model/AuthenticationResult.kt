package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

data class AuthenticationResult(
    @SerializedName("User")
    val user: User,
    
    @SerializedName("SessionInfo")
    val sessionInfo: SessionInfo? = null,
    
    @SerializedName("AccessToken")
    val accessToken: String,
    
    @SerializedName("ServerId")
    val serverId: String
)

data class User(
    @SerializedName("Name")
    val name: String,
    
    @SerializedName("Id")
    val id: String,
    
    @SerializedName("HasPassword")
    val hasPassword: Boolean = false,
    
    @SerializedName("HasConfiguredPassword")
    val hasConfiguredPassword: Boolean = false,
    
    @SerializedName("HasConfiguredEasyPassword")
    val hasConfiguredEasyPassword: Boolean = false
)

data class SessionInfo(
    @SerializedName("Id")
    val id: String,
    
    @SerializedName("UserId")
    val userId: String,
    
    @SerializedName("UserName")
    val userName: String,
    
    @SerializedName("Client")
    val client: String,
    
    @SerializedName("LastActivityDate")
    val lastActivityDate: String,
    
    @SerializedName("DeviceName")
    val deviceName: String,
    
    @SerializedName("DeviceId")
    val deviceId: String,
    
    @SerializedName("ApplicationVersion")
    val applicationVersion: String
)
