package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecommendationDto(
    @SerialName("BaselineItemName")
    val baselineItemName: String? = null,

    @SerialName("CategoryId")
    val categoryId: String? = null,

    @SerialName("Items")
    val items: List<BaseItemDto>? = null,

    @SerialName("RecommendationType")
    val recommendationType: String? = null
)