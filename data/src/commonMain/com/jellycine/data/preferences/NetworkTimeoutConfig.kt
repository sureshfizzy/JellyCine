package com.jellycine.data.preferences

data class NetworkTimeoutConfig(
    val requestTimeoutMs: Int,
    val connectionTimeoutMs: Int,
    val socketTimeoutMs: Int
)
