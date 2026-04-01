package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal playback device profile used for server-side stream negotiation.
 */
@Serializable
data class DeviceProfile(
    @SerialName("Name")
    val name: String? = null,
    @SerialName("MaxStreamingBitrate")
    val maxStreamingBitrate: Long? = null,
    @SerialName("MaxStaticBitrate")
    val maxStaticBitrate: Long? = null,
    @SerialName("SupportedMediaTypes")
    val supportedMediaTypes: String? = null,
    @SerialName("DirectPlayProfiles")
    val directPlayProfiles: List<DirectPlayProfile>? = null,
    @SerialName("TranscodingProfiles")
    val transcodingProfiles: List<TranscodingProfile>? = null,
    @SerialName("SubtitleProfiles")
    val subtitleProfiles: List<SubtitleProfile>? = null
)

@Serializable
data class DirectPlayProfile(
    @SerialName("Container")
    val container: String? = null,
    @SerialName("AudioCodec")
    val audioCodec: String? = null,
    @SerialName("VideoCodec")
    val videoCodec: String? = null,
    @SerialName("Type")
    val type: String? = null
)

@Serializable
data class TranscodingProfile(
    @SerialName("Container")
    val container: String? = null,
    @SerialName("Type")
    val type: String? = null,
    @SerialName("VideoCodec")
    val videoCodec: String? = null,
    @SerialName("AudioCodec")
    val audioCodec: String? = null,
    @SerialName("Protocol")
    val protocol: String? = null,
    @SerialName("Context")
    val context: String? = null,
    @SerialName("EnableSubtitlesInManifest")
    val enableSubtitlesInManifest: Boolean? = null,
    @SerialName("MaxAudioChannels")
    val maxAudioChannels: String? = null
)

@Serializable
data class SubtitleProfile(
    @SerialName("Format")
    val format: String? = null,
    @SerialName("Method")
    val method: String? = null
)
