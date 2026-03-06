package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

/**
 * Minimal playback device profile used for server-side stream negotiation.
 */
data class DeviceProfile(
    @SerializedName("Name")
    val name: String? = null,
    @SerializedName("MaxStreamingBitrate")
    val maxStreamingBitrate: Long? = null,
    @SerializedName("MaxStaticBitrate")
    val maxStaticBitrate: Long? = null,
    @SerializedName("SupportedMediaTypes")
    val supportedMediaTypes: String? = null,
    @SerializedName("DirectPlayProfiles")
    val directPlayProfiles: List<DirectPlayProfile>? = null,
    @SerializedName("TranscodingProfiles")
    val transcodingProfiles: List<TranscodingProfile>? = null,
    @SerializedName("SubtitleProfiles")
    val subtitleProfiles: List<SubtitleProfile>? = null
)

data class DirectPlayProfile(
    @SerializedName("Container")
    val container: String? = null,
    @SerializedName("AudioCodec")
    val audioCodec: String? = null,
    @SerializedName("VideoCodec")
    val videoCodec: String? = null,
    @SerializedName("Type")
    val type: String? = null
)

data class TranscodingProfile(
    @SerializedName("Container")
    val container: String? = null,
    @SerializedName("Type")
    val type: String? = null,
    @SerializedName("VideoCodec")
    val videoCodec: String? = null,
    @SerializedName("AudioCodec")
    val audioCodec: String? = null,
    @SerializedName("Protocol")
    val protocol: String? = null,
    @SerializedName("Context")
    val context: String? = null,
    @SerializedName("EnableSubtitlesInManifest")
    val enableSubtitlesInManifest: Boolean? = null,
    @SerializedName("MaxAudioChannels")
    val maxAudioChannels: String? = null
)

data class SubtitleProfile(
    @SerializedName("Format")
    val format: String? = null,
    @SerializedName("Method")
    val method: String? = null
)
