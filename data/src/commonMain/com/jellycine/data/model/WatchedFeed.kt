package com.jellycine.data.model

data class WatchedFeedSection(
    val title: String,
    val items: List<BaseItemDto>
)

data class WatchedFeedState(
    val sections: List<WatchedFeedSection>,
    val error: String?
)