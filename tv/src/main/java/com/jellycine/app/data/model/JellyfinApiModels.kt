package com.jellycine.app.data.model

data class SystemInfo(
    val serverName: String? = null,
    val version: String? = null,
    val productName: String? = null,
    val operatingSystem: String? = null,
    val id: String? = null,
    val startupWizardCompleted: Boolean? = null,
    val supportsLibraryMonitor: Boolean? = null,
    val webSocketPortNumber: Int? = null,
    val completedInstallations: List<String>? = null,
    val canSelfRestart: Boolean? = null,
    val canLaunchWebBrowser: Boolean? = null,
    val programDataPath: String? = null,
    val webPath: String? = null,
    val itemsByNamePath: String? = null,
    val cachePath: String? = null,
    val logPath: String? = null,
    val internalMetadataPath: String? = null,
    val transcodingTempPath: String? = null,
    val hasUpdateAvailable: Boolean? = null,
    val encoderLocation: String? = null,
    val systemArchitecture: String? = null
)



data class AuthenticationRequest(
    val username: String,
    val pw: String = "",
    val password: String = ""
)


data class AuthenticationResult(
    val user: UserDto? = null,
    val sessionInfo: SessionInfo? = null,
    val accessToken: String? = null,
    val serverId: String? = null
)


data class SessionInfo(
    val playState: PlayState? = null,
    val additionalUsers: List<SessionUserInfo>? = null,
    val capabilities: ClientCapabilities? = null,
    val remoteEndPoint: String? = null,
    val playableMediaTypes: List<String>? = null,
    val id: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val client: String? = null,
    val lastActivityDate: String? = null,
    val lastPlaybackCheckIn: String? = null,
    val deviceName: String? = null,
    val deviceType: String? = null,
    val nowPlayingItem: BaseItemDto? = null,
    val fullNowPlayingItem: BaseItemDto? = null,
    val nowViewingItem: BaseItemDto? = null,
    val deviceId: String? = null,
    val applicationVersion: String? = null,
    val transcodingInfo: TranscodingInfo? = null,
    val isActive: Boolean? = null,
    val supportsMediaControl: Boolean? = null,
    val supportsRemoteControl: Boolean? = null,
    val nowPlayingQueue: List<QueueItem>? = null,
    val nowPlayingQueueFullItems: List<BaseItemDto>? = null,
    val hasCustomDeviceName: Boolean? = null,
    val playlistItemId: String? = null,
    val serverId: String? = null,
    val userPrimaryImageTag: String? = null,
    val supportedCommands: List<String>? = null
)


data class PlayState(
    val positionTicks: Long? = null,
    val canSeek: Boolean? = null,
    val isPaused: Boolean? = null,
    val isMuted: Boolean? = null,
    val volumeLevel: Int? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val mediaSourceId: String? = null,
    val playMethod: String? = null,
    val repeatMode: String? = null,
    val liveStreamId: String? = null
)




data class UserDto(
    val name: String? = null,
    val serverId: String? = null,
    val serverName: String? = null,
    val id: String? = null,
    val primaryImageTag: String? = null,
    val hasPassword: Boolean? = null,
    val hasConfiguredPassword: Boolean? = null,
    val hasConfiguredEasyPassword: Boolean? = null,
    val enableAutoLogin: Boolean? = null,
    val lastLoginDate: String? = null,
    val lastActivityDate: String? = null,
    val configuration: UserConfiguration? = null,
    val policy: UserPolicy? = null,
    val primaryImageAspectRatio: Double? = null
)


data class UserConfiguration(
    val audioLanguagePreference: String? = null,
    val playDefaultAudioTrack: Boolean? = null,
    val subtitleLanguagePreference: String? = null,
    val displayMissingEpisodes: Boolean? = null,
    val groupedFolders: List<String>? = null,
    val subtitleMode: String? = null,
    val displayCollectionsView: Boolean? = null,
    val enableLocalPassword: Boolean? = null,
    val orderedViews: List<String>? = null,
    val latestItemsExcludes: List<String>? = null,
    val myMediaExcludes: List<String>? = null,
    val hidePlayedInLatest: Boolean? = null,
    val rememberAudioSelections: Boolean? = null,
    val rememberSubtitleSelections: Boolean? = null,
    val enableNextEpisodeAutoPlay: Boolean? = null
)


