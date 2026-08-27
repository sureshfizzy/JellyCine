package com.vela.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticationRequest(
    @SerialName("Username")
    val username: String,
    
    @SerialName("Pw")
    val password: String
)
