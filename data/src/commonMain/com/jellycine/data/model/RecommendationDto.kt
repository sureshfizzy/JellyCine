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

fun RecommendationDto.title(): String? {
    val baseline = baselineItemName?.takeIf { it.isNotBlank() }
    return when (recommendationType) {
        "HasDirectorFromRecentlyPlayed",
        "HasLikedDirector" -> baseline?.let { "Directed by $it" }

        "HasActorFromRecentlyPlayed",
        "HasLikedActor" -> baseline?.let { "Starring $it" }

        "SimilarToLikedItem" -> baseline?.let { "Because you like $it" }
        "SimilarToRecentlyPlayed" -> baseline?.let { "Because you watched $it" }
        else -> baseline
    }
}

fun RecommendationDto.seerRole(): SeerrPersonRole? {
    return when (recommendationType) {
        "HasDirectorFromRecentlyPlayed",
        "HasLikedDirector" -> SeerrPersonRole.DIRECTOR

        "HasActorFromRecentlyPlayed",
        "HasLikedActor" -> SeerrPersonRole.ACTOR

        else -> null
    }
}