package com.jellycine.data.model

import com.jellycine.data.network.NetworkModule

data class AuthHeaderDto(
    val scheme: String,
    val clientName: String = "JellyCine",
    val deviceName: String = "Android",
    val deviceId: String,
    val version: String,
    val accessToken: String? = null
) {
    fun asHeaderValue(): String {
        return buildString {
            append("$scheme ")
            append("Client=\"$clientName\", ")
            append("Device=\"$deviceName\", ")
            append("DeviceId=\"$deviceId\", ")
            append("Version=\"$version\"")

            if (!accessToken.isNullOrBlank()) {
                append(", Token=\"$accessToken\"")
            }
        }
    }

    companion object {
        fun fromServerType(
            serverType: NetworkModule.ServerType?,
            deviceId: String,
            version: String,
            accessToken: String? = null,
            clientName: String = "JellyCine",
            deviceName: String = "Android"
        ): AuthHeaderDto {
            val scheme = if (serverType == NetworkModule.ServerType.EMBY) "Emby" else "MediaBrowser"
            return AuthHeaderDto(
                scheme = scheme,
                clientName = clientName,
                deviceName = deviceName,
                deviceId = deviceId,
                version = version,
                accessToken = accessToken
            )
        }
    }
}
