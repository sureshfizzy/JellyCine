package com.jellycine.app.ui.screens.dashboard.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class ViewAllViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private lateinit var mediaRepository: MediaRepository

    private val _uiState = MutableStateFlow(ViewAllUiState())
    val uiState: StateFlow<ViewAllUiState> = _uiState.asStateFlow()

    private val _items = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val items: StateFlow<List<BaseItemDto>> = _items.asStateFlow()

    private var currentPage = 0
    private val pageSize = 50
    private var totalItems = 0
    private var hasMorePages = true

    fun loadItems(
        contentType: ContentType,
        parentId: String? = null,
        refresh: Boolean = false
    ) {
        if (refresh) {
            currentPage = 0
            _items.value = emptyList()
            hasMorePages = true
        }

        if (!hasMorePages && !refresh) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                if (!::mediaRepository.isInitialized) {
                    mediaRepository = MediaRepositoryProvider.getInstance(context)
                }

                withContext(Dispatchers.IO) {
                    val result = when (contentType) {
                        ContentType.MOVIES -> mediaRepository.getUserItems(
                            parentId = parentId,
                            includeItemTypes = "Movie",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,CriticRating,ProductionYear,Overview"
                        )
                        ContentType.SERIES -> mediaRepository.getUserItems(
                            parentId = parentId,
                            includeItemTypes = "Series",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,CriticRating,ProductionYear,Overview"
                        )
                        ContentType.EPISODES -> mediaRepository.getUserItems(
                            parentId = parentId,
                            includeItemTypes = "Episode",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "SeriesName,SeriesId,SeasonName,SeasonId,Overview"
                        )
                        ContentType.ALL -> mediaRepository.getUserItems(
                            parentId = parentId,
                            includeItemTypes = "Movie,Series",
                            sortBy = _uiState.value.sortBy,
                            sortOrder = _uiState.value.sortOrder,
                            limit = pageSize,
                            startIndex = currentPage * pageSize,
                            recursive = true,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,CriticRating,ProductionYear,Overview"
                        )
                    }

                    result.fold(
                        onSuccess = { queryResult ->
                            val newItems = queryResult.items ?: emptyList()
                            totalItems = queryResult.totalRecordCount ?: 0
                            hasMorePages = (currentPage + 1) * pageSize < totalItems

                            withContext(Dispatchers.Main) {
                                if (refresh) {
                                    _items.value = newItems
                                } else {
                                    _items.value = _items.value + newItems
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

    fun loadMoreItems(contentType: ContentType, parentId: String? = null) {
        loadItems(contentType, parentId, refresh = false)
    }

    fun setSortBy(sortBy: String, contentType: ContentType, parentId: String? = null) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy)
        loadItems(contentType, parentId, refresh = true)
    }

    fun setSortOrder(sortOrder: String, contentType: ContentType, parentId: String? = null) {
        _uiState.value = _uiState.value.copy(sortOrder = sortOrder)
        loadItems(contentType, parentId, refresh = true)
    }

    fun toggleGenreFilter(genre: String, contentType: ContentType, parentId: String? = null) {
        val currentGenres = _uiState.value.selectedGenres.toMutableSet()
        if (currentGenres.contains(genre)) {
            currentGenres.remove(genre)
        } else {
            currentGenres.add(genre)
        }
        _uiState.value = _uiState.value.copy(selectedGenres = currentGenres)
        loadItems(contentType, parentId, refresh = true)
    }

    fun toggleYearFilter(year: String, contentType: ContentType, parentId: String? = null) {
        val currentYears = _uiState.value.selectedYears.toMutableSet()
        if (currentYears.contains(year)) {
            currentYears.remove(year)
        } else {
            currentYears.add(year)
        }
        _uiState.value = _uiState.value.copy(selectedYears = currentYears)
        loadItems(contentType, parentId, refresh = true)
    }

    fun clearFilters(contentType: ContentType, parentId: String? = null) {
        _uiState.value = _uiState.value.copy(
            selectedGenres = emptySet(),
            selectedYears = emptySet()
        )
        loadItems(contentType, parentId, refresh = true)
    }
}

data class ViewAllUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortBy: String = "DateCreated",
    val sortOrder: String = "Descending",
    val selectedGenres: Set<String> = emptySet(),
    val selectedYears: Set<String> = emptySet(),
    val totalItems: Int = 0,
    val hasMorePages: Boolean = true
)

enum class ContentType {
    ALL, MOVIES, SERIES, EPISODES
}