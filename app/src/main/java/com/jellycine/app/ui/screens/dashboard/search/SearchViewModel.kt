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

    private val searchCache = mutableMapOf<String, SearchCacheEntry>()
    private val cacheExpirationTime = 300_000L // 5 minutes

    init {
        loadsuggestions()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query

        searchJob?.cancel()
        
        if (query.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                error = null
            )
            searchJob = viewModelScope.launch {
                delay(300)
                searchAndCategorize(query)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                movieResults = emptyList(),
                showResults = emptyList(),
                episodeResults = emptyList(),
                isSearching = false,
                error = null
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

    private suspend fun searchAndCategorize(query: String) {
        _uiState.value = _uiState.value.copy(
            isSearching = true,
            error = null
        )

        val cacheKey = query.lowercase().trim()
        val cachedEntry = searchCache[cacheKey]
        val currentTime = System.currentTimeMillis()

        if (cachedEntry != null && (currentTime - cachedEntry.timestamp) < cacheExpirationTime) {
            _uiState.value = _uiState.value.copy(
                movieResults = cachedEntry.movieResults,
                showResults = cachedEntry.showResults,
                episodeResults = cachedEntry.episodeResults,
                isSearching = false,
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

            val allItems = searchResult.getOrElse {
                performClientSideSearch(query)
                return
            }
            val filteredItems = filterSearchItems(allItems, query)
            val categorizedResults = categorizeResults(filteredItems)

            searchCache[cacheKey] = SearchCacheEntry(
                movieResults = categorizedResults.movieResults,
                showResults = categorizedResults.showResults,
                episodeResults = categorizedResults.episodeResults,
                timestamp = currentTime
            )

            _uiState.value = _uiState.value.copy(
                movieResults = categorizedResults.movieResults,
                showResults = categorizedResults.showResults,
                episodeResults = categorizedResults.episodeResults,
                isSearching = false,
                error = null
            )

        } catch (e: Exception) {
            performClientSideSearch(query)
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
                val name = item.name.orEmpty()
                val originalTitle = item.originalTitle.orEmpty()
                name.contains(query, ignoreCase = true) ||
                    originalTitle.contains(query, ignoreCase = true)
            }.take(20)
            val categorizedResults = categorizeResults(filteredResults)

            _uiState.value = _uiState.value.copy(
                movieResults = categorizedResults.movieResults,
                showResults = categorizedResults.showResults,
                episodeResults = categorizedResults.episodeResults,
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

    private fun loadsuggestions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(SuggestionsLoading = true)

            try {
                mediaRepository.getSuggestions(
                    mediaType = "Movie,Series",
                    limit = 15
                ).fold(
                    onSuccess = { suggestions ->
                        _uiState.value = _uiState.value.copy(
                            suggestions = suggestions,
                            SuggestionsLoading = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            SuggestionsLoading = false,
                            error = error.message
                        )
                    }
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    SuggestionsLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun filterSearchItems(items: List<BaseItemDto>, query: String): List<BaseItemDto> {
        val lowerQuery = query.lowercase()
        val queryWords = lowerQuery.split(" ").filter { it.isNotBlank() }

        if (queryWords.size <= 1) return items

        val filteredItems = items.filter { item ->
            val text = "${item.name.orEmpty()} ${item.originalTitle.orEmpty()}".lowercase()
            text.contains(lowerQuery) || run {
                var index = -1
                queryWords.all { word ->
                    index = text.indexOf(word, index + 1)
                    index >= 0
                }
            }
        }

        return filteredItems.ifEmpty { items }
    }

    private fun categorizeResults(items: List<BaseItemDto>): CategorizedSearchResults {
        return CategorizedSearchResults(
            movieResults = items.filter { it.type == "Movie" }.take(20),
            showResults = items.filter { it.type == "Series" }.take(20),
            episodeResults = items.filter { it.type == "Episode" }.take(20)
        )
    }
}

data class SearchUiState(
    val suggestions: List<BaseItemDto> = emptyList(),
    val movieResults: List<BaseItemDto> = emptyList(),
    val showResults: List<BaseItemDto> = emptyList(),
    val episodeResults: List<BaseItemDto> = emptyList(),
    val isSearching: Boolean = false,
    val SuggestionsLoading: Boolean = false,
    val error: String? = null
)

data class SearchCacheEntry(
    val movieResults: List<BaseItemDto>,
    val showResults: List<BaseItemDto>,
    val episodeResults: List<BaseItemDto>,
    val timestamp: Long
)

private data class CategorizedSearchResults(
    val movieResults: List<BaseItemDto>,
    val showResults: List<BaseItemDto>,
    val episodeResults: List<BaseItemDto>
)