data class UserPolicy(
    val isAdministrator: Boolean? = null,
    val isHidden: Boolean? = null,
    val isDisabled: Boolean? = null,
    val maxParentalRating: Int? = null,
    val blockedTags: List<String>? = null,
    val enableUserPreferenceAccess: Boolean? = null,
    val accessSchedules: List<AccessSchedule>? = null,
    val blockUnratedItems: List<String>? = null,
    val enableRemoteControlOfOtherUsers: Boolean? = null,
    val enableSharedDeviceControl: Boolean? = null,
    val enableRemoteAccess: Boolean? = null,
    val enableLiveTvManagement: Boolean? = null,
    val enableLiveTvAccess: Boolean? = null,
    val enableMediaPlayback: Boolean? = null,
    val enableAudioPlaybackTranscoding: Boolean? = null,
    val enableVideoPlaybackTranscoding: Boolean? = null,
    val enablePlaybackRemuxing: Boolean? = null,
    val forceRemoteSourceTranscoding: Boolean? = null,
    val enableContentDeletion: Boolean? = null,
    val enableContentDeletionFromFolders: List<String>? = null,
    val enableContentDownloading: Boolean? = null,
    val enableSyncTranscoding: Boolean? = null,
    val enableMediaConversion: Boolean? = null,
    val enabledDevices: List<String>? = null,
    val enableAllDevices: Boolean? = null,
    val enabledChannels: List<String>? = null,
    val enableAllChannels: Boolean? = null,
    val enabledFolders: List<String>? = null,
    val enableAllFolders: Boolean? = null,
    val invalidLoginAttemptCount: Int? = null,
    val loginAttemptsBeforeLockout: Int? = null,
    val maxActiveSessions: Int? = null,
    val enablePublicSharing: Boolean? = null,
    val blockedMediaFolders: List<String>? = null,
    val blockedChannels: List<String>? = null,
    val remoteClientBitrateLimit: Int? = null,
    val authenticationProviderId: String? = null,
    val passwordResetProviderId: String? = null,
    val syncPlayAccess: String? = null
)


data class AccessSchedule(
    val id: Int? = null,
    val userId: String? = null,
    val dayOfWeek: String? = null,
    val startHour: Double? = null,
    val endHour: Double? = null
)

// ========================================
// MEDIA ITEM MODELS
// ========================================


