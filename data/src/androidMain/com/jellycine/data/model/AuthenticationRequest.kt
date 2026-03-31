package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

data class AuthenticationRequest(
    @SerializedName("Username")
    val username: String,
    
    @SerializedName("Pw")
    val password: String
)
