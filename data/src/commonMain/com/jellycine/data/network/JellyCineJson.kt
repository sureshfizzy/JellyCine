package com.jellycine.data.network

import kotlinx.serialization.json.Json

val JellyCineJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
}
