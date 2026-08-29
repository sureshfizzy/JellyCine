package com.vela.app.ui.screens.dashboard.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.app.ui.screens.dashboard.favorites.FAVORITES_VIEW_ALL_PARENT_ID
import com.vela.data.model.AwardMode
import com.vela.data.model.BaseItemDto
import com.vela.data.model.QueryResult
import com.vela.data.model.SeerrItemIds
import com.vela.data.repository.AwardsRepositoryProvider
import com.vela.data.repository.MediaRepository
import com.vela.data.repository.MediaRepositoryProvider
import com.vela.data.repository.SeerrRepository
import com.vela.shared.playback.UserDataRefreshSignals
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ViewAllViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private lateinit var mediaRepository: MediaRepository
    private val seerrRepository = SeerrRepository(context)
    private val awardsRepository = AwardsRepositoryProvider.getInstance(context)
    private val authRepository = com.vela.data.repository.AuthRepositoryProvider.getInstance(context)

    private val _uiState = MutableStateFlow(ViewAllUiState())
    val uiState: StateFlow<ViewAllUiState> = _uiState.asStateFlow()

    private val _items = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val items: StateFlow<List<BaseItemDto>> = _items.asStateFlow()

    private var currentPage = 0
    private val pageSize = 60
    private var totalItems = 0
    private var hasMorePages = true
    private var currentRequestKey: String? = null
    private var loadJob: Job? = null
    private var ensureJob: Job? = null
    @Volatile
    private var loadGeneration = 0
    private var folderStack: List<LibraryFolderNav> = emptyList()
    private var drilledGenre: LibraryGenreNav? = null

    private fun repository(): MediaRepository {
        if (!::mediaRepository.isInitialized) {
            mediaRepository = MediaRepositoryProvider.getInstance(context)
        }
        return mediaRepository
    }

    fun ensureItemsLoaded(
        contentType: ContentType,
        parentId: String? = null,
        genreId: String? = null
    ) {
        val requestKey = requestKey(contentType, parentId, genreId)
        val alreadyLoaded = currentRequestKey == requestKey &&
            (_items.value.isNotEmpty() || _uiState.value.recommendationSections.isNotEmpty())
        if (alreadyLoaded || (currentRequestKey == requestKey && ensureJob?.isActive == true)) {
            return
        }
        currentRequestKey = requestKey
        ensureJob?.cancel()
        ensureJob = viewModelScope.launch {
            val isAdmin = withContext(Dispatchers.IO) {
                repository().getCurrentUser().getOrNull()?.policy?.isAdministrator == true
            }
            _uiState.value = _uiState.value.copy(isAdministrator = isAdmin)
            loadLibrarySortPreferences(parentId)
            loadItems(contentType, parentId, refresh = true, genreId = genreId)
        }
    }

    fun refreshIfPopulated(
        contentType: ContentType,
        parentId: String? = null,
        genreId: String? = null
    ) {
        if (_items.value.isEmpty() && _uiState.value.recommendationSections.isEmpty()) {
            return
        }
        loadItems(contentType, parentId, refresh = true, genreId = genreId)
    }

    fun setBrowseTab(
        tab: LibraryBrowseTab,
        contentType: ContentType,
        parentId: String? = null,
        genreId: String? = null
    ) {
        if (_uiState.value.browseTab == tab) return
        folderStack = emptyList()
        drilledGenre = null
        _uiState.value = _uiState.value.copy(
            browseTab = tab,
            folderTitle = null,
            canGoBackInFolder = false
        )
        loadItems(contentType, parentId, refresh = true, genreId = genreId)
    }

    fun openFolder(
        folder: BaseItemDto,
        contentType: ContentType,
        parentId: String? = null,
        genreId: String? = null
    ) {
        val folderId = folder.id?.takeIf { it.isNotBlank() } ?: return
        folderStack = folderStack + LibraryFolderNav(folderId, folder.name.orEmpty())
        _uiState.value = _uiState.value.copy(
            folderTitle = folder.name,
            canGoBackInFolder = true,
            browseTab = LibraryBrowseTab.FOLDERS
        )
        loadItems(contentType, parentId, refresh = true, genreId = genreId)
    }

    fun openGenre(
        genre: BaseItemDto,
        contentType: ContentType,
        parentId: String? = null,
        genreId: String? = null
    ) {
        val id = genre.id?.takeIf { it.isNotBlank() } ?: return
        drilledGenre = LibraryGenreNav(id, genre.name.orEmpty())
        _uiState.value = _uiState.value.copy(
            folderTitle = genre.name,
            canGoBackInFolder = true,
            browseTab = LibraryBrowseTab.GENRES
        )
        loadItems(contentType, parentId, refresh = true, genreId = genreId)
    }

    fun popBrowseLevel(
        contentType: ContentType,
        parentId: String? = null,
        genreId: String? = null
    ): Boolean {
        return when {
            folderStack.isNotEmpty() -> {
                folderStack = folderStack.dropLast(1)
                _uiState.value = _uiState.value.copy(
                    folderTitle = folderStack.lastOrNull()?.name,
                    canGoBackInFolder = folderStack.isNotEmpty() || drilledGenre != null
                )
                loadItems(contentType, parentId, refresh = true, genreId = genreId)
                true
            }
            drilledGenre != null -> {
                drilledGenre = null
                _uiState.value = _uiState.value.copy(
                    folderTitle = null,
                    canGoBackInFolder = false
                )
                loadItems(contentType, parentId, refresh = true, genreId = genreId)
                true
            }
            else -> false
        }
    }

    fun loadItems(
        contentType: ContentType,
        parentId: String? = null,
        refresh: Boolean = false,
        genreId: String? = null
    ) {
        currentRequestKey = requestKey(contentType, parentId, genreId)

        if (refresh) {
            currentPage = 0
            hasMorePages = true
        }

        if (!hasMorePages && !refresh) return

        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            val showRefresh = refresh && _items.value.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !showRefresh,
                isRefreshing = showRefresh,
                error = null
            )

            try {
                val repo = repository()
                withContext(Dispatchers.IO) {
                    val selectedGenres = _uiState.value.selectedGenres
                        .toList()
                        .sorted()
                        .joinToString("|")
                        .ifBlank { null }
                    val selectedGenreIds = drilledGenre?.id ?: genreId?.takeIf { it.isNotBlank() }
                    val isWatchedRequest = parentId == WATCHED_VIEW_ALL_PARENT_ID
                    val isFavoritesRequest = parentId == FAVORITES_VIEW_ALL_PARENT_ID
                    val browseTab = _uiState.value.browseTab
                    var result = loadQuery(
                        repo = repo,
                        contentType = contentType,
                        parentId = parentId,
                        selectedGenres = selectedGenres,
                        selectedGenreIds = selectedGenreIds,
                        isWatchedRequest = isWatchedRequest,
                        isFavoritesRequest = isFavoritesRequest,
                        browseTab = browseTab
                    )
                    if (
                        result.isFailure &&
                        isUnsupportedLibrarySort(result.exceptionOrNull()) &&
                        _uiState.value.sortBy != "DateCreated" &&
                        browseTab.supportsSort()
                    ) {
                        _uiState.value = _uiState.value.copy(
                            sortBy = "DateCreated",
                            sortOrder = "Descending"
                        )
                        result = loadQuery(
                            repo = repo,
                            contentType = contentType,
                            parentId = parentId,
                            selectedGenres = selectedGenres,
                            selectedGenreIds = selectedGenreIds,
                            isWatchedRequest = isWatchedRequest,
                            isFavoritesRequest = isFavoritesRequest,
                            browseTab = browseTab
                        )
                    }

                    result.fold(
                        onSuccess = { queryResult ->
                            val newItems = (queryResult.items ?: emptyList()).let { fetchedItems ->
                                val selectedGenresSet = _uiState.value.selectedGenres
                                if (selectedGenresSet.size > 1 && browseTab == LibraryBrowseTab.ITEMS) {
                                    fetchedItems.filter { item ->
                                        val itemGenres = item.genres.orEmpty().toSet()
                                        selectedGenresSet.all { genre -> itemGenres.contains(genre) }
                                    }
                                } else {
                                    fetchedItems
                                }
                            }
                            totalItems = queryResult.totalRecordCount ?: newItems.size
                            val pagingTab = browseTab.supportsSort() ||
                                (browseTab == LibraryBrowseTab.GENRES && drilledGenre != null)
                            hasMorePages = !isWatchedRequest &&
                                contentType != ContentType.AWARD &&
                                browseTab != LibraryBrowseTab.RECOMMENDED &&
                                pagingTab &&
                                (currentPage + 1) * pageSize < totalItems

                            withContext(Dispatchers.Main) {
                                if (generation != loadGeneration) return@withContext
                                if (refresh) {
                                    _items.value = newItems
                                } else {
                                    _items.value = _items.value + newItems
                                }
                                currentPage++
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    totalItems = totalItems,
                                    hasMorePages = hasMorePages
                                )
                            }
                        },
                        onFailure = { exception ->
                            if (exception.isCancellation()) throw exception
                            withContext(Dispatchers.Main) {
                                if (generation != loadGeneration) return@withContext
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    error = exception.message ?: "Unknown error occurred"
                                )
                            }
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != loadGeneration) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    private suspend fun loadQuery(
        repo: MediaRepository,
        contentType: ContentType,
        parentId: String?,
        selectedGenres: String?,
        selectedGenreIds: String?,
        isWatchedRequest: Boolean,
        isFavoritesRequest: Boolean,
        browseTab: LibraryBrowseTab
    ): Result<QueryResult<BaseItemDto>> {
        val effectiveParentId = folderStack.lastOrNull()?.id ?: parentId
        val includeTypes = libraryIncludeTypes(contentType)
        return when {
            contentType == ContentType.SEERR_STUDIO -> seerrRepository.getStudios(
                scopeId = authRepository.getActiveSessionSnapshot().activeServerId.orEmpty(),
                studioId = parentId.orEmpty(),
                limit = pageSize,
                startIndex = currentPage * pageSize
            )
            contentType == ContentType.SEERR_NETWORK -> seerrRepository.getNetworks(
                scopeId = authRepository.getActiveSessionSnapshot().activeServerId.orEmpty(),
                networkId = parentId.orEmpty(),
                limit = pageSize,
                startIndex = currentPage * pageSize
            )
            contentType == ContentType.AWARD -> loadAwardItems(parentId)
            browseTab == LibraryBrowseTab.RECOMMENDED -> loadRecommendations(repo, contentType, parentId)
            browseTab == LibraryBrowseTab.TRAILERS -> repo.getUserItems(
                parentId = parentId,
                includeItemTypes = "Trailer",
                sortBy = _uiState.value.sortBy,
                sortOrder = _uiState.value.sortOrder,
                limit = pageSize,
                startIndex = currentPage * pageSize,
                recursive = true,
                fields = LIBRARY_ITEM_FIELDS
            )
            browseTab == LibraryBrowseTab.COLLECTIONS -> repo.getUserItems(
                parentId = parentId,
                includeItemTypes = "BoxSet",
                sortBy = _uiState.value.sortBy,
                sortOrder = _uiState.value.sortOrder,
                limit = pageSize,
                startIndex = currentPage * pageSize,
                recursive = true,
                fields = LIBRARY_ITEM_FIELDS
            )
            browseTab == LibraryBrowseTab.GENRES && drilledGenre == null -> {
                repo.getFilteredGenres(
                    parentId = parentId,
                    includeItemTypes = includeTypes
                ).map { genres ->
                    QueryResult(items = genres, totalRecordCount = genres.size, startIndex = 0)
                }
            }
            browseTab == LibraryBrowseTab.FOLDERS -> repo.getUserItems(
                parentId = effectiveParentId,
                sortBy = _uiState.value.sortBy,
                sortOrder = _uiState.value.sortOrder,
                limit = pageSize,
                startIndex = currentPage * pageSize,
                recursive = false,
                fields = LIBRARY_ITEM_FIELDS
            )
            isWatchedRequest -> when (contentType) {
                ContentType.MOVIES -> repo.loadWatchedItems("Movie")
                    .map { QueryResult(items = it, totalRecordCount = it.size, startIndex = 0) }
                ContentType.SERIES -> repo.loadWatchedItems("Episode")
                    .mapCatching { repo.loadSeriesForWatchedEpisodes(it).getOrThrow() }
                    .map { QueryResult(items = it, totalRecordCount = it.size, startIndex = 0) }
                ContentType.EPISODES -> repo.loadWatchedItems("Episode")
                    .map { QueryResult(items = it, totalRecordCount = it.size, startIndex = 0) }
                else -> repo.getUserItems(
                    parentId = parentId,
                    includeItemTypes = includeTypes,
                    sortBy = _uiState.value.sortBy,
                    sortOrder = _uiState.value.sortOrder,
                    limit = pageSize,
                    startIndex = currentPage * pageSize,
                    recursive = true,
                    fields = LIBRARY_ITEM_FIELDS
                )
            }
            isFavoritesRequest -> repo.getFavoriteItems(includeItemTypes = includeTypes)
            else -> repo.getUserItems(
                parentId = parentId,
                genres = selectedGenres,
                genreIds = selectedGenreIds,
                includeItemTypes = includeTypes,
                sortBy = _uiState.value.sortBy,
                sortOrder = _uiState.value.sortOrder,
                limit = pageSize,
                startIndex = currentPage * pageSize,
                recursive = true,
                fields = LIBRARY_ITEM_FIELDS
            )
        }
    }

    private suspend fun loadRecommendations(
        repo: MediaRepository,
        contentType: ContentType,
        parentId: String?
    ): Result<QueryResult<BaseItemDto>> {
        val sections = if (contentType == ContentType.SERIES) {
            repo.getSuggestions(mediaType = "Series", limit = 24)
                .map { items ->
                    listOf(
                        LibraryRecommendationSection(
                            recommendationType = null,
                            baselineName = null,
                            items = items
                        )
                    ).filter { it.items.isNotEmpty() }
                }
        } else {
            repo.getMovieRecommendations(
                parentId = parentId,
                categoryLimit = 8,
                itemLimit = 18
            ).map { rows ->
                rows.map { row ->
                    LibraryRecommendationSection(
                        recommendationType = row.recommendationType,
                        baselineName = row.baselineItemName,
                        items = row.items.orEmpty()
                    )
                }.filter { it.items.isNotEmpty() }
            }.recoverCatching { error ->
                val fallback = repo.getSuggestions(mediaType = "Movie", limit = 24).getOrElse { throw error }
                listOf(
                    LibraryRecommendationSection(
                        recommendationType = null,
                        baselineName = null,
                        items = fallback
                    )
                ).filter { it.items.isNotEmpty() }
            }
        }

        return sections.map { loaded ->
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(recommendationSections = loaded)
            }
            val flat = loaded.flatMap { it.items }.distinctBy { it.id }
            QueryResult(items = flat, totalRecordCount = flat.size, startIndex = 0)
        }
    }

    private fun libraryIncludeTypes(contentType: ContentType): String {
        return when (contentType) {
            ContentType.MOVIES, ContentType.MOVIES_GENRE -> "Movie"
            ContentType.SERIES, ContentType.TVSHOWS_GENRE -> "Series"
            ContentType.EPISODES -> "Episode"
            else -> "Movie,Series"
        }
    }

    private suspend fun loadAwardItems(parentId: String?): Result<QueryResult<BaseItemDto>> {
        val parts = parentId?.split("_").orEmpty()
        val qid = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: return Result.success(QueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0))
        val mode = if (parts.getOrNull(1) == AwardMode.NOMINEES.name) AwardMode.NOMINEES else AwardMode.WINNERS
        val refs = awardsRepository.getCategoryRefs(listOf(qid), mode)[qid].orEmpty()
        val items = awardsRepository.hydrate(refs, limit = refs.size).map { title ->
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

    fun loadMoreItems(contentType: ContentType, parentId: String? = null, genreId: String? = null) {
        loadItems(contentType, parentId, refresh = false, genreId = genreId)
    }

    private suspend fun loadLibrarySortPreferences(parentId: String?) {
        val libraryId = parentId?.takeIf {
            it.isNotBlank() &&
                it != WATCHED_VIEW_ALL_PARENT_ID &&
                it != FAVORITES_VIEW_ALL_PARENT_ID
        } ?: return
        val prefs = withContext(Dispatchers.IO) {
            repository().getLibrarySortPreferences(libraryId).getOrNull()
        } ?: return
        val sortBy = matchedLibrarySortBy(prefs.sortBy) ?: return
        _uiState.value = _uiState.value.copy(
            sortBy = sortBy,
            sortOrder = librarySortOrder(prefs.sortOrder, _uiState.value.sortOrder)
        )
    }

    fun setSort(sortBy: String, sortOrder: String, contentType: ContentType, parentId: String? = null, genreId: String? = null) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy, sortOrder = sortOrder)
        viewModelScope.launch {
            parentId
                ?.takeIf { it.isNotBlank() && it != WATCHED_VIEW_ALL_PARENT_ID && it != FAVORITES_VIEW_ALL_PARENT_ID }
                ?.let { libraryId ->
                    withContext(Dispatchers.IO) {
                        repository().saveLibrarySortPreferences(
                            parentId = libraryId,
                            sortBy = sortBy,
                            sortOrder = sortOrder
                        )
                    }
                }
        }
        loadItems(contentType, parentId, refresh = true, genreId = genreId)
    }

    fun toggleGenreFilter(genre: String, contentType: ContentType, parentId: String? = null, genreId: String? = null) {
        val currentGenres = LinkedHashSet(_uiState.value.selectedGenres)
        if (currentGenres.contains(genre)) {
            currentGenres.remove(genre)
        } else {
            currentGenres.add(genre)
        }
        _uiState.value = _uiState.value.copy(selectedGenres = currentGenres)
        loadItems(contentType, parentId, refresh = true, genreId = genreId)
    }

    fun clearFilters(contentType: ContentType, parentId: String? = null, genreId: String? = null) {
        _uiState.value = _uiState.value.copy(selectedGenres = emptySet())
        loadItems(contentType, parentId, refresh = true, genreId = genreId)
    }

    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    fun updateLocalItem(itemId: String, transform: (BaseItemDto) -> BaseItemDto) {
        _items.value = _items.value.map { item ->
            if (item.id == itemId) transform(item) else item
        }
        _uiState.value = _uiState.value.copy(
            recommendationSections = _uiState.value.recommendationSections.map { section ->
                section.copy(
                    items = section.items.map { item ->
                        if (item.id == itemId) transform(item) else item
                    }
                )
            }
        )
    }

    fun removeLocalItem(itemId: String) {
        _items.value = _items.value.filterNot { it.id == itemId }
        _uiState.value = _uiState.value.copy(
            recommendationSections = _uiState.value.recommendationSections.map { section ->
                section.copy(items = section.items.filterNot { it.id == itemId })
            },
            totalItems = (_uiState.value.totalItems - 1).coerceAtLeast(0)
        )
    }

    fun showActionResult(success: Boolean, detail: String? = null) {
        _uiState.value = _uiState.value.copy(
            actionMessage = detail ?: if (success) "ok" else "error"
        )
    }

    fun notifyUserData(itemId: String?, played: Boolean? = null) {
        UserDataRefreshSignals.notifyUserDataChanged(itemId, played)
    }

    private fun requestKey(contentType: ContentType, parentId: String?, genreId: String?): String {
        return listOf(
            contentType.name,
            parentId.orEmpty(),
            genreId.orEmpty(),
            _uiState.value.browseTab.name,
            folderStack.lastOrNull()?.id.orEmpty(),
            drilledGenre?.id.orEmpty(),
            _uiState.value.sortBy,
            _uiState.value.sortOrder
        ).joinToString("|")
    }

    private fun isUnsupportedLibrarySort(error: Throwable?): Boolean {
        val message = error?.message.orEmpty()
        return message.contains(": 500") ||
            message.endsWith("500") ||
            message.contains(" 500")
    }

    private fun Throwable.isCancellation(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is CancellationException) return true
            current = current.cause
        }
        return false
    }
}

