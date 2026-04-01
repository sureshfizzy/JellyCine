package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BaseItemDto(
    @SerialName("Name")
    val name: String? = null,
    
    @SerialName("OriginalTitle")
    val originalTitle: String? = null,
    
    @SerialName("ServerId")
    val serverId: String? = null,
    
    @SerialName("Id")
    val id: String? = null,
    
    @SerialName("Etag")
    val etag: String? = null,
    
    @SerialName("DateCreated")
    val dateCreated: String? = null,
    
    @SerialName("DateLastMediaAdded")
    val dateLastMediaAdded: String? = null,
    
    @SerialName("CanDelete")
    val canDelete: Boolean? = null,
    
    @SerialName("CanDownload")
    val canDownload: Boolean? = null,
    
    @SerialName("HasSubtitles")
    val hasSubtitles: Boolean? = null,
    
    @SerialName("Container")
    val container: String? = null,
    
    @SerialName("SortName")
    val sortName: String? = null,
    
    @SerialName("ForcedSortName")
    val forcedSortName: String? = null,
    
    @SerialName("Video3DFormat")
    val video3DFormat: String? = null,
    
    @SerialName("PremiereDate")
    val premiereDate: String? = null,
    
    @SerialName("ExternalUrls")
    val externalUrls: List<ExternalUrl>? = null,
    
    @SerialName("MediaSources")
    val mediaSources: List<MediaSourceInfo>? = null,
    
    @SerialName("CriticRating")
    val criticRating: Float? = null,
    
    @SerialName("ProductionLocations")
    val productionLocations: List<String>? = null,
    
    @SerialName("Path")
    val path: String? = null,
    
    @SerialName("EnableMediaSourceDisplay")
    val enableMediaSourceDisplay: Boolean? = null,
    
    @SerialName("OfficialRating")
    val officialRating: String? = null,
    
    @SerialName("CustomRating")
    val customRating: String? = null,
    
    @SerialName("ChannelId")
    val channelId: String? = null,
    
    @SerialName("ChannelName")
    val channelName: String? = null,
    
    @SerialName("Overview")
    val overview: String? = null,
    
    @SerialName("Taglines")
    val taglines: List<String>? = null,
    
    @SerialName("Genres")
    val genres: List<String>? = null,
    
    @SerialName("CommunityRating")
    val communityRating: Float? = null,
    
    @SerialName("CumulativeRunTimeTicks")
    val cumulativeRunTimeTicks: Long? = null,
    
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    
    @SerialName("PlayAccess")
    val playAccess: String? = null,
    
    @SerialName("AspectRatio")
    val aspectRatio: String? = null,
    
    @SerialName("ProductionYear")
    val productionYear: Int? = null,
    
    @SerialName("IsPlaceHolder")
    val isPlaceHolder: Boolean? = null,
    
    @SerialName("Number")
    val number: String? = null,
    
    @SerialName("ChannelNumber")
    val channelNumber: String? = null,
    
    @SerialName("IndexNumber")
    val indexNumber: Int? = null,
    
    @SerialName("IndexNumberEnd")
    val indexNumberEnd: Int? = null,
    
    @SerialName("ParentIndexNumber")
    val parentIndexNumber: Int? = null,
    
    @SerialName("RemoteTrailers")
    val remoteTrailers: List<MediaUrl>? = null,
    
    @SerialName("ProviderIds")
    val providerIds: Map<String, String>? = null,
    
    @SerialName("IsHD")
    val isHD: Boolean? = null,
    
    @SerialName("IsFolder")
    val isFolder: Boolean? = null,
    
    @SerialName("ParentId")
    val parentId: String? = null,
    
    @SerialName("Type")
    val type: String? = null,
    
    @SerialName("People")
    val people: List<BaseItemPerson>? = null,
    
    @SerialName("Studios")
    val studios: List<NameGuidPair>? = null,
    
    @SerialName("GenreItems")
    val genreItems: List<NameGuidPair>? = null,
    
    @SerialName("ParentLogoItemId")
    val parentLogoItemId: String? = null,
    
    @SerialName("ParentBackdropItemId")
    val parentBackdropItemId: String? = null,
    
    @SerialName("ParentBackdropImageTags")
    val parentBackdropImageTags: List<String>? = null,
    
    @SerialName("LocalTrailerCount")
    val localTrailerCount: Int? = null,
    
    @SerialName("UserData")
    val userData: UserItemDataDto? = null,
    
    @SerialName("RecursiveItemCount")
    val recursiveItemCount: Int? = null,
    
    @SerialName("ChildCount")
    val childCount: Int? = null,
    
    @SerialName("SeriesName")
    val seriesName: String? = null,
    
    @SerialName("SeriesId")
    val seriesId: String? = null,
    
    @SerialName("SeasonId")
    val seasonId: String? = null,
    
    @SerialName("SpecialFeatureCount")
    val specialFeatureCount: Int? = null,
    
    @SerialName("DisplayPreferencesId")
    val displayPreferencesId: String? = null,
    
    @SerialName("Status")
    val status: String? = null,
    
    @SerialName("AirTime")
    val airTime: String? = null,
    
    @SerialName("AirDays")
    val airDays: List<String>? = null,
    
    @SerialName("Tags")
    val tags: List<String>? = null,
    
    @SerialName("PrimaryImageAspectRatio")
    val primaryImageAspectRatio: Double? = null,
    
    @SerialName("Artists")
    val artists: List<String>? = null,
    
    @SerialName("ArtistItems")
    val artistItems: List<NameGuidPair>? = null,
    
    @SerialName("Album")
    val album: String? = null,
    
    @SerialName("CollectionType")
    val collectionType: String? = null,
    
    @SerialName("DisplayOrder")
    val displayOrder: String? = null,
    
    @SerialName("AlbumId")
    val albumId: String? = null,
    
    @SerialName("AlbumPrimaryImageTag")
    val albumPrimaryImageTag: String? = null,
    
    @SerialName("SeriesPrimaryImageTag")
    val seriesPrimaryImageTag: String? = null,
    
    @SerialName("AlbumArtist")
    val albumArtist: String? = null,
    
    @SerialName("AlbumArtists")
    val albumArtists: List<NameGuidPair>? = null,
    
    @SerialName("SeasonName")
    val seasonName: String? = null,
    
    @SerialName("MediaStreams")
    val mediaStreams: List<MediaStream>? = null,
    
    @SerialName("VideoType")
    val videoType: String? = null,
    
    @SerialName("PartCount")
    val partCount: Int? = null,
    
    @SerialName("MediaSourceCount")
    val mediaSourceCount: Int? = null,
    
    @SerialName("ImageTags")
    val imageTags: Map<String, String>? = null,
    
    @SerialName("BackdropImageTags")
    val backdropImageTags: List<String>? = null,
    
    @SerialName("ScreenshotImageTags")
    val screenshotImageTags: List<String>? = null,
    
    @SerialName("ParentLogoImageTag")
    val parentLogoImageTag: String? = null,
    
    @SerialName("ParentArtItemId")
    val parentArtItemId: String? = null,
    
    @SerialName("ParentArtImageTag")
    val parentArtImageTag: String? = null,
    
    @SerialName("SeriesThumbImageTag")
    val seriesThumbImageTag: String? = null,
    
    @SerialName("ImageBlurHashes")
    val imageBlurHashes: Map<String, Map<String, String>>? = null,
    
    @SerialName("SeriesStudio")
    val seriesStudio: String? = null,
    
    @SerialName("ParentThumbItemId")
    val parentThumbItemId: String? = null,
    
    @SerialName("ParentThumbImageTag")
    val parentThumbImageTag: String? = null,
    
    @SerialName("ParentPrimaryImageItemId")
    val parentPrimaryImageItemId: String? = null,
    
    @SerialName("ParentPrimaryImageTag")
    val parentPrimaryImageTag: String? = null,
    
    @SerialName("Chapters")
    val chapters: List<ChapterInfo>? = null,
    
    @SerialName("LocationType")
    val locationType: String? = null,
    
    @SerialName("IsoType")
    val isoType: String? = null,
    
    @SerialName("MediaType")
    val mediaType: String? = null,
    
    @SerialName("EndDate")
    val endDate: String? = null,
    
    @SerialName("LockedFields")
    val lockedFields: List<String>? = null,
    
    @SerialName("TrailerCount")
    val trailerCount: Int? = null,
    
    @SerialName("MovieCount")
    val movieCount: Int? = null,
    
    @SerialName("SeriesCount")
    val seriesCount: Int? = null,
    
    @SerialName("ProgramCount")
    val programCount: Int? = null,
    
    @SerialName("EpisodeCount")
    val episodeCount: Int? = null,
    
    @SerialName("SongCount")
    val songCount: Int? = null,
    
    @SerialName("AlbumCount")
    val albumCount: Int? = null,
    
    @SerialName("ArtistCount")
    val artistCount: Int? = null,
    
    @SerialName("MusicVideoCount")
    val musicVideoCount: Int? = null,
    
    @SerialName("LockData")
    val lockData: Boolean? = null,
    
    @SerialName("Width")
    val width: Int? = null,
    
    @SerialName("Height")
    val height: Int? = null,
    
    @SerialName("CameraMake")
    val cameraMake: String? = null,
    
    @SerialName("CameraModel")
    val cameraModel: String? = null,
    
    @SerialName("Software")
    val software: String? = null,
    
    @SerialName("ExposureTime")
    val exposureTime: Double? = null,
    
    @SerialName("FocalLength")
    val focalLength: Double? = null,
    
    @SerialName("ImageOrientation")
    val imageOrientation: String? = null,
    
    @SerialName("Aperture")
    val aperture: Double? = null,
    
    @SerialName("ShutterSpeed")
    val shutterSpeed: Double? = null,
    
    @SerialName("Latitude")
    val latitude: Double? = null,
    
    @SerialName("Longitude")
    val longitude: Double? = null,
    
    @SerialName("Altitude")
    val altitude: Double? = null,
    
    @SerialName("IsoSpeedRating")
    val isoSpeedRating: Int? = null,
    
    @SerialName("SeriesTimerId")
    val seriesTimerId: String? = null,
    
    @SerialName("ProgramId")
    val programId: String? = null,
    
    @SerialName("ChannelPrimaryImageTag")
    val channelPrimaryImageTag: String? = null,
    
    @SerialName("StartDate")
    val startDate: String? = null,
    
    @SerialName("CompletionPercentage")
    val completionPercentage: Double? = null,
    
    @SerialName("IsRepeat")
    val isRepeat: Boolean? = null,
    
    @SerialName("EpisodeTitle")
    val episodeTitle: String? = null,
    
    @SerialName("ChannelType")
    val channelType: String? = null,
    
    @SerialName("Audio")
    val audio: String? = null,
    
    @SerialName("IsMovie")
    val isMovie: Boolean? = null,
    
    @SerialName("IsSports")
    val isSports: Boolean? = null,
    
    @SerialName("IsSeries")
    val isSeries: Boolean? = null,
    
    @SerialName("IsLive")
    val isLive: Boolean? = null,
    
    @SerialName("IsNews")
    val isNews: Boolean? = null,
    
    @SerialName("IsKids")
    val isKids: Boolean? = null,
    
    @SerialName("IsPremiere")
    val isPremiere: Boolean? = null,
    
    @SerialName("TimerId")
    val timerId: String? = null,
    
    @SerialName("CurrentProgram")
    val currentProgram: BaseItemDto? = null
)
