package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticationResult(
    @SerialName("User")
    val user: User,
    
    @SerialName("SessionInfo")
    val sessionInfo: SessionInfo? = null,
    
    @SerialName("AccessToken")
    val accessToken: String,
    
    @SerialName("ServerId")
    val serverId: String
)

@Serializable
data class User(
    @SerialName("Name")
    val name: String,
    
    @SerialName("Id")
    val id: String,
    
    @SerialName("HasPassword")
    val hasPassword: Boolean = false,
    
    @SerialName("HasConfiguredPassword")
    val hasConfiguredPassword: Boolean = false,
    
    @SerialName("HasConfiguredEasyPassword")
    val hasConfiguredEasyPassword: Boolean = false
)

@Serializable
data class SessionInfo(
    @SerialName("Id")
    val id: String,
    
    @SerialName("UserId")
    val userId: String,
    
    @SerialName("UserName")
    val userName: String,
    
    @SerialName("Client")
    val client: String,
    
    @SerialName("LastActivityDate")
    val lastActivityDate: String,
    
    @SerialName("DeviceName")
    val deviceName: String,
    
    @SerialName("DeviceId")
    val deviceId: String,
    
    @SerialName("ApplicationVersion")
    val applicationVersion: String
)