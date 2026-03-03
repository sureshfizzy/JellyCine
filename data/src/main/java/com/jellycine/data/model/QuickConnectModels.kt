package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

data class QuickConnectDto(
    @SerializedName("Secret")
    val secret: String
)

data class QuickConnectResult(
    @SerializedName("Code")
    val code: String? = null,

    @SerializedName("Secret")
    val secret: String? = null
)