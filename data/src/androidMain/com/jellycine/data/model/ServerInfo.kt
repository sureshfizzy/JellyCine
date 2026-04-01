package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    @SerialName("ServerName")
    val serverName: String? = null,

    @SerialName("ProductName")
    val productName: String? = null,

    @SerialName("Version")
    val version: String? = null,

    @SerialName("Id")
    val id: String? = null,

    @SerialName("LocalAddress")
    val localAddress: String? = null,

    @SerialName("WanAddress")
    val wanAddress: String? = null
)