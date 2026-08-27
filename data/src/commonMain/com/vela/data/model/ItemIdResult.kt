package com.vela.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemIdResult(
    @SerialName("Id")
    val id: String? = null
)
