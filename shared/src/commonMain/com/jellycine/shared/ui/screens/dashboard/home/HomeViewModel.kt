package com.jellycine.shared.ui.screens.dashboard.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.HomeLibrarySectionData
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val featured: List<BaseItemDto> = emptyList(),
    val continueWatching: List<BaseItemDto> = emptyList(),
    val nextUp: List<BaseItemDto> = emptyList(),
    val sections: List<HomeLibrarySectionData> = emptyList(),
    val imageHeaders: Map<String, String> = emptyMap(),
    val serverName: String? = null,
    val serverType: String? = null,
    val username: String? = null,
    val profileImageUrl: String? = null
)

class HomeViewModel(
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            authRepository.observeActiveSession().collectLatest { session ->
                _uiState.update {
                    it.copy(
                        serverName = session.serverName,
                        serverType = session.serverType,
                        username = session.username
                    )
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            coroutineScope {
                val headers = async { mediaRepository.getImageAuthHeaders() }
                val featured = async { mediaRepository.getLatestItems(limit = 8) }
                val resume = async { mediaRepository.getResumeItems(limit = 12) }
                val nextUp = async { mediaRepository.getNextUpItems(limit = 12) }
                val sections = async { mediaRepository.getHomeLibrarySections() }
                val profileImage = async { mediaRepository.getUserProfileImageUrl() }

                val loadedHeaders = headers.await()
                val loadedFeatured = featured.await().getOrNull().orEmpty()
                val loadedResume = resume.await().getOrNull().orEmpty()
                val loadedNextUp = nextUp.await().getOrNull().orEmpty()
                val loadedSections = sections.await().getOrNull().orEmpty().filter { it.items.isNotEmpty() }
                val loadedProfileImage = profileImage.await()

                _uiState.update {
                    it.copy(
                        featured = loadedFeatured,
                        continueWatching = loadedResume,
                        nextUp = loadedNextUp,
                        sections = loadedSections,
                        imageHeaders = loadedHeaders,
                        profileImageUrl = loadedProfileImage
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            mediaRepository.clearPersistedHomeSnapshot()
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
