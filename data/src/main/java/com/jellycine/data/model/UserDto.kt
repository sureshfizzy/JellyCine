package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("Name")
    val name: String? = null,
    
    @SerializedName("ServerId")
    val serverId: String? = null,
    
    @SerializedName("ServerName")
    val serverName: String? = null,
    
    @SerializedName("Id")
    val id: String? = null,
    
    @SerializedName("PrimaryImageTag")
    val primaryImageTag: String? = null,
    
    @SerializedName("HasPassword")
    val hasPassword: Boolean? = null,
    
    @SerializedName("HasConfiguredPassword")
    val hasConfiguredPassword: Boolean? = null,
    
    @SerializedName("HasConfiguredEasyPassword")
    val hasConfiguredEasyPassword: Boolean? = null,
    
    @SerializedName("EnableAutoLogin")
    val enableAutoLogin: Boolean? = null,
    
    @SerializedName("LastLoginDate")
    val lastLoginDate: String? = null,
    
    @SerializedName("LastActivityDate")
    val lastActivityDate: String? = null,
    
    @SerializedName("Configuration")
    val configuration: UserConfiguration? = null,
    
    @SerializedName("Policy")
    val policy: UserPolicy? = null,
    
    @SerializedName("PrimaryImageAspectRatio")
    val primaryImageAspectRatio: Double? = null
)

data class UserConfiguration(
    @SerializedName("AudioLanguagePreference")
    val audioLanguagePreference: String? = null,
    
    @SerializedName("PlayDefaultAudioTrack")
    val playDefaultAudioTrack: Boolean? = null,
    
    @SerializedName("SubtitleLanguagePreference")
    val subtitleLanguagePreference: String? = null,
    
    @SerializedName("DisplayMissingEpisodes")
    val displayMissingEpisodes: Boolean? = null,
    
    @SerializedName("GroupedFolders")
    val groupedFolders: List<String>? = null,
    
    @SerializedName("SubtitleMode")
    val subtitleMode: String? = null,
    
    @SerializedName("DisplayCollectionsView")
    val displayCollectionsView: Boolean? = null,
    
    @SerializedName("EnableLocalPassword")
    val enableLocalPassword: Boolean? = null,
    
    @SerializedName("OrderedViews")
    val orderedViews: List<String>? = null,
    
    @SerializedName("LatestItemsExcludes")
    val latestItemsExcludes: List<String>? = null,
    
    @SerializedName("MyMediaExcludes")
    val myMediaExcludes: List<String>? = null,
    
    @SerializedName("HidePlayedInLatest")
    val hidePlayedInLatest: Boolean? = null,
    
    @SerializedName("RememberAudioSelections")
    val rememberAudioSelections: Boolean? = null,
    
    @SerializedName("RememberSubtitleSelections")
    val rememberSubtitleSelections: Boolean? = null,
    
    @SerializedName("EnableNextEpisodeAutoPlay")
    val enableNextEpisodeAutoPlay: Boolean? = null
)

data class UserPolicy(
    @SerializedName("IsAdministrator")
    val isAdministrator: Boolean? = null,
    
    @SerializedName("IsHidden")
    val isHidden: Boolean? = null,
    
    @SerializedName("IsDisabled")
    val isDisabled: Boolean? = null,
    
    @SerializedName("MaxParentalRating")
    val maxParentalRating: Int? = null,
    
    @SerializedName("BlockedTags")
    val blockedTags: List<String>? = null,
    
    @SerializedName("EnableUserPreferenceAccess")
    val enableUserPreferenceAccess: Boolean? = null,
    
    @SerializedName("BlockUnratedItems")
    val blockUnratedItems: List<String>? = null,
    
    @SerializedName("EnableRemoteControlOfOtherUsers")
    val enableRemoteControlOfOtherUsers: Boolean? = null,
    
    @SerializedName("EnableSharedDeviceControl")
    val enableSharedDeviceControl: Boolean? = null,
    
    @SerializedName("EnableRemoteAccess")
    val enableRemoteAccess: Boolean? = null,
    
    @SerializedName("EnableLiveTvManagement")
    val enableLiveTvManagement: Boolean? = null,
    
    @SerializedName("EnableLiveTvAccess")
    val enableLiveTvAccess: Boolean? = null,
    
    @SerializedName("EnableMediaPlayback")
    val enableMediaPlayback: Boolean? = null,
    
    @SerializedName("EnableAudioPlaybackTranscoding")
    val enableAudioPlaybackTranscoding: Boolean? = null,
    
    @SerializedName("EnableVideoPlaybackTranscoding")
    val enableVideoPlaybackTranscoding: Boolean? = null
)
