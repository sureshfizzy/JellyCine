package com.jellycine.shared.ui.screens.dashboard.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.HomeLibrarySectionData
import com.jellycine.data.repository.MediaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val featured: List<BaseItemDto> = emptyList(),
    val continueWatching: List<BaseItemDto> = emptyList(),
    val nextUp: List<BaseItemDto> = emptyList(),
    val sections: List<HomeLibrarySectionData> = emptyList(),
    val imageHeaders: Map<String, String> = emptyMap()
)

class HomeViewModel(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            coroutineScope {
                val headers = async { mediaRepository.getImageAuthHeaders() }
                val featured = async { mediaRepository.getLatestItems(limit = 8) }
                val resume = async { mediaRepository.getResumeItems(limit = 12) }
                val nextUp = async { mediaRepository.getNextUpItems(limit = 12) }
                val sections = async { mediaRepository.getHomeLibrarySections() }

                _uiState.value = HomeUiState(
                    isLoading = false,
                    featured = featured.await().getOrNull().orEmpty(),
                    continueWatching = resume.await().getOrNull().orEmpty(),
                    nextUp = nextUp.await().getOrNull().orEmpty(),
                    sections = sections.await().getOrNull().orEmpty().filter { it.items.isNotEmpty() },
                    imageHeaders = headers.await()
                )
            }
        }
    }

    suspend fun imageUrl(
        itemId: String,
        imageType: String,
        width: Int,
        imageTag: String? = null
    ): String? {
        return mediaRepository.getImageUrlString(
            itemId = itemId,
            imageType = imageType,
            width = width,
            imageTag = imageTag
        )
    }
}
