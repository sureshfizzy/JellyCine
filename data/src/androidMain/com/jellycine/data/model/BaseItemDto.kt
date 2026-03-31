package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

data class BaseItemDto(
    @SerializedName("Name")
    val name: String? = null,
    
    @SerializedName("OriginalTitle")
    val originalTitle: String? = null,
    
    @SerializedName("ServerId")
    val serverId: String? = null,
    
    @SerializedName("Id")
    val id: String? = null,
    
    @SerializedName("Etag")
    val etag: String? = null,
    
    @SerializedName("DateCreated")
    val dateCreated: String? = null,
    
    @SerializedName("DateLastMediaAdded")
    val dateLastMediaAdded: String? = null,
    
    @SerializedName("CanDelete")
    val canDelete: Boolean? = null,
    
    @SerializedName("CanDownload")
    val canDownload: Boolean? = null,
    
    @SerializedName("HasSubtitles")
    val hasSubtitles: Boolean? = null,
    
    @SerializedName("Container")
    val container: String? = null,
    
    @SerializedName("SortName")
    val sortName: String? = null,
    
    @SerializedName("ForcedSortName")
    val forcedSortName: String? = null,
    
    @SerializedName("Video3DFormat")
    val video3DFormat: String? = null,
    
    @SerializedName("PremiereDate")
    val premiereDate: String? = null,
    
    @SerializedName("ExternalUrls")
    val externalUrls: List<ExternalUrl>? = null,
    
    @SerializedName("MediaSources")
    val mediaSources: List<MediaSourceInfo>? = null,
    
    @SerializedName("CriticRating")
    val criticRating: Float? = null,
    
    @SerializedName("ProductionLocations")
    val productionLocations: List<String>? = null,
    
    @SerializedName("Path")
    val path: String? = null,
    
    @SerializedName("EnableMediaSourceDisplay")
    val enableMediaSourceDisplay: Boolean? = null,
    
    @SerializedName("OfficialRating")
    val officialRating: String? = null,
    
    @SerializedName("CustomRating")
    val customRating: String? = null,
    
    @SerializedName("ChannelId")
    val channelId: String? = null,
    
    @SerializedName("ChannelName")
    val channelName: String? = null,
    
    @SerializedName("Overview")
    val overview: String? = null,
    
    @SerializedName("Taglines")
    val taglines: List<String>? = null,
    
    @SerializedName("Genres")
    val genres: List<String>? = null,
    
    @SerializedName("CommunityRating")
    val communityRating: Float? = null,
    
    @SerializedName("CumulativeRunTimeTicks")
    val cumulativeRunTimeTicks: Long? = null,
    
    @SerializedName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    
    @SerializedName("PlayAccess")
    val playAccess: String? = null,
    
    @SerializedName("AspectRatio")
    val aspectRatio: String? = null,
    
    @SerializedName("ProductionYear")
    val productionYear: Int? = null,
    
    @SerializedName("IsPlaceHolder")
    val isPlaceHolder: Boolean? = null,
    
    @SerializedName("Number")
    val number: String? = null,
    
    @SerializedName("ChannelNumber")
    val channelNumber: String? = null,
    
    @SerializedName("IndexNumber")
    val indexNumber: Int? = null,
    
    @SerializedName("IndexNumberEnd")
    val indexNumberEnd: Int? = null,
    
    @SerializedName("ParentIndexNumber")
    val parentIndexNumber: Int? = null,
    
    @SerializedName("RemoteTrailers")
    val remoteTrailers: List<MediaUrl>? = null,
    
    @SerializedName("ProviderIds")
    val providerIds: Map<String, String>? = null,
    
    @SerializedName("IsHD")
    val isHD: Boolean? = null,
    
    @SerializedName("IsFolder")
    val isFolder: Boolean? = null,
    
    @SerializedName("ParentId")
    val parentId: String? = null,
    
    @SerializedName("Type")
    val type: String? = null,
    
    @SerializedName("People")
    val people: List<BaseItemPerson>? = null,
    
    @SerializedName("Studios")
    val studios: List<NameGuidPair>? = null,
    
    @SerializedName("GenreItems")
    val genreItems: List<NameGuidPair>? = null,
    
    @SerializedName("ParentLogoItemId")
    val parentLogoItemId: String? = null,
    
    @SerializedName("ParentBackdropItemId")
    val parentBackdropItemId: String? = null,
    
    @SerializedName("ParentBackdropImageTags")
    val parentBackdropImageTags: List<String>? = null,
    
    @SerializedName("LocalTrailerCount")
    val localTrailerCount: Int? = null,
    
    @SerializedName("UserData")
    val userData: UserItemDataDto? = null,
    
    @SerializedName("RecursiveItemCount")
    val recursiveItemCount: Int? = null,
    
    @SerializedName("ChildCount")
    val childCount: Int? = null,
    
    @SerializedName("SeriesName")
    val seriesName: String? = null,
    
    @SerializedName("SeriesId")
    val seriesId: String? = null,
    
    @SerializedName("SeasonId")
    val seasonId: String? = null,
    
    @SerializedName("SpecialFeatureCount")
    val specialFeatureCount: Int? = null,
    
    @SerializedName("DisplayPreferencesId")
    val displayPreferencesId: String? = null,
    
    @SerializedName("Status")
    val status: String? = null,
    
    @SerializedName("AirTime")
    val airTime: String? = null,
    
    @SerializedName("AirDays")
    val airDays: List<String>? = null,
    
    @SerializedName("Tags")
    val tags: List<String>? = null,
    
    @SerializedName("PrimaryImageAspectRatio")
    val primaryImageAspectRatio: Double? = null,
    
    @SerializedName("Artists")
    val artists: List<String>? = null,
    
    @SerializedName("ArtistItems")
    val artistItems: List<NameGuidPair>? = null,
    
    @SerializedName("Album")
    val album: String? = null,
    
    @SerializedName("CollectionType")
    val collectionType: String? = null,
    
    @SerializedName("DisplayOrder")
    val displayOrder: String? = null,
    
    @SerializedName("AlbumId")
    val albumId: String? = null,
    
    @SerializedName("AlbumPrimaryImageTag")
    val albumPrimaryImageTag: String? = null,
    
    @SerializedName("SeriesPrimaryImageTag")
    val seriesPrimaryImageTag: String? = null,
    
    @SerializedName("AlbumArtist")
    val albumArtist: String? = null,
    
    @SerializedName("AlbumArtists")
    val albumArtists: List<NameGuidPair>? = null,
    
    @SerializedName("SeasonName")
    val seasonName: String? = null,
    
    @SerializedName("MediaStreams")
    val mediaStreams: List<MediaStream>? = null,
    
    @SerializedName("VideoType")
    val videoType: String? = null,
    
    @SerializedName("PartCount")
    val partCount: Int? = null,
    
    @SerializedName("MediaSourceCount")
    val mediaSourceCount: Int? = null,
    
    @SerializedName("ImageTags")
    val imageTags: Map<String, String>? = null,
    
    @SerializedName("BackdropImageTags")
    val backdropImageTags: List<String>? = null,
    
    @SerializedName("ScreenshotImageTags")
    val screenshotImageTags: List<String>? = null,
    
    @SerializedName("ParentLogoImageTag")
    val parentLogoImageTag: String? = null,
    
    @SerializedName("ParentArtItemId")
    val parentArtItemId: String? = null,
    
    @SerializedName("ParentArtImageTag")
    val parentArtImageTag: String? = null,
    
    @SerializedName("SeriesThumbImageTag")
    val seriesThumbImageTag: String? = null,
    
    @SerializedName("ImageBlurHashes")
    val imageBlurHashes: Map<String, Map<String, String>>? = null,
    
    @SerializedName("SeriesStudio")
    val seriesStudio: String? = null,
    
    @SerializedName("ParentThumbItemId")
    val parentThumbItemId: String? = null,
    
    @SerializedName("ParentThumbImageTag")
    val parentThumbImageTag: String? = null,
    
    @SerializedName("ParentPrimaryImageItemId")
    val parentPrimaryImageItemId: String? = null,
    
    @SerializedName("ParentPrimaryImageTag")
    val parentPrimaryImageTag: String? = null,
    
    @SerializedName("Chapters")
    val chapters: List<ChapterInfo>? = null,
    
    @SerializedName("LocationType")
    val locationType: String? = null,
    
    @SerializedName("IsoType")
    val isoType: String? = null,
    
    @SerializedName("MediaType")
    val mediaType: String? = null,
    
    @SerializedName("EndDate")
    val endDate: String? = null,
    
    @SerializedName("LockedFields")
    val lockedFields: List<String>? = null,
    
    @SerializedName("TrailerCount")
    val trailerCount: Int? = null,
    
    @SerializedName("MovieCount")
    val movieCount: Int? = null,
    
    @SerializedName("SeriesCount")
    val seriesCount: Int? = null,
    
    @SerializedName("ProgramCount")
    val programCount: Int? = null,
    
    @SerializedName("EpisodeCount")
    val episodeCount: Int? = null,
    
    @SerializedName("SongCount")
    val songCount: Int? = null,
    
    @SerializedName("AlbumCount")
    val albumCount: Int? = null,
    
    @SerializedName("ArtistCount")
    val artistCount: Int? = null,
    
    @SerializedName("MusicVideoCount")
    val musicVideoCount: Int? = null,
    
    @SerializedName("LockData")
    val lockData: Boolean? = null,
    
    @SerializedName("Width")
    val width: Int? = null,
    
    @SerializedName("Height")
    val height: Int? = null,
    
    @SerializedName("CameraMake")
    val cameraMake: String? = null,
    
    @SerializedName("CameraModel")
    val cameraModel: String? = null,
    
    @SerializedName("Software")
    val software: String? = null,
    
    @SerializedName("ExposureTime")
    val exposureTime: Double? = null,
    
    @SerializedName("FocalLength")
    val focalLength: Double? = null,
    
    @SerializedName("ImageOrientation")
    val imageOrientation: String? = null,
    
    @SerializedName("Aperture")
    val aperture: Double? = null,
    
    @SerializedName("ShutterSpeed")
    val shutterSpeed: Double? = null,
    
    @SerializedName("Latitude")
    val latitude: Double? = null,
    
    @SerializedName("Longitude")
    val longitude: Double? = null,
    
    @SerializedName("Altitude")
    val altitude: Double? = null,
    
    @SerializedName("IsoSpeedRating")
    val isoSpeedRating: Int? = null,
    
    @SerializedName("SeriesTimerId")
    val seriesTimerId: String? = null,
    
    @SerializedName("ProgramId")
    val programId: String? = null,
    
    @SerializedName("ChannelPrimaryImageTag")
    val channelPrimaryImageTag: String? = null,
    
    @SerializedName("StartDate")
    val startDate: String? = null,
    
    @SerializedName("CompletionPercentage")
    val completionPercentage: Double? = null,
    
    @SerializedName("IsRepeat")
    val isRepeat: Boolean? = null,
    
    @SerializedName("EpisodeTitle")
    val episodeTitle: String? = null,
    
    @SerializedName("ChannelType")
    val channelType: String? = null,
    
    @SerializedName("Audio")
    val audio: String? = null,
    
    @SerializedName("IsMovie")
    val isMovie: Boolean? = null,
    
    @SerializedName("IsSports")
    val isSports: Boolean? = null,
    
    @SerializedName("IsSeries")
    val isSeries: Boolean? = null,
    
    @SerializedName("IsLive")
    val isLive: Boolean? = null,
    
    @SerializedName("IsNews")
    val isNews: Boolean? = null,
    
    @SerializedName("IsKids")
    val isKids: Boolean? = null,
    
    @SerializedName("IsPremiere")
    val isPremiere: Boolean? = null,
    
    @SerializedName("TimerId")
    val timerId: String? = null,
    
    @SerializedName("CurrentProgram")
    val currentProgram: BaseItemDto? = null
)
