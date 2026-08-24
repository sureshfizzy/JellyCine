package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemInfoFull(
    @SerialName("ServerName")
    val serverName: String? = null,
    @SerialName("Version")
    val version: String? = null,
    @SerialName("Id")
    val id: String? = null,
    @SerialName("OperatingSystem")
    val operatingSystem: String? = null,
    @SerialName("OperatingSystemDisplayName")
    val operatingSystemDisplayName: String? = null,
    @SerialName("HasUpdateAvailable")
    val hasUpdateAvailable: Boolean? = null
)

@Serializable
data class AdminSessionInfo(
    @SerialName("Id")
    val id: String? = null,
    @SerialName("UserName")
    val userName: String? = null,
    @SerialName("Client")
    val client: String? = null,
    @SerialName("DeviceName")
    val deviceName: String? = null,
    @SerialName("ApplicationVersion")
    val applicationVersion: String? = null,
    @SerialName("RemoteEndPoint")
    val remoteEndPoint: String? = null,
    @SerialName("NowPlayingItem")
    val nowPlayingItem: SessionNowPlayingItem? = null,
    @SerialName("PlayState")
    val playState: SessionPlayState? = null
)

@Serializable
data class SessionNowPlayingItem(
    @SerialName("Name")
    val name: String? = null,
    @SerialName("SeriesName")
    val seriesName: String? = null,
    @SerialName("SeriesId")
    val seriesId: String? = null,
    @SerialName("Id")
    val id: String? = null,
    @SerialName("Type")
    val type: String? = null,
    @SerialName("Container")
    val container: String? = null,
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerialName("ProductionYear")
    val productionYear: Int? = null,
    @SerialName("Bitrate")
    val bitrate: Int? = null,
    @SerialName("MediaStreams")
    val mediaStreams: List<SessionMediaStream>? = null
)

@Serializable
data class SessionMediaStream(
    @SerialName("Type")
    val type: String? = null,
    @SerialName("Codec")
    val codec: String? = null,
    @SerialName("DisplayTitle")
    val displayTitle: String? = null,
    @SerialName("IsDefault")
    val isDefault: Boolean? = null
)

@Serializable
data class SessionPlayState(
    @SerialName("PositionTicks")
    val positionTicks: Long? = null,
    @SerialName("IsPaused")
    val isPaused: Boolean? = null,
    @SerialName("PlayMethod")
    val playMethod: String? = null
)

@Serializable
data class ActivityLogEntry(
    @SerialName("Id")
    val id: Long? = null,
    @SerialName("Name")
    val name: String? = null,
    @SerialName("Date")
    val date: String? = null,
    @SerialName("ShortOverview")
    val shortOverview: String? = null
)

@Serializable
data class ActivityLogResult(
    @SerialName("Items")
    val items: List<ActivityLogEntry> = emptyList(),
    @SerialName("TotalRecordCount")
    val totalRecordCount: Int? = null
)