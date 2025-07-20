package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

data class ServerInfo(
    @SerializedName("ServerName")
    val serverName: String,

    @SerializedName("Version")
    val version: String? = null,

    @SerializedName("Id")
    val id: String? = null,

    @SerializedName("LocalAddress")
    val localAddress: String? = null,

    @SerializedName("WanAddress")
    val wanAddress: String? = null
)
