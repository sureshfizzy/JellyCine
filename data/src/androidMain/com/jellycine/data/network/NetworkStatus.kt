package com.jellycine.data.network

sealed class NetworkStatus {
    object Online : NetworkStatus()
    object LocalNetwork : NetworkStatus()
    object Offline : NetworkStatus()

    val isAvailable: Boolean get() = this !is Offline
}