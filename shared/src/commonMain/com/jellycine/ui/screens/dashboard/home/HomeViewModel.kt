package com.jellycine.ui.screens.dashboard.home

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
    val isLoading: Boolean = true,
    val selectedCategory: String = HomeCategory.HOME,
    val featured: List<BaseItemDto> = emptyList(),
    val continueWatching: List<BaseItemDto> = emptyList(),
    val nextUp: List<BaseItemDto> = emptyList(),
    val libraries: List<BaseItemDto> = emptyList(),
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
            val headers = mediaRepository.getImageAuthHeaders()
            val snapshot = mediaRepository.loadPersistedHomeSnapshot()
            _uiState.update {
                it.copy(
                    imageHeaders = headers,
                    isLoading = snapshot == null,
                    featured = snapshot?.featuredHomeItems.orEmpty(),
                    continueWatching = snapshot?.continueWatchingItems.orEmpty(),
                    nextUp = snapshot?.nextUpItems.orEmpty(),
                    libraries = snapshot?.myMediaLibraries.orEmpty(),
                    sections = snapshot?.homeLibrarySections.orEmpty().filter { section -> section.items.isNotEmpty() },
                    profileImageUrl = snapshot?.profileImageUrl
                )
            }

            coroutineScope {
                launch {
                    loadFeatured(HomeCategory.HOME)?.let { featured ->
                        _uiState.update {
                            if (it.selectedCategory == HomeCategory.HOME) it.copy(isLoading = false, featured = featured) else it
                        }
                    }
                }
                launch {
                    mediaRepository.getResumeItems(limit = 12).getOrNull()?.let { items ->
                        _uiState.update { it.copy(continueWatching = items) }
                    }
                }
                launch {
                    mediaRepository.getNextUpItems(limit = 12).getOrNull()?.let { items ->
                        _uiState.update { it.copy(nextUp = items) }
                    }
                }
                launch {
                    mediaRepository.getHomeLibrarySections().getOrNull()?.let { sections ->
                        _uiState.update { it.copy(sections = sections.filter { section -> section.items.isNotEmpty() }) }
                    }
                }
                launch {
                    mediaRepository.getHomeLibraries().getOrNull()?.let { libraries ->
                        _uiState.update { it.copy(libraries = libraries) }
                    }
                }
                launch {
                    mediaRepository.getUserProfileImageUrl()?.let { url ->
                        _uiState.update { it.copy(profileImageUrl = url) }
                    }
                }
            }

            _uiState.update { it.copy(isLoading = false) }
            persistSnapshot()
        }
    }

    private suspend fun persistSnapshot() {
        val state = _uiState.value
        if (state.selectedCategory != HomeCategory.HOME) return
        mediaRepository.persistHomeSnapshot(
            featuredHomeItems = state.featured,
            continueWatchingItems = state.continueWatching,
            nextUpItems = state.nextUp,
            homeLibrarySections = state.sections,
            myMediaLibraries = state.libraries,
            username = state.username,
            serverName = state.serverName,
            profileImageUrl = state.profileImageUrl
        )
    }

    fun selectCategory(category: String) {
        if (category == _uiState.value.selectedCategory) return
        _uiState.update { it.copy(selectedCategory = category, isLoading = true, featured = emptyList()) }
        viewModelScope.launch {
            val featured = loadFeatured(category).orEmpty()
            _uiState.update {
                if (it.selectedCategory == category) it.copy(isLoading = false, featured = featured) else it
            }
        }
    }

    private suspend fun loadFeatured(category: String): List<BaseItemDto>? {
        val result = mediaRepository.getUserItems(
            parentId = null,
            includeItemTypes = when (category) {
                HomeCategory.MOVIES -> "Movie"
                HomeCategory.TV_SHOWS -> "Series"
                else -> "Movie,Series"
            },
            sortBy = "Random",
            sortOrder = null,
            limit = 10,
            startIndex = 0,
            recursive = true,
            fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating,ProductionYear,PremiereDate,OfficialRating,Overview,RemoteTrailers"
        )
        return result.getOrNull()?.items.orEmpty()
            .filter { it.id != null && !it.name.isNullOrBlank() }
            .takeIf { result.isSuccess }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            mediaRepository.clearPersistedHomeSnapshot()
        }
    }
}
