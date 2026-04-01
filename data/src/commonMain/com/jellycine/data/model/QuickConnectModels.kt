package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuickConnectDto(
    @SerialName("Secret")
    val secret: String
)

@Serializable
data class QuickConnectResult(
    @SerialName("Code")
    val code: String? = null,

    @SerialName("Secret")
    val secret: String? = null
)
