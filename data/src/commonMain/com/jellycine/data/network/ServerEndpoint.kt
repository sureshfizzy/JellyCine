package com.jellycine.data.network

import com.jellycine.data.model.ServerInfo

data class ServerEndpoint(
    val baseUrl: String,
    val serverType: ServerType,
    val serverInfo: ServerInfo
)