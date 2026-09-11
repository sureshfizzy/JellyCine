package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThemeMediaResult(
    @SerialName("ThemeSongsResult")
    val themeSongsResult: QueryResult<BaseItemDto>? = null
)
