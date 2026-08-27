package com.vela.data.network

import com.vela.data.model.ServerInfo

data class ServerEndpoint(
    val baseUrl: String,
    val serverType: ServerType,
    val serverInfo: ServerInfo
)