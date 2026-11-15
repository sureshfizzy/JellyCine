package com.jellycine.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SavedServer(
    @SerialName("id")
    val id: String,
    @SerialName("serverUrl")
    val serverUrl: String,
    @SerialName("serverName")
    val serverName: String,
    @SerialName("serverTypeRaw")
    val serverTypeRaw: String,
    @SerialName("username")
    val username: String,
    @SerialName("userId")
    val userId: String,
    @SerialName("profileImageUrl")
    val profileImageUrl: String? = null,
    @SerialName("lastUsedAt")
    val lastUsedAt: Long
)

data class ActiveSessionSnapshot(
    val serverName: String?,
    val serverUrl: String?,
    val serverType: String?,
    val username: String?,
    val savedServers: List<SavedServer>,
    val activeServerId: String?
)
