package com.vela.data.network

import kotlinx.serialization.json.Json

val VelaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
}
