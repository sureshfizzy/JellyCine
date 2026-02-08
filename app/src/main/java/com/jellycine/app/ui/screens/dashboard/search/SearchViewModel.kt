package com.jellycine.app.ui.screens.dashboard.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var searchJob: Job? = null

    private val searchCache = mutableMapOf<String, SearchCacheEntry>()
    private val cacheExpirationTime = 300_000L // 5 minutes

    init {
        loadPopularMovies()
        loadTrendingMovies()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query

        searchJob?.cancel()
        
        if (query.isNotEmpty()) {
            searchJob = viewModelScope.launch {
                delay(300)
                searchMovies(query)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                isSearching = false,
                isSearchExecuted = false
            )
        }
    }

    fun executeSearch() {
        val query = _searchQuery.value
        if (query.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                searchAndCategorize(query)
            }
        }
    }

    private suspend fun searchMovies(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true)

        try {
            val searchResult = mediaRepository.searchItems(
                searchTerm = query,
                includeItemTypes = "Movie,Series,Episode",
                limit = 50
            )

            searchResult.fold(
                onSuccess = { items ->
                    val sortedItems = items.sortedWith(compareBy<BaseItemDto> { item ->
                        when (item.type) {
                            "Movie" -> 0
                            "Series" -> 1
                            "Episode" -> 2
                            else -> 3
                        }
                    }.thenBy { it.name })

                    _uiState.value = _uiState.value.copy(
                        searchResults = sortedItems,
                        isSearching = false,
                        error = null
                    )
                },
                onFailure = {
                    performClientSideSearch(query)
                }
            )
        } catch (e: Exception) {
            performClientSideSearch(query)
        }
    }

    private suspend fun searchAndCategorize(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true, isSearchExecuted = false)

        val cacheKey = query.lowercase().trim()
        val cachedEntry = searchCache[cacheKey]
        val currentTime = System.currentTimeMillis()

        if (cachedEntry != null && (currentTime - cachedEntry.timestamp) < cacheExpirationTime) {
            _uiState.value = _uiState.value.copy(
                movieResults = cachedEntry.movieResults,
                showResults = cachedEntry.showResults,
                episodeResults = cachedEntry.episodeResults,
                isSearching = false,
                isSearchExecuted = true,
                error = null
            )
            return
        }

        try {
            val searchResult = mediaRepository.searchItems(
                searchTerm = query,
                includeItemTypes = "Movie,Series,Episode",
                limit = 60
            )

            val allItems = searchResult.getOrNull() ?: emptyList()

            val movies = allItems.filter { it.type == "Movie" }.take(20)
            val shows = allItems.filter { it.type == "Series" }.take(20)
            val episodes = allItems.filter { it.type == "Episode" }.take(20)

            searchCache[cacheKey] = SearchCacheEntry(
                movieResults = movies,
                showResults = shows,
                episodeResults = episodes,
                timestamp = currentTime
            )

            _uiState.value = _uiState.value.copy(
                movieResults = movies,
                showResults = shows,
                episodeResults = episodes,
                isSearching = false,
                isSearchExecuted = true,
                error = null
            )

        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                isSearchExecuted = true,
                error = e.message
            )
        }
    }
    
    private suspend fun performClientSideSearch(query: String) {
        try {
            val movieResult = mediaRepository.getUserItems(
                includeItemTypes = "Movie",
                recursive = true,
                limit = 100,
                sortBy = "SortName",
                sortOrder = "Ascending"
            )

            val seriesResult = mediaRepository.getUserItems(
                includeItemTypes = "Series",
                recursive = true,
                limit = 100,
                sortBy = "SortName",
                sortOrder = "Ascending"
            )

            val allItems = mutableListOf<BaseItemDto>()

            movieResult.getOrNull()?.items?.let { movies ->
                allItems.addAll(movies)
            }

            seriesResult.getOrNull()?.items?.let { series ->
                allItems.addAll(series)
            }

            if (allItems.isEmpty()) {
                val latestResult = mediaRepository.getLatestItems(
                    includeItemTypes = "Movie,Series",
                    limit = 50
                )
                latestResult.getOrNull()?.let { latest ->
                    allItems.addAll(latest)
                }
            }

            val filteredResults = allItems.filter { item ->
                val nameMatch = item.name?.contains(query, ignoreCase = true) == true
                val titleMatch = item.originalTitle?.contains(query, ignoreCase = true) == true
                nameMatch || titleMatch
            }.sortedWith(compareBy<BaseItemDto> { item ->
                when (item.type) {
                    "Movie" -> 0
                    "Series" -> 1
                    "Episode" -> 2
                    else -> 3
                }
            }.thenBy { it.name }).take(20)

            _uiState.value = _uiState.value.copy(
                searchResults = filteredResults,
                isSearching = false,
                error = null
            )
            
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                error = e.message
            )
        }
    }

    private fun loadPopularMovies() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val popularItems = coroutineScope {
                    val recentMoviesDeferred = async {
                        mediaRepository.getRecentlyAddedMovies(limit = 14).getOrNull().orEmpty()
                    }
                    val recentSeriesDeferred = async {
                        mediaRepository.getUserItems(
                            includeItemTypes = "Series",
                            recursive = true,
                            limit = 10,
                            sortBy = "DateCreated",
                            sortOrder = "Descending",
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
                        ).getOrNull()?.items.orEmpty()
                    }

                    val primaryBurst = mergeAndNormalize(
                        recentMoviesDeferred.await(),
                        recentSeriesDeferred.await(),
                        limit = 24
                    )

                    if (primaryBurst.size >= 20) {
                        return@coroutineScope primaryBurst.take(20)
                    }

                    val latestDeferred = async {
                        mediaRepository.getLatestItems(
                            includeItemTypes = "Movie,Series",
                            limit = 28,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
                        ).getOrNull().orEmpty()
                    }
                    val fallbackDeferred = async {
                        mediaRepository.getUserItems(
                            includeItemTypes = "Movie,Series",
                            recursive = true,
                            limit = 28,
                            sortBy = "DateCreated",
                            sortOrder = "Descending",
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
                        ).getOrNull()?.items.orEmpty()
                    }

                    mergeAndNormalize(
                        primaryBurst,
                        latestDeferred.await(),
                        fallbackDeferred.await(),
                        limit = 20
                    )
                }

                _uiState.value = _uiState.value.copy(
                    popularMovies = popularItems,
                    isLoading = false,
                    error = null
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun loadTrendingMovies() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTrendingLoading = true)

            try {
                val trendingMovies = coroutineScope {
                    val moviesDeferred = async {
                        mediaRepository.getUserItems(
                            includeItemTypes = "Movie",
                            recursive = true,
                            limit = 10,
                            sortBy = "PlayCount,CommunityRating,DateCreated",
                            sortOrder = "Descending",
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
                        ).getOrNull()?.items.orEmpty()
                    }
                    val seriesDeferred = async {
                        mediaRepository.getUserItems(
                            includeItemTypes = "Series",
                            recursive = true,
                            limit = 10,
                            sortBy = "PlayCount,CommunityRating,DateCreated",
                            sortOrder = "Descending",
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
                        ).getOrNull()?.items.orEmpty()
                    }

                    val primaryBurst = mergeAndNormalize(
                        interleaveLists(moviesDeferred.await(), seriesDeferred.await()),
                        limit = 18
                    )

                    if (primaryBurst.size >= 12) {
                        return@coroutineScope primaryBurst.take(15)
                    }

                    val recentMoviesDeferred = async {
                        mediaRepository.getRecentlyAddedMovies(limit = 12).getOrNull().orEmpty()
                    }
                    val recentSeriesDeferred = async {
                        mediaRepository.getUserItems(
                            includeItemTypes = "Series",
                            recursive = true,
                            limit = 12,
                            sortBy = "DateCreated",
                            sortOrder = "Descending",
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
                        ).getOrNull()?.items.orEmpty()
                    }
                    val latestDeferred = async {
                        mediaRepository.getLatestItems(
                            includeItemTypes = "Movie,Series",
                            limit = 20,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
                        ).getOrNull().orEmpty()
                    }

                    mergeAndNormalize(
                        primaryBurst,
                        interleaveLists(recentMoviesDeferred.await(), recentSeriesDeferred.await()),
                        latestDeferred.await(),
                        limit = 15
                    )
                }

                _uiState.value = _uiState.value.copy(
                    trendingMovies = trendingMovies,
                    isTrendingLoading = false,
                    error = null
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTrendingLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun clearSearchCache() {
        searchCache.clear()
    }

    private fun interleaveLists(
        first: List<BaseItemDto>,
        second: List<BaseItemDto>
    ): List<BaseItemDto> {
        if (first.isEmpty()) return second
        if (second.isEmpty()) return first

        val merged = ArrayList<BaseItemDto>(first.size + second.size)
        val maxSize = maxOf(first.size, second.size)
        for (index in 0 until maxSize) {
            if (index < first.size) merged.add(first[index])
            if (index < second.size) merged.add(second[index])
        }
        return merged
    }

    private fun mergeAndNormalize(
        vararg groups: List<BaseItemDto>,
        limit: Int
    ): List<BaseItemDto> {
        return groups
            .asSequence()
            .flatten()
            .filter { item -> item.id != null && !item.name.isNullOrBlank() }
            .distinctBy { it.id }
            .take(limit)
            .toList()
    }
}

data class SearchUiState(
    val searchResults: List<BaseItemDto> = emptyList(),
    val popularMovies: List<BaseItemDto> = emptyList(),
    val trendingMovies: List<BaseItemDto> = emptyList(),
    val movieResults: List<BaseItemDto> = emptyList(),
    val showResults: List<BaseItemDto> = emptyList(),
    val episodeResults: List<BaseItemDto> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val isTrendingLoading: Boolean = false,
    val isSearchExecuted: Boolean = false,
    val error: String? = null
)

data class SearchCacheEntry(
    val movieResults: List<BaseItemDto>,
    val showResults: List<BaseItemDto>,
    val episodeResults: List<BaseItemDto>,
    val timestamp: Long
)
