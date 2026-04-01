package com.jellycine.data.network

import com.jellycine.data.model.ServerInfo

fun inferServerType(baseUrl: String): ServerType {
    return if (trimTrailingSlash(baseUrl).endsWith("/emby", ignoreCase = true)) {
        ServerType.EMBY
    } else {
        ServerType.UNKNOWN
    }
}

fun detectServerType(serverInfo: ServerInfo, headers: ApiHeaders? = null): ServerType {
    val appHeader = headers?.get("X-Application").orEmpty()
    if (appHeader.contains("jellyfin", ignoreCase = true)) {
        return ServerType.JELLYFIN
    }
    if (appHeader.contains("emby", ignoreCase = true)) {
        return ServerType.EMBY
    }
    val productName = serverInfo.productName.orEmpty()
    if (productName.contains("jellyfin", ignoreCase = true)) {
        return ServerType.JELLYFIN
    }
    if (productName.contains("emby", ignoreCase = true)) {
        return ServerType.EMBY
    }
    val version = serverInfo.version.orEmpty()
    if (version.startsWith("4.")) {
        return ServerType.EMBY
    }
    return ServerType.UNKNOWN
}