data class ViewAllUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortBy: String = "DateCreated",
    val sortOrder: String = "Descending",
    val selectedGenres: Set<String> = emptySet(),
    val totalItems: Int = 0,
    val hasMorePages: Boolean = true,
    val isRefreshing: Boolean = false,
    val browseTab: LibraryBrowseTab = LibraryBrowseTab.ITEMS,
    val folderTitle: String? = null,
    val canGoBackInFolder: Boolean = false,
    val isAdministrator: Boolean = false,
    val actionMessage: String? = null,
    val recommendationSections: List<LibraryRecommendationSection> = emptyList()
)

enum class ContentType {
    ALL, MOVIES, SERIES, EPISODES, MOVIES_GENRE, TVSHOWS_GENRE, SEERR_STUDIO, SEERR_NETWORK, AWARD
}

fun ContentType.isSeerrCatalog(): Boolean =
    this == ContentType.SEERR_STUDIO || this == ContentType.SEERR_NETWORK

fun ContentType.isLibraryCatalog(): Boolean =
    this == ContentType.ALL ||
        this == ContentType.MOVIES ||
        this == ContentType.SERIES ||
        this == ContentType.EPISODES

fun ContentType.isGenreCatalog(): Boolean =
    this == ContentType.MOVIES_GENRE ||
        this == ContentType.TVSHOWS_GENRE
