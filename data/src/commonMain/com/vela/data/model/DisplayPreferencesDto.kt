package com.vela.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DisplayPreferencesDto(
    @SerialName("Id")
    val id: String? = null,
    @SerialName("SortBy")
    val sortBy: String? = null,
    @SerialName("SortOrder")
    val sortOrder: String? = null,
    @SerialName("Client")
    val client: String? = null,
    @SerialName("CustomPrefs")
    val customPrefs: Map<String, String>? = null
)
