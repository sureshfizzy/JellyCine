package com.jellycine.app.ui.screens.dashboard.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        loadPopularMovies()
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

    private fun searchMovies(query: String) {
        viewModelScope.launch {
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
                    onFailure = { exception ->
                        performClientSideSearch(query)
                    }
                )
                
            } catch (e: Exception) {
                performClientSideSearch(query)
            }
        }
    }

    private fun searchAndCategorize(query: String) {
        viewModelScope.launch {
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
                return@launch
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
                var result = mediaRepository.getRecentlyAddedMovies(limit = 20)

                if (result.getOrNull()?.isEmpty() == true) {
                    result = mediaRepository.getLatestItems(
                        includeItemTypes = "Movie",
                        limit = 20
                    )
                }

                if (result.getOrNull()?.isEmpty() == true) {
                    val allMoviesResult = mediaRepository.getUserItems(
                        includeItemTypes = "Movie",
                        recursive = true,
                        limit = 20,
                        sortBy = "DateCreated",
                        sortOrder = "Descending"
                    )
                    result = allMoviesResult.map { it.items ?: emptyList() }
                }
                
                result.fold(
                    onSuccess = { movies ->
                        _uiState.value = _uiState.value.copy(
                            popularMovies = movies,
                            isLoading = false,
                            error = null
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message
                        )
                    }
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

    fun clearSearchCache() {
        searchCache.clear()
    }
}

data class SearchUiState(
    val searchResults: List<BaseItemDto> = emptyList(),
    val popularMovies: List<BaseItemDto> = emptyList(),
    val movieResults: List<BaseItemDto> = emptyList(),
    val showResults: List<BaseItemDto> = emptyList(),
    val episodeResults: List<BaseItemDto> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val isSearchExecuted: Boolean = false,
    val error: String? = null
)

data class SearchCacheEntry(
    val movieResults: List<BaseItemDto>,
    val showResults: List<BaseItemDto>,
    val episodeResults: List<BaseItemDto>,
    val timestamp: Long
)