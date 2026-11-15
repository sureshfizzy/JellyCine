package com.jellycine.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscoveredServer(
    @SerialName("Address") val address: String,
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("EndpointAddress") val endpointAddress: String? = null
)