data class BaseItemDto(
    val name: String? = null,
    val originalTitle: String? = null,
    val serverId: String? = null,
    val id: String? = null,
    val etag: String? = null,
    val sourceType: String? = null,
    val playlistItemId: String? = null,
    val dateCreated: String? = null,
    val dateLastMediaAdded: String? = null,
    val extraType: String? = null,
    val airsBeforeSeasonNumber: Int? = null,
    val airsAfterSeasonNumber: Int? = null,
    val airsBeforeEpisodeNumber: Int? = null,
    val canDelete: Boolean? = null,
    val canDownload: Boolean? = null,
    val hasSubtitles: Boolean? = null,
    val preferredMetadataLanguage: String? = null,
    val preferredMetadataCountryCode: String? = null,
    val supportsSync: Boolean? = null,
    val container: String? = null,
    val sortName: String? = null,
    val forcedSortName: String? = null,
    val video3DFormat: String? = null,
    val premiereDate: String? = null,
    val externalUrls: List<ExternalUrl>? = null,
    val mediaSources: List<MediaSourceInfo>? = null,
    val criticRating: Float? = null,
    val productionLocations: List<String>? = null,
    val path: String? = null,
    val enableMediaSourceDisplay: Boolean? = null,
    val overview: String? = null,
    val taglines: List<String>? = null,
    val genres: List<String>? = null,
    val communityRating: Float? = null,
    val cumulativeRunTimeTicks: Long? = null,
    val runTimeTicks: Long? = null,
    val playAccess: String? = null,
    val aspectRatio: String? = null,
    val productionYear: Int? = null,
    val isPlaceHolder: Boolean? = null,
    val number: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val type: String? = null,
    val isFolder: Boolean? = null,
    val parentId: String? = null,
    val userData: UserItemDataDto? = null,
    val recursiveItemCount: Int? = null,
    val childCount: Int? = null,
    val seriesName: String? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val specialFeatureCount: Int? = null,
    val displayPreferencesId: String? = null,
    val status: String? = null,
    val airTime: String? = null,
    val airDays: List<String>? = null,
    val tags: List<String>? = null,
    val primaryImageAspectRatio: Double? = null,
    val artists: List<String>? = null,
    val artistItems: List<NameGuidPair>? = null,
    val album: String? = null,
    val collectionType: String? = null,
    val displayOrder: String? = null,
    val albumId: String? = null,
    val albumPrimaryImageTag: String? = null,
    val seriesPrimaryImageTag: String? = null,
    val albumArtist: String? = null,
    val albumArtists: List<NameGuidPair>? = null,
    val seasonName: String? = null,
    val mediaStreams: List<MediaStream>? = null,
    val videoType: String? = null,
    val partCount: Int? = null,
    val mediaSourceCount: Int? = null,
    val imageTags: Map<String, String>? = null,
    val backdropImageTags: List<String>? = null,
    val screenshotImageTags: List<String>? = null,
    val parentLogoImageTag: String? = null,
    val parentArtItemId: String? = null,
    val parentArtImageTag: String? = null,
    val seriesThumbImageTag: String? = null,
    val imageBlurHashes: Map<String, Map<String, String>>? = null,
    val seriesStudio: String? = null,
    val parentThumbItemId: String? = null,
    val parentThumbImageTag: String? = null,
    val parentPrimaryImageItemId: String? = null,
    val parentPrimaryImageTag: String? = null,
    val chapters: List<ChapterInfo>? = null,
    val locationType: String? = null,
    val isoType: String? = null,
    val mediaType: String? = null,
    val endDate: String? = null,
    val lockedFields: List<String>? = null,
    val trailerCount: Int? = null,
    val movieCount: Int? = null,
    val seriesCount: Int? = null,
    val programCount: Int? = null,
    val episodeCount: Int? = null,
    val songCount: Int? = null,
    val albumCount: Int? = null,
    val artistCount: Int? = null,
    val musicVideoCount: Int? = null,
    val lockData: Boolean? = null,
    val width: Int? = null,
    val height: Int? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val software: String? = null,
    val exposureTime: Double? = null,
    val focalLength: Double? = null,
    val imageOrientation: String? = null,
    val aperture: Double? = null,
    val shutterSpeed: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val isoSpeedRating: Int? = null,
    val seriesTimerId: String? = null,
    val programId: String? = null,
    val channelPrimaryImageTag: String? = null,
    val startDate: String? = null,
    val completionPercentage: Double? = null,
    val isRepeat: Boolean? = null,
    val episodeTitle: String? = null,
    val channelType: String? = null,
    val audio: String? = null,
    val isMovie: Boolean? = null,
    val isSports: Boolean? = null,
    val isSeries: Boolean? = null,
    val isLive: Boolean? = null,
    val isNews: Boolean? = null,
    val isKids: Boolean? = null,
    val isPremiere: Boolean? = null,
    val timerId: String? = null,
    val currentProgram: BaseItemDto? = null
)

// ========================================
// SUPPORTING MODELS
// ========================================


data class ExternalUrl(
    val name: String? = null,
    val url: String? = null
)


data class MediaSourceInfo(
    val protocol: String? = null,
    val id: String? = null,
    val path: String? = null,
    val encoderPath: String? = null,
    val encoderProtocol: String? = null,
    val type: String? = null,
    val container: String? = null,
    val size: Long? = null,
    val name: String? = null,
    val isRemote: Boolean? = null,
    val etag: String? = null,
    val runTimeTicks: Long? = null,
    val readAtNativeFramerate: Boolean? = null,
    val ignoreDts: Boolean? = null,
    val ignoreIndex: Boolean? = null,
    val genPtsInput: Boolean? = null,
    val supportsTranscoding: Boolean? = null,
    val supportsDirectStream: Boolean? = null,
    val supportsDirectPlay: Boolean? = null,
    val isInfiniteStream: Boolean? = null,
    val requiresOpening: Boolean? = null,
    val openToken: String? = null,
    val requiresClosing: Boolean? = null,
    val liveStreamId: String? = null,
    val bufferMs: Int? = null,
    val requiresLooping: Boolean? = null,
    val supportsProbing: Boolean? = null,
    val videoType: String? = null,
    val isoType: String? = null,
    val video3DFormat: String? = null,
    val mediaStreams: List<MediaStream>? = null,
    val mediaAttachments: List<MediaAttachment>? = null,
    val formats: List<String>? = null,
    val bitrate: Int? = null,
    val timestamp: String? = null,
    val requiredHttpHeaders: Map<String, String>? = null,
    val transcodingUrl: String? = null,
    val transcodingSubProtocol: String? = null,
    val transcodingContainer: String? = null,
    val analyzeDurationMs: Int? = null,
    val defaultAudioStreamIndex: Int? = null,
    val defaultSubtitleStreamIndex: Int? = null
)


