package com.jellycine.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaExtra(
    val url: String,
    val name: String,
    val type: String,
    val thumbnailUrl: String? = null
)

internal data class RawVideo(
    val key: String? = null,
    val name: String? = null,
    val site: String? = null,
    val type: String? = null,
    val official: Boolean? = null
)

internal fun List<RawVideo>?.toMediaExtras(): List<MediaExtra> {
    val keptPerType = mutableMapOf<String, Int>()
    return this.orEmpty()
        .filter { video ->
            video.site.equals("YouTube", ignoreCase = true) && !video.key.isNullOrBlank()
        }
        .distinctBy { video -> video.key }
        .sortedWith(
            compareBy<RawVideo> { video -> video.typeRank() }
                .thenByDescending { video -> video.official == true }
        )
        .filter { video ->
            val typeKey = video.type?.lowercase()?.takeIf { it.isNotBlank() } ?: "other"
            val cap = TYPE_CAPS[typeKey] ?: DEFAULT_TYPE_CAP
            val kept = keptPerType.getOrElse(typeKey) { 0 }
            (kept < cap).also { allowed -> if (allowed) keptPerType[typeKey] = kept + 1 }
        }
        .map { video ->
            val key = video.key.orEmpty()
            MediaExtra(
                url = "https://www.youtube.com/watch?v=$key",
                name = video.name?.takeIf { it.isNotBlank() }
                    ?: video.type?.takeIf { it.isNotBlank() }
                    ?: "Trailer",
                type = video.type?.takeIf { it.isNotBlank() } ?: "Trailer",
                thumbnailUrl = "https://img.youtube.com/vi/$key/hqdefault.jpg"
            )
        }
}

private val TYPE_CAPS = mapOf(
    "trailer" to 2,
    "teaser" to 2,
    "featurette" to 2,
    "behind the scenes" to 3,
    "clip" to 3
)

private const val DEFAULT_TYPE_CAP = 2

private fun RawVideo.typeRank(): Int = when (type?.lowercase()) {
    "trailer" -> 0
    "teaser" -> 1
    "behind the scenes" -> 2
    "featurette" -> 3
    "clip" -> 4
    else -> 5
}