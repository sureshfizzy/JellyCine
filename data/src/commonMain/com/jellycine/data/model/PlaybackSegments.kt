package com.jellycine.data.model

data class PlaybackSegmentWindow(
    val startMs: Long,
    val endMs: Long?,
    val source: PlaybackSegmentSource
)

data class PlaybackSegments(
    val intro: PlaybackSegmentWindow? = null,
    val recap: PlaybackSegmentWindow? = null,
    val credits: PlaybackSegmentWindow? = null,
    val preview: PlaybackSegmentWindow? = null
) {
    fun hasAnySegments(): Boolean {
        return intro != null || recap != null || credits != null || preview != null
    }
}