data class MediaStream(
    val codec: String? = null,
    val codecTag: String? = null,
    val language: String? = null,
    val colorRange: String? = null,
    val colorSpace: String? = null,
    val colorTransfer: String? = null,
    val colorPrimaries: String? = null,
    val dvVersionMajor: Int? = null,
    val dvVersionMinor: Int? = null,
    val dvProfile: Int? = null,
    val dvLevel: Int? = null,
    val rpuPresentFlag: Int? = null,
    val elPresentFlag: Int? = null,
    val blPresentFlag: Int? = null,
    val dvBlSignalCompatibilityId: Int? = null,
    val comment: String? = null,
    val timeBase: String? = null,
    val codecTimeBase: String? = null,
    val title: String? = null,
    val videoRange: String? = null,
    val videoRangeType: String? = null,
    val videoDoViTitle: String? = null,
    val localizedUndefined: String? = null,
    val localizedDefault: String? = null,
    val localizedForced: String? = null,
    val localizedExternal: String? = null,
    val displayTitle: String? = null,
    val nalLengthSize: String? = null,
    val isInterlaced: Boolean? = null,
    val isAVC: Boolean? = null,
    val channelLayout: String? = null,
    val bitRate: Int? = null,
    val bitDepth: Int? = null,
    val refFrames: Int? = null,
    val packetLength: Int? = null,
    val channels: Int? = null,
    val sampleRate: Int? = null,
    val isDefault: Boolean? = null,
    val isForced: Boolean? = null,
    val height: Int? = null,
    val width: Int? = null,
    val averageFrameRate: Float? = null,
    val realFrameRate: Float? = null,
    val profile: String? = null,
    val type: String? = null,
    val aspectRatio: String? = null,
    val index: Int? = null,
    val score: Int? = null,
    val isExternal: Boolean? = null,
    val deliveryMethod: String? = null,
    val deliveryUrl: String? = null,
    val isExternalUrl: Boolean? = null,
    val isTextSubtitleStream: Boolean? = null,
    val supportsExternalStream: Boolean? = null,
    val path: String? = null,
    val pixelFormat: String? = null,
    val level: Double? = null,
    val isAnamorphic: Boolean? = null
)


data class MediaAttachment(
    val codec: String? = null,
    val codecTag: String? = null,
    val comment: String? = null,
    val index: Int? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val deliveryUrl: String? = null
)


data class UserItemDataDto(
    val rating: Double? = null,
    val playedPercentage: Double? = null,
    val unplayedItemCount: Int? = null,
    val playbackPositionTicks: Long? = null,
    val playCount: Int? = null,
    val isFavorite: Boolean? = null,
    val likes: Boolean? = null,
    val lastPlayedDate: String? = null,
    val played: Boolean? = null,
    val key: String? = null,
    val itemId: String? = null
)


data class NameGuidPair(
    val name: String? = null,
    val id: String? = null
)


data class ChapterInfo(
    val startPositionTicks: Long? = null,
    val name: String? = null,
    val imagePath: String? = null,
    val imageDateModified: String? = null,
    val imageTag: String? = null
)


data class SessionUserInfo(
    val userId: String? = null,
    val userName: String? = null
)


data class ClientCapabilities(
    val playableMediaTypes: List<String>? = null,
    val supportedCommands: List<String>? = null,
    val supportsMediaControl: Boolean? = null,
    val supportsContentUploading: Boolean? = null,
    val messageCallbackUrl: String? = null,
    val supportsPersistentIdentifier: Boolean? = null,
    val supportsSync: Boolean? = null,
    val deviceProfile: DeviceProfile? = null,
    val appStoreUrl: String? = null,
    val iconUrl: String? = null
)


