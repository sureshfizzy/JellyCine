package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

data class RecommendationDto(
    @SerializedName("BaselineItemName")
    val baselineItemName: String? = null,

    @SerializedName("CategoryId")
    val categoryId: String? = null,

    @SerializedName("Items")
    val items: List<BaseItemDto>? = null,

    @SerializedName("RecommendationType")
    val recommendationType: String? = null
)