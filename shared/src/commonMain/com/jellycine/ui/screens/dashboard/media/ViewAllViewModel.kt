package com.jellycine.ui.screens.dashboard.media

import com.jellycine.data.model.*
import com.jellycine.data.repository.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Seerr-backed studio and network catalogs. Platforms without Seerr pass `null` and use the local library. */
interface ViewAllSeerrSource {
    fun isConnected(scopeId: String): Boolean

    suspend fun getStudios(
        scopeId: String,
        studioId: String,
        limit: Int,
        startIndex: Int
    ): Result<QueryResult<BaseItemDto>>

    suspend fun getNetworks(
        scopeId: String,
        networkId: String,
        limit: Int,
        startIndex: Int
    ): Result<QueryResult<BaseItemDto>>
}

class ViewAllViewModel(
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository,
    private val awardsRepository: AwardsRepository?,
    private val seerr: ViewAllSeerrSource?,
    private val contentType: ContentType,
    private val parentId: String? = null,
    private val genreId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewAllUiState())
    val uiState: StateFlow<ViewAllUiState> = _uiState.asStateFlow()

    private val _items = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val items: StateFlow<List<BaseItemDto>> = _items.asStateFlow()

    private val _serverGenres = MutableStateFlow<List<String>>(emptyList())

    /** Items to show: genre-filtered (all selected genres must match) and de-duplicated. */
    val displayItems: StateFlow<List<BaseItemDto>> = combine(_items, uiState.map { it.selectedGenres }) { items, genres ->
        val filtered = if (genres.size > 1) {
            items.filter { item -> item.genres.orEmpty().toSet().containsAll(genres) }
        } else {
            items
        }
        filtered.distinctBy(::viewAllItemKey)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val availableGenres: StateFlow<List<String>> = combine(_serverGenres, uiState.map { it.selectedGenres }) { genres, selected ->
        (genres + selected).distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Logo for the Seerr studio or network being shown, if it is one of the bundled catalog entries. */
    val seerrLogoUrl: String? = when (contentType) {
        ContentType.SEERR_STUDIO -> SeerrCatalog.popularStudios()
        ContentType.SEERR_NETWORK -> SeerrCatalog.popularNetworks()
        else -> emptyList()
    }.firstOrNull { it.id == parentId }?.logoUrl

    private val isWatchedRequest = parentId == WATCHED_VIEW_ALL_PARENT_ID
    private val isFavoritesRequest = parentId == FAVORITES_VIEW_ALL_PARENT_ID

    private val genreIncludeItemTypes: String? = if (isWatchedRequest || isFavoritesRequest) {
        null
    } else when (contentType) {
        ContentType.MOVIES, ContentType.MOVIES_GENRE -> "Movie"
        ContentType.SERIES, ContentType.TVSHOWS_GENRE -> "Series"
        ContentType.ALL -> "Movie,Series"
        ContentType.EPISODES,
        ContentType.SEERR_STUDIO,
        ContentType.SEERR_NETWORK,
        ContentType.AWARD -> null
    }

    private var currentPage = 0
    private val pageSize = 50
    private var totalItems = 0
    private var hasMorePages = true

    init {
        loadItems(refresh = true)
        loadGenres()
    }

    private fun loadGenres() {
        val includeItemTypes = genreIncludeItemTypes ?: return
        viewModelScope.launch {
            mediaRepository.getFilteredGenres(parentId = parentId, includeItemTypes = includeItemTypes).fold(
                onSuccess = { genres ->
                    _serverGenres.value = genres.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
                },
                onFailure = { _serverGenres.value = emptyList() }
            )
        }
    }

    /** Series lists change when playback marks things watched, so they reload on a user-data refresh. */
    fun onUserDataChanged() {
        if (contentType.includesSeriesItems()) loadItems(refresh = true)
    }

    fun loadItems(refresh: Boolean = false) {
        if (refresh) {
            currentPage = 0
            hasMorePages = true
        }

        if (!hasMorePages && !refresh) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                withContext(Dispatchers.Default) {
                    val selectedGenres = _uiState.value.selectedGenres
                        .toList()
                        .sorted()
                        .joinToString("|")
                        .ifBlank { null }
                    val selectedGenreIds = genreId?.takeIf { it.isNotBlank() }
                    val result = when (contentType) {
                        ContentType.SEERR_STUDIO -> {
                            val scopeId = authRepository.getActiveSessionSnapshot().activeServerId.orEmpty()
                            if (seerr != null && seerr.isConnected(scopeId)) {
                                seerr.getStudios(
                                    scopeId = scopeId,
                                    studioId = parentId.orEmpty(),
                                    limit = pageSize,
                                    startIndex = currentPage * pageSize
                                )
                            } else {
                                loadLocalCatalogItems(
                                    SeerrCatalog.studioById(parentId.orEmpty()),
                                    includeItemTypes = "Movie"
                                )
                            }
                        }
                        ContentType.SEERR_NETWORK -> {
                            val scopeId = authRepository.getActiveSessionSnapshot().activeServerId.orEmpty()
                            if (seerr != null && seerr.isConnected(scopeId)) {
                                seerr.getNetworks(
                                    scopeId = scopeId,
                                    networkId = parentId.orEmpty(),
                                    limit = pageSize,
                                    startIndex = currentPage * pageSize
                                )
                            } else {
                                loadLocalCatalogItems(
                                    SeerrCatalog.networkById(parentId.orEmpty()),
                                    includeItemTypes = "Series"
                                )
                            }
                        }
                        ContentType.MOVIES -> if (isWatchedRequest) {
                            mediaRepository.loadWatchedItems("Movie")
                                .map { QueryResult(items = it, totalRecordCount = it.size, startIndex = 0) }
                        } else if (isFavoritesRequest) {
                            mediaRepository.getFavoriteItems(includeItemTypes = "Movie")
                        } else mediaRepository.getUserItems(
                            parentId = parentId,
                            genres = selectedGenres,
                            includeItemTypes = "Movie,BoxSet",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,CriticRating,ProductionYear,Overview,UserData"
                        )
                        ContentType.SERIES -> if (isWatchedRequest) {
                            mediaRepository.loadWatchedItems("Episode")
                                .mapCatching { mediaRepository.loadSeriesForWatchedEpisodes(it).getOrThrow() }
                                .map { QueryResult(items = it, totalRecordCount = it.size, startIndex = 0) }
                        } else if (isFavoritesRequest) {
                            mediaRepository.getFavoriteItems(includeItemTypes = "Series")
                        } else mediaRepository.getUserItems(
                            parentId = parentId,
                            genres = selectedGenres,
                            includeItemTypes = "Series",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,CriticRating,ProductionYear,Overview,UserData"
                        )
                        ContentType.EPISODES -> if (isWatchedRequest) {
                            mediaRepository.loadWatchedItems("Episode")
                                .map { QueryResult(items = it, totalRecordCount = it.size, startIndex = 0) }
                        } else if (isFavoritesRequest) {
                            mediaRepository.getFavoriteItems(includeItemTypes = "Episode")
                        } else mediaRepository.getUserItems(
                            parentId = parentId,
                            genres = selectedGenres,
                            includeItemTypes = "Episode",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "SeriesName,SeriesId,SeasonName,SeasonId,Overview,UserData"
                        )
                        ContentType.MOVIES_GENRE -> mediaRepository.getUserItems(
                            parentId = parentId,
                            genres = selectedGenres,
                            genreIds = selectedGenreIds,
                            includeItemTypes = "Movie",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,CriticRating,ProductionYear,Overview,UserData"
                        )
                        ContentType.TVSHOWS_GENRE -> mediaRepository.getUserItems(
                            parentId = parentId,
                            genres = selectedGenres,
                            genreIds = selectedGenreIds,
                            includeItemTypes = "Series",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,CriticRating,ProductionYear,Overview,UserData"
                        )
                        ContentType.ALL -> mediaRepository.getUserItems(
                            parentId = parentId,
                            genres = selectedGenres,
                            includeItemTypes = "Movie,Series",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,CriticRating,ProductionYear,Overview,UserData"
                        )
                        ContentType.AWARD -> loadAwardItems(parentId)
                    }

                    result.fold(
                        onSuccess = { queryResult ->
                            val newItems = (queryResult.items ?: emptyList()).let { fetchedItems ->
                                val selectedGenresSet = _uiState.value.selectedGenres
                                if (selectedGenresSet.size > 1) {
                                    fetchedItems.filter { item ->
                                        val itemGenres = item.genres.orEmpty().toSet()
                                        selectedGenresSet.all { genre -> itemGenres.contains(genre) }
                                    }
                                } else {
                                    fetchedItems
                                }
                            }
                            totalItems = queryResult.totalRecordCount ?: 0
                            hasMorePages = !isWatchedRequest &&
                                contentType != ContentType.AWARD &&
                                (currentPage + 1) * pageSize < totalItems

                            withContext(Dispatchers.Main) {
                                val merged = if (refresh) newItems else _items.value + newItems
                                _items.value = if (contentType.isSeerrCatalog()) {
                                    merged.distinctBy { mediaRepository.versionMergeKey(it) }
                                } else {
                                    merged
                                }
                                currentPage++
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    totalItems = totalItems,
                                    hasMorePages = hasMorePages
                                )
                            }
                        },
                        onFailure = { exception ->
                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = exception.message ?: "Unknown error occurred"
                                )
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    private suspend fun loadAwardItems(parentId: String?): Result<QueryResult<BaseItemDto>> {
        val parts = parentId?.split("_").orEmpty()
        val qid = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: return Result.success(QueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0))
        val mode = if (parts.getOrNull(1) == AwardMode.NOMINEES.name) AwardMode.NOMINEES else AwardMode.WINNERS
        val repository = awardsRepository
            ?: return Result.success(QueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0))
        val refs = repository.getCategoryRefs(listOf(qid), mode)[qid].orEmpty()
        val items = repository.hydrate(refs, limit = refs.size).map { title ->
            BaseItemDto(
                id = title.jellyfinMediaId?.takeIf { it.isNotBlank() }
                    ?: SeerrItemIds.detailId(title.tmdbId, title.mediaType),
                name = title.title,
                type = if (title.mediaType == "tv") "Series" else "Movie",
                productionYear = title.productionYear,
                providerIds = mapOf("tmdb" to title.tmdbId),
                imageUrl = title.posterUrl
            )
        }
        return Result.success(QueryResult(items = items, totalRecordCount = items.size, startIndex = 0))
    }

    private suspend fun loadLocalCatalogItems(
        catalogItem: SeerrCatalogItem?,
        includeItemTypes: String
    ): Result<QueryResult<BaseItemDto>> {
        val matchNames = catalogItem?.localMatchNames.orEmpty()
        if (matchNames.isEmpty()) {
            return Result.success(QueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0))
        }
        val studioIds = mediaRepository.resolveStudioIds(matchNames)
        if (studioIds.isEmpty()) {
            return Result.success(QueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0))
        }
        return mediaRepository.getUserItems(
            includeItemTypes = includeItemTypes,
            studioIds = studioIds.joinToString(","),
            recursive = true,
            sortBy = _uiState.value.sortBy,
            sortOrder = _uiState.value.sortOrder,
            limit = pageSize,
            startIndex = currentPage * pageSize,
            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,CriticRating,ProductionYear,Overview,UserData,ProviderIds"
        )
    }

    fun loadMoreItems() = loadItems(refresh = false)

    fun setSort(sortBy: String, sortOrder: String) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy, sortOrder = sortOrder)
        loadItems(refresh = true)
    }

    fun toggleGenreFilter(genre: String) {
        val genres = LinkedHashSet(_uiState.value.selectedGenres)
        if (!genres.remove(genre)) genres.add(genre)
        _uiState.value = _uiState.value.copy(selectedGenres = genres)
        loadItems(refresh = true)
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(selectedGenres = emptySet())
        loadItems(refresh = true)
    }
}

data class ViewAllUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortBy: String = "DateCreated",
    val sortOrder: String = "Descending",
    val selectedGenres: Set<String> = emptySet(),
    val totalItems: Int = 0,
    val hasMorePages: Boolean = true
)

data class SortOption(
    val sortBy: String,
    val sortOrder: String
)

val ViewAllSortOptions = listOf(
    SortOption("DateCreated", "Descending"),
    SortOption("SortName", "Ascending"),
    SortOption("SortName", "Descending"),
    SortOption("ProductionYear", "Descending"),
    SortOption("ProductionYear", "Ascending"),
    SortOption("CommunityRating", "Descending"),
    SortOption("CommunityRating", "Ascending")
)

fun viewAllItemKey(item: BaseItemDto): String {
    return item.id ?: "${item.name}_${item.type}_${item.seriesId}_${item.indexNumber ?: 0}"
}

private fun ContentType.includesSeriesItems(): Boolean {
    return this == ContentType.SERIES ||
        this == ContentType.TVSHOWS_GENRE ||
        this == ContentType.ALL
}