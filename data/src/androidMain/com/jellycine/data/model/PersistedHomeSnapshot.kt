package com.jellycine.data.model

data class HomeLibrarySectionData(
    val library: BaseItemDto,
    val items: List<BaseItemDto>
)

data class PersistedHomeSnapshot(
    val snapshotKey: String,
    val updatedAt: Long,
    val featuredHomeItems: List<BaseItemDto>,
    val continueWatchingItems: List<BaseItemDto>,
    val nextUpItems: List<BaseItemDto>? = null,
    val homeLibrarySections: List<HomeLibrarySectionData>,
    val myMediaLibraries: List<BaseItemDto>? = null,
    val username: String? = null,
    val serverName: String? = null,
    val serverUrl: String? = null,
    val profileImageUrl: String? = null,
    val isAdministrator: Boolean? = null,
    val isVideoTranscodingAllowed: Boolean? = null,
    val isAudioTranscodingAllowed: Boolean? = null
)