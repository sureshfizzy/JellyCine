package com.jellycine.app.ui.screens.dashboard.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.app.ui.components.common.filterSeerTitles
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrDiscoveryCategory
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.SeerrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository
) : ViewModel() {
    private val authRepository = AuthRepositoryProvider.getInstance(context)
    private val seerrRepository = SeerrRepository(context)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDiscoveryTab = MutableStateFlow(SearchDiscoveryTab.SUGGESTIONS)
    val selectedDiscoveryTab: StateFlow<SearchDiscoveryTab> = _selectedDiscoveryTab.asStateFlow()

    private var searchJob: Job? = null
    private var seerrScopeId: String? = null

    init {
        loadsuggestions()
        refreshSeerrConnectionState()
    }

    fun selectDiscoveryTab(tab: SearchDiscoveryTab) {
        if (_selectedDiscoveryTab.value == tab &&
            (tab.seerrCategory == null || _uiState.value.seerrDiscoveryItems.isNotEmpty())
        ) {
            return
        }
        _selectedDiscoveryTab.value = tab
        tab.seerrCategory?.let(::loadSeerrDiscovery)
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
                seerrMovieResults = emptyList(),
                seerrShowResults = emptyList(),
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

        val activeServerId = authRepository.getActiveSessionSnapshot().activeServerId
        val isSeerrConnected = seerrRepository.getSavedConnectionInfo(activeServerId)?.isVerified == true
        val seerrTitles = fetchSeerrTitles(
            query = query,
            activeServerId = activeServerId,
            isSeerrConnected = isSeerrConnected
        )

        val localItems = loadServerSearchItems(query)
        if (localItems == null) {
            if (seerrTitles.isNotEmpty()) {
                applySearchResults(
                    localItems = emptyList(),
                    seerrTitles = seerrTitles,
                    query = query
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = "Search failed"
                )
            }
            return
        }

        applySearchResults(
            localItems = localItems,
            seerrTitles = seerrTitles,
            query = query
        )
    }

    private suspend fun loadServerSearchItems(query: String): List<BaseItemDto>? {
        return try {
            mediaRepository.searchItems(
                searchTerm = query,
                includeItemTypes = "Movie,Series,Episode",
                limit = 60
            ).getOrNull()?.let { items ->
                filterSearchItems(items, query)
            }
        } catch (_: Exception) {
            null
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

    fun refreshSeerrConnectionState(activeServerId: String? = authRepository.getActiveSessionSnapshot().activeServerId) {
        val scopeId = activeServerId?.takeIf { it.isNotBlank() }
        val scopeChanged = seerrScopeId != scopeId
        val wasSeerrConnected = _uiState.value.isSeerrConnected
        seerrScopeId = scopeId
        val isSeerrConnected = scopeId != null &&
            seerrRepository.getSavedConnectionInfo(scopeId)?.isVerified == true

        if (!isSeerrConnected) {
            _selectedDiscoveryTab.value = SearchDiscoveryTab.SUGGESTIONS
            _uiState.value = _uiState.value.copy(
                isSeerrConnected = false,
                seerrMovieResults = emptyList(),
                seerrShowResults = emptyList(),
                seerrDiscoveryItems = emptyList(),
                seerrDiscoveryLoading = false
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isSeerrConnected = true,
            seerrDiscoveryItems = if (scopeChanged) emptyList() else _uiState.value.seerrDiscoveryItems
        )

        if ((_searchQuery.value.isNotBlank()) && (!wasSeerrConnected || scopeChanged)) {
            executeSearch()
            return
        }

        val category = _selectedDiscoveryTab.value.seerrCategory ?: return
        if ((scopeChanged || _uiState.value.seerrDiscoveryItems.isEmpty()) && !_uiState.value.seerrDiscoveryLoading) {
            loadSeerrDiscovery(category)
        }
    }

    private fun loadSeerrDiscovery(category: SeerrDiscoveryCategory) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                seerrDiscoveryLoading = true
            )

            val activeServerId = authRepository.getActiveSessionSnapshot().activeServerId
            val scopeId = activeServerId?.takeIf { it.isNotBlank() }
            val isSeerrConnected = seerrRepository.getSavedConnectionInfo(activeServerId)?.isVerified == true
            if (scopeId == null || !isSeerrConnected) {
                _uiState.value = _uiState.value.copy(
                    isSeerrConnected = false,
                    seerrDiscoveryItems = emptyList(),
                    seerrDiscoveryLoading = false,
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSeerrConnected = true)

            seerrRepository.getDiscoveryTitles(
                scopeId = scopeId,
                category = category,
                limit = 20
            ).fold(
                onSuccess = { titles ->
                    if (_selectedDiscoveryTab.value.seerrCategory == category) {
                        _uiState.value = _uiState.value.copy(
                            seerrDiscoveryItems = titles.toDiscoveryItems(),
                            seerrDiscoveryLoading = false
                        )
                    }
                },
                onFailure = {
                    if (_selectedDiscoveryTab.value.seerrCategory == category) {
                        _uiState.value = _uiState.value.copy(
                            seerrDiscoveryItems = emptyList(),
                            seerrDiscoveryLoading = false
                        )
                    }
                }
            )
        }
    }

    private fun List<SeerrRecommendationTitle>.toDiscoveryItems(): List<BaseItemDto> =
        distinctBy { title -> "${title.mediaType}:${title.tmdbId}" }
            .map { title ->
                BaseItemDto(
                    id = SeerrItemIds.detailId(tmdbId = title.tmdbId, mediaType = title.mediaType),
                    name = title.title,
                    type = if (title.mediaType.equals("tv", ignoreCase = true)) "Series" else "Movie",
                    providerIds = buildMap {
                        put("tmdb", title.tmdbId)
                        title.jellyfinMediaId?.takeIf { it.isNotBlank() }?.let { put("jellyfin", it) }
                    },
                    productionYear = title.productionYear,
                    imageUrl = title.posterUrl
                )
            }

    private fun filterSearchItems(items: List<BaseItemDto>, query: String): List<BaseItemDto> {
        return items
            .mapNotNull { item ->
                bestSearchMatch(query, item.name, item.originalTitle)?.let { match -> item to match }
            }
            .sortedBy { (_, match) -> match.priority }
            .map { (item, _) -> item }
    }

    private fun applySearchResults(
        localItems: List<BaseItemDto>,
        seerrTitles: List<SeerrRecommendationTitle>,
        query: String
    ) {
        val movieResults = localItems.filter { it.type == "Movie" }.take(20)
        val showResults = localItems.filter { it.type == "Series" }.take(20)
        val episodeResults = localItems.filter { it.type == "Episode" }.take(20)

        _uiState.value = _uiState.value.copy(
            movieResults = movieResults,
            showResults = showResults,
            episodeResults = episodeResults,
            seerrMovieResults = buildSeerrResults(
                seerrTitles = seerrTitles,
                mediaType = "movie",
                query = query,
                localItems = movieResults
            ),
            seerrShowResults = buildSeerrResults(
                seerrTitles = seerrTitles,
                mediaType = "tv",
                query = query,
                localItems = showResults
            ),
            isSearching = false,
            error = null
        )
    }

    private fun buildSeerrResults(
        seerrTitles: List<SeerrRecommendationTitle>,
        mediaType: String,
        query: String,
        localItems: List<BaseItemDto>
    ): List<SeerrRecommendationTitle> {
        if (seerrTitles.isEmpty()) return emptyList()

        val matchedTitles = seerrTitles
            .filter { title -> title.mediaType.equals(mediaType, ignoreCase = true) }
            .mapNotNull { title ->
                bestSearchMatch(query, title.title)?.let { match -> title to match }
            }
            .sortedBy { (_, match) -> match.priority }
            .map { (title, _) -> title }
            .distinctBy { title -> title.tmdbId }

        return filterSeerTitles(
            seerrTitles = matchedTitles,
            localItems = localItems
        ).take(12)
    }

    private fun bestSearchMatch(query: String, vararg texts: String?): SearchMatch? {
        return texts
            .filterNotNull()
            .mapNotNull { text -> textSearchMatch(text, query) }
            .minByOrNull { match -> match.priority }
    }

    private fun textSearchMatch(
        text: String,
        query: String
    ): SearchMatch? {
        val trimmedQuery = query.trim()
        if (text.isBlank() || trimmedQuery.isBlank()) return null

        val lowerQuery = trimmedQuery.lowercase(Locale.US)
        val normalizedQuery = trimmedQuery.normalizedSearchKey()
        val lowerText = text.lowercase(Locale.US)
        val normalizedText = text.normalizedSearchKey()
        val queryWords = lowerQuery
            .split(Regex("[^a-z0-9]+"))
            .filter { word -> word.isNotBlank() }
        val tokens = lowerText.split(Regex("[^a-z0-9]+")).filter { token -> token.isNotBlank() }
        val allWordsMatch = queryWords.all { word ->
            tokens.any { token ->
                token == word || token.startsWith(word) || token.contains(word)
            }
        }

        return when {
            normalizedText == normalizedQuery -> SearchMatch.EXACT
            lowerText == lowerQuery -> SearchMatch.EXACT_TEXT
            lowerText.contains(lowerQuery) -> SearchMatch.PHRASE
            normalizedText.contains(normalizedQuery) -> SearchMatch.NORMALIZED_PHRASE
            allWordsMatch -> SearchMatch.ALL_WORDS
            queryWords.size == 1 && tokens.any { token ->
                token.startsWith(queryWords.first()) || token.contains(queryWords.first())
            } -> SearchMatch.SINGLE_WORD
            else -> null
        }
    }

    private fun String.normalizedSearchKey(): String {
        return lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
    }

    private suspend fun fetchSeerrTitles(
        query: String,
        activeServerId: String?,
        isSeerrConnected: Boolean
    ): List<SeerrRecommendationTitle> {
        val scopeId = activeServerId?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (!isSeerrConnected) return emptyList()

        return seerrRepository.searchTitles(
            scopeId = scopeId,
            query = query,
            limit = 20
        ).getOrElse { emptyList() }
    }

    private enum class SearchMatch(val priority: Int) {
        EXACT(0),
        EXACT_TEXT(1),
        PHRASE(2),
        NORMALIZED_PHRASE(3),
        ALL_WORDS(4),
        SINGLE_WORD(5)
    }
}

enum class SearchDiscoveryTab(val seerrCategory: SeerrDiscoveryCategory?) {
    SUGGESTIONS(null),
    TRENDING(SeerrDiscoveryCategory.TRENDING),
    POPULAR_MOVIES(SeerrDiscoveryCategory.POPULAR_MOVIES),
    POPULAR_SHOWS(SeerrDiscoveryCategory.POPULAR_SHOWS),
    UPCOMING_MOVIES(SeerrDiscoveryCategory.UPCOMING_MOVIES),
    UPCOMING_SHOWS(SeerrDiscoveryCategory.UPCOMING_SHOWS)
}

data class SearchUiState(
    val suggestions: List<BaseItemDto> = emptyList(),
    val movieResults: List<BaseItemDto> = emptyList(),
    val showResults: List<BaseItemDto> = emptyList(),
    val episodeResults: List<BaseItemDto> = emptyList(),
    val seerrMovieResults: List<SeerrRecommendationTitle> = emptyList(),
    val seerrShowResults: List<SeerrRecommendationTitle> = emptyList(),
    val seerrDiscoveryItems: List<BaseItemDto> = emptyList(),
    val isSeerrConnected: Boolean = false,
    val isSearching: Boolean = false,
    val SuggestionsLoading: Boolean = false,
    val seerrDiscoveryLoading: Boolean = false,
    val error: String? = null
)