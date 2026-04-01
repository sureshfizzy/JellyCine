package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("Name")
    val name: String? = null,
    
    @SerialName("ServerId")
    val serverId: String? = null,
    
    @SerialName("ServerName")
    val serverName: String? = null,
    
    @SerialName("Id")
    val id: String? = null,
    
    @SerialName("PrimaryImageTag")
    val primaryImageTag: String? = null,
    
    @SerialName("HasPassword")
    val hasPassword: Boolean? = null,
    
    @SerialName("HasConfiguredPassword")
    val hasConfiguredPassword: Boolean? = null,
    
    @SerialName("HasConfiguredEasyPassword")
    val hasConfiguredEasyPassword: Boolean? = null,
    
    @SerialName("EnableAutoLogin")
    val enableAutoLogin: Boolean? = null,
    
    @SerialName("LastLoginDate")
    val lastLoginDate: String? = null,
    
    @SerialName("LastActivityDate")
    val lastActivityDate: String? = null,
    
    @SerialName("Configuration")
    val configuration: UserConfiguration? = null,
    
    @SerialName("Policy")
    val policy: UserPolicy? = null,
    
    @SerialName("PrimaryImageAspectRatio")
    val primaryImageAspectRatio: Double? = null
)

@Serializable
data class UserConfiguration(
    @SerialName("AudioLanguagePreference")
    val audioLanguagePreference: String? = null,
    
    @SerialName("PlayDefaultAudioTrack")
    val playDefaultAudioTrack: Boolean? = null,
    
    @SerialName("SubtitleLanguagePreference")
    val subtitleLanguagePreference: String? = null,
    
    @SerialName("DisplayMissingEpisodes")
    val displayMissingEpisodes: Boolean? = null,
    
    @SerialName("GroupedFolders")
    val groupedFolders: List<String>? = null,
    
    @SerialName("SubtitleMode")
    val subtitleMode: String? = null,
    
    @SerialName("DisplayCollectionsView")
    val displayCollectionsView: Boolean? = null,
    
    @SerialName("EnableLocalPassword")
    val enableLocalPassword: Boolean? = null,
    
    @SerialName("OrderedViews")
    val orderedViews: List<String>? = null,
    
    @SerialName("LatestItemsExcludes")
    val latestItemsExcludes: List<String>? = null,
    
    @SerialName("MyMediaExcludes")
    val myMediaExcludes: List<String>? = null,
    
    @SerialName("HidePlayedInLatest")
    val hidePlayedInLatest: Boolean? = null,
    
    @SerialName("RememberAudioSelections")
    val rememberAudioSelections: Boolean? = null,
    
    @SerialName("RememberSubtitleSelections")
    val rememberSubtitleSelections: Boolean? = null,
    
    @SerialName("EnableNextEpisodeAutoPlay")
    val enableNextEpisodeAutoPlay: Boolean? = null
)

@Serializable
data class UserPolicy(
    @SerialName("IsAdministrator")
    val isAdministrator: Boolean? = null,
    
    @SerialName("IsHidden")
    val isHidden: Boolean? = null,
    
    @SerialName("IsDisabled")
    val isDisabled: Boolean? = null,
    
    @SerialName("MaxParentalRating")
    val maxParentalRating: Int? = null,
    
    @SerialName("BlockedTags")
    val blockedTags: List<String>? = null,
    
    @SerialName("EnableUserPreferenceAccess")
    val enableUserPreferenceAccess: Boolean? = null,
    
    @SerialName("BlockUnratedItems")
    val blockUnratedItems: List<String>? = null,
    
    @SerialName("EnableRemoteControlOfOtherUsers")
    val enableRemoteControlOfOtherUsers: Boolean? = null,
    
    @SerialName("EnableSharedDeviceControl")
    val enableSharedDeviceControl: Boolean? = null,
    
    @SerialName("EnableRemoteAccess")
    val enableRemoteAccess: Boolean? = null,
    
    @SerialName("EnableLiveTvManagement")
    val enableLiveTvManagement: Boolean? = null,
    
    @SerialName("EnableLiveTvAccess")
    val enableLiveTvAccess: Boolean? = null,
    
    @SerialName("EnableMediaPlayback")
    val enableMediaPlayback: Boolean? = null,
    
    @SerialName("EnableAudioPlaybackTranscoding")
    val enableAudioPlaybackTranscoding: Boolean? = null,
    
    @SerialName("EnableVideoPlaybackTranscoding")
    val enableVideoPlaybackTranscoding: Boolean? = null
)
