package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A subtitle result returned by the server's remote subtitle providers
 * (e.g. Open Subtitles) via GET /Items/{itemId}/RemoteSearch/Subtitles/{language}.
 */
@Serializable
data class RemoteSubtitleInfo(
    @SerialName("Id")
    val id: String? = null,

    @SerialName("ProviderName")
    val providerName: String? = null,

    @SerialName("Name")
    val name: String? = null,

    @SerialName("Format")
    val format: String? = null,

    @SerialName("Language")
    val language: String? = null,

    @SerialName("ThreeLetterISOLanguageName")
    val threeLetterISOLanguageName: String? = null,

    @SerialName("DownloadCount")
    val downloadCount: Int? = null,

    @SerialName("CommunityRating")
    val communityRating: Float? = null,

    @SerialName("IsHashMatch")
    val isHashMatch: Boolean? = null,

    @SerialName("IsForced")
    val isForced: Boolean? = null,

    @SerialName("Author")
    val author: String? = null,

    @SerialName("Comment")
    val comment: String? = null,

    @SerialName("DateCreated")
    val dateCreated: String? = null
)