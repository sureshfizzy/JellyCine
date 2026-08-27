package com.vela.app.ui.screens.dashboard.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vela.data.model.BaseItemDto
import com.vela.data.preferences.FederatedViewPreferences
import com.vela.data.repository.AuthRepositoryProvider
import com.vela.data.repository.FederatedContentSection
import com.vela.data.repository.FederatedMediaItem
import com.vela.data.repository.FederatedMediaRepository
import com.vela.data.repository.FederatedServer
import com.vela.data.repository.FederatedServerFailure
import com.vela.shared.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FederatedHomeTab {
    CONTINUE_WATCHING,
    FAVORITES,
    LIBRARIES
}

sealed interface FederatedContentUiState {
    data object Loading : FederatedContentUiState
    data class Ready(
        val items: List<FederatedMediaItem>,
        val failures: List<FederatedServerFailure>
    ) : FederatedContentUiState
}

data class FederatedHomeUiState(
    val servers: List<FederatedServer> = emptyList(),
    val selectedTab: FederatedHomeTab = FederatedHomeTab.CONTINUE_WATCHING,
    val excludedContinueWatchingServerIds: Set<String> = emptySet(),
    val content: FederatedContentUiState = FederatedContentUiState.Loading,
    val openingServerId: String? = null,
    val actionError: String? = null
)

class FederatedHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FederatedMediaRepository(application)
    private val authRepository = AuthRepositoryProvider.getInstance(application)
    private val preferences = FederatedViewPreferences(application)
    private val sessionNavigator = FederatedSessionNavigator(application)
    private val contentCache = mutableMapOf<FederatedHomeTab, FederatedContentUiState.Ready>()
    private val _uiState = MutableStateFlow(
        FederatedHomeUiState(servers = repository.availableServers())
    )
    val uiState: StateFlow<FederatedHomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.excludedContinueWatchingServerIds
                .distinctUntilChanged()
                .collect { excludedIds ->
                    val changed = excludedIds != _uiState.value.excludedContinueWatchingServerIds
                    _uiState.update { it.copy(excludedContinueWatchingServerIds = excludedIds) }
                    if (changed) {
                        contentCache.remove(FederatedHomeTab.CONTINUE_WATCHING)
                        if (_uiState.value.selectedTab == FederatedHomeTab.CONTINUE_WATCHING) {
                            loadSelected(force = true)
                        }
                    }
                }
        }
        viewModelScope.launch {
            authRepository.observeActiveSession()
                .map { snapshot ->
                    snapshot.savedServers.map { server ->
                        "${server.id}|${server.serverUrl}|${server.activeLineId}|${server.lastUsedAt}"
                    }
                }
                .distinctUntilChanged()
                .collect {
                    contentCache.clear()
                    _uiState.update { current ->
                        current.copy(servers = repository.availableServers())
                    }
                    loadSelected(force = true)
                }
        }
        loadSelected(force = true)
    }

    fun selectTab(tab: FederatedHomeTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { current ->
            current.copy(
                selectedTab = tab,
                content = contentCache[tab] ?: FederatedContentUiState.Loading
            )
        }
        loadSelected(force = false)
    }

    fun refresh() {
        contentCache.remove(_uiState.value.selectedTab)
        loadSelected(force = true)
    }

    fun setContinueWatchingServerIncluded(serverId: String, included: Boolean) {
        viewModelScope.launch {
            preferences.setContinueWatchingServerIncluded(serverId, included)
        }
    }

    fun openItem(result: FederatedMediaItem, onReady: (BaseItemDto) -> Unit) {
        if (_uiState.value.openingServerId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(openingServerId = result.serverId, actionError = null) }
            sessionNavigator.activate(result.serverId).fold(
                onSuccess = {
                    _uiState.update { it.copy(openingServerId = null) }
                    onReady(result.item)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            openingServerId = null,
                            actionError = error.message ?: getApplication<Application>().getString(
                                R.string.federated_search_switch_failed
                            )
                        )
                    }
                }
            )
        }
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    private fun loadSelected(force: Boolean) {
        val tab = _uiState.value.selectedTab
        if (!force) {
            contentCache[tab]?.let { cached ->
                _uiState.update { it.copy(content = cached) }
                return
            }
        }
        loadJob?.cancel()
        _uiState.update { it.copy(content = FederatedContentUiState.Loading) }
        loadJob = viewModelScope.launch {
            val section = when (tab) {
                FederatedHomeTab.CONTINUE_WATCHING -> FederatedContentSection.CONTINUE_WATCHING
                FederatedHomeTab.FAVORITES -> FederatedContentSection.FAVORITES
                FederatedHomeTab.LIBRARIES -> FederatedContentSection.LIBRARIES
            }
            val response = repository.loadContent(
                section = section,
                excludedContinueWatchingServerIds =
                    _uiState.value.excludedContinueWatchingServerIds
            )
            if (_uiState.value.selectedTab != tab) return@launch
            val ready = FederatedContentUiState.Ready(response.items, response.failures)
            contentCache[tab] = ready
            _uiState.update { it.copy(content = ready, servers = repository.availableServers()) }
        }
    }
}
