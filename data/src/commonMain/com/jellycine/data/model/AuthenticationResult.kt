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
    val id: String? = null,

    @SerialName("UserId")
    val userId: String? = null,

    @SerialName("UserName")
    val userName: String? = null,

    @SerialName("Client")
    val client: String? = null,

    @SerialName("LastActivityDate")
    val lastActivityDate: String? = null,

    @SerialName("DeviceName")
    val deviceName: String? = null,

    @SerialName("DeviceId")
    val deviceId: String? = null,

    @SerialName("ApplicationVersion")
    val applicationVersion: String? = null
)