data class DeviceProfile(
    val name: String? = null,
    val id: String? = null,
    val identification: DeviceIdentification? = null,
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val manufacturerUrl: String? = null,
    val modelName: String? = null,
    val modelDescription: String? = null,
    val modelNumber: String? = null,
    val modelUrl: String? = null,
    val serialNumber: String? = null,
    val enableAlbumArtInDidl: Boolean? = null,
    val enableSingleAlbumArtLimit: Boolean? = null,
    val enableSingleSubtitleLimit: Boolean? = null,
    val supportedMediaTypes: String? = null,
    val userId: String? = null,
    val albumArtPn: String? = null,
    val maxAlbumArtWidth: Int? = null,
    val maxAlbumArtHeight: Int? = null,
    val maxIconWidth: Int? = null,
    val maxIconHeight: Int? = null,
    val maxStreamingBitrate: Long? = null,
    val maxStaticBitrate: Long? = null,
    val musicStreamingTranscodingBitrate: Int? = null,
    val maxStaticMusicBitrate: Int? = null,
    val sonyAggregationFlags: String? = null,
    val protocolInfo: String? = null,
    val timelineOffsetSeconds: Int? = null,
    val requiresPlainVideoItems: Boolean? = null,
    val requiresPlainFolders: Boolean? = null,
    val enableMSMediaReceiverRegistrar: Boolean? = null,
    val ignoreTranscodeByteRangeRequests: Boolean? = null,
    val xmlRootAttributes: List<XmlAttribute>? = null,
    val directPlayProfiles: List<DirectPlayProfile>? = null,
    val transcodingProfiles: List<TranscodingProfile>? = null,
    val containerProfiles: List<ContainerProfile>? = null,
    val codecProfiles: List<CodecProfile>? = null,
    val responseProfiles: List<ResponseProfile>? = null,
    val subtitleProfiles: List<SubtitleProfile>? = null
)


data class DeviceIdentification(
    val friendlyName: String? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val modelName: String? = null,
    val modelDescription: String? = null,
    val modelUrl: String? = null,
    val manufacturer: String? = null,
    val manufacturerUrl: String? = null,
    val headers: List<HttpHeaderInfo>? = null
)


data class XmlAttribute(
    val name: String? = null,
    val value: String? = null
)


data class DirectPlayProfile(
    val container: String? = null,
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val type: String? = null
)


data class TranscodingProfile(
    val container: String? = null,
    val type: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val protocol: String? = null,
    val estimateContentLength: Boolean? = null,
    val enableMpegtsM2TsMode: Boolean? = null,
    val transcodeSeekInfo: String? = null,
    val copyTimestamps: Boolean? = null,
    val context: String? = null,
    val enableSubtitlesInManifest: Boolean? = null,
    val maxAudioChannels: String? = null,
    val minSegments: Int? = null,
    val segmentLength: Int? = null,
    val breakOnNonKeyFrames: Boolean? = null,
    val conditions: List<ProfileCondition>? = null
)


data class ContainerProfile(
    val type: String? = null,
    val conditions: List<ProfileCondition>? = null,
    val container: String? = null
)


data class CodecProfile(
    val type: String? = null,
    val conditions: List<ProfileCondition>? = null,
    val applyConditions: List<ProfileCondition>? = null,
    val codec: String? = null,
    val container: String? = null
)


data class ResponseProfile(
    val container: String? = null,
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val type: String? = null,
    val orgPn: String? = null,
    val mimeType: String? = null,
    val conditions: List<ProfileCondition>? = null
)


data class SubtitleProfile(
    val format: String? = null,
    val method: String? = null,
    val didlMode: String? = null,
    val language: String? = null,
    val container: String? = null
)


data class ProfileCondition(
    val condition: String? = null,
    val property: String? = null,
    val value: String? = null,
    val isRequired: Boolean? = null
)


data class HttpHeaderInfo(
    val name: String? = null,
    val value: String? = null,
    val match: String? = null
)


data class TranscodingInfo(
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val container: String? = null,
    val isVideoDirect: Boolean? = null,
    val isAudioDirect: Boolean? = null,
    val bitrate: Int? = null,
    val framerate: Float? = null,
    val completionPercentage: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val audioChannels: Int? = null,
    val hardwareAccelerationType: String? = null,
    val transcodeReasons: List<String>? = null
)


data class QueueItem(
    val id: String? = null,
    val playlistItemId: String? = null
)
