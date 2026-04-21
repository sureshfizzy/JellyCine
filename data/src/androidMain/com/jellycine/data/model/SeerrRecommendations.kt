package com.jellycine.data.model

data class SeerrRecommendationTitle(
    val tmdbId: String,
    val title: String,
    val mediaType: String,
    val productionYear: Int? = null,
    val posterPath: String? = null,
    val jellyfinMediaId: String? = null,
    val roleLabel: String? = null,
    val requestState: SeerrRequestState = SeerrRequestState.NONE
)

enum class SeerrPersonCreditType {
    DIRECTOR,
    ACTOR
}

enum class SeerrRequestState {
    NONE,
    REQUESTED
}