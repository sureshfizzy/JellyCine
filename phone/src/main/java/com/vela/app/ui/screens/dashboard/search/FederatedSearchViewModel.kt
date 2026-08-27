package com.vela.app.ui.screens.dashboard.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vela.data.model.BaseItemDto
import com.vela.data.model.SearchMediaType
import com.vela.data.repository.AuthRepositoryProvider
import com.vela.data.repository.FederatedServerFailure
import com.vela.data.repository.FederatedMediaItem
import com.vela.data.repository.FederatedMediaRepository
import com.vela.data.repository.FederatedServer
import com.vela.shared.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FederatedSearchUiState(
    val query: String = "",
    val servers: List<FederatedServer> = emptyList(),
    val selectedServerId: String? = null,
    val selectedTypes: Set<SearchMediaType> = DEFAULT_FEDERATED_SEARCH_TYPES,
    val items: List<FederatedMediaItem> = emptyList(),
    val failures: List<FederatedServerFailure> = emptyList(),
    val isSearching: Boolean = false,
    val openingServerId: String? = null,
    val actionError: String? = null
)

class FederatedSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val searchRepository = FederatedMediaRepository(application)
    private val authRepository = AuthRepositoryProvider.getInstance(application)
    private val sessionNavigator = FederatedSessionNavigator(application)
    private val _uiState = MutableStateFlow(
        FederatedSearchUiState(servers = searchRepository.availableServers())
    )
    val uiState: StateFlow<FederatedSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.observeActiveSession().collect {
                val servers = searchRepository.availableServers()
                _uiState.update { current ->
                    current.copy(
                        servers = servers,
                        selectedServerId = current.selectedServerId
                            ?.takeIf { selectedId -> servers.any { it.id == selectedId } }
                    )
                }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        scheduleSearch()
    }

    fun submitSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { searchCurrentQuery() }
    }

    fun selectServer(serverId: String?) {
        _uiState.update { it.copy(selectedServerId = serverId) }
    }

    fun toggleType(type: SearchMediaType) {
        val current = _uiState.value.selectedTypes
        val updated = if (type in current) current - type else current + type
        if (updated.isEmpty()) return
        _uiState.update { it.copy(selectedTypes = updated) }
        scheduleSearch(immediate = true)
    }

    fun openResult(result: FederatedMediaItem, onReady: (BaseItemDto) -> Unit) {
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
                            actionError = error.message
                                ?: getApplication<Application>().getString(
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

    private fun scheduleSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        val query = _uiState.value.query
        if (query.isBlank()) {
            _uiState.update {
                it.copy(items = emptyList(), failures = emptyList(), isSearching = false)
            }
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            searchCurrentQuery()
        }
    }

    private suspend fun searchCurrentQuery() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        val selectedTypes = _uiState.value.selectedTypes
        _uiState.update { it.copy(isSearching = true, failures = emptyList()) }

        val response = try {
            searchRepository.search(query, selectedTypes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    actionError = error.message
                        ?: getApplication<Application>().getString(R.string.search_failed)
                )
            }
            return
        }

        // 只允许当前输入和类型对应的请求落地，避免慢服务器覆盖更新后的查询结果。
        if (
            _uiState.value.query.trim() != query ||
            _uiState.value.selectedTypes != selectedTypes
        ) return
        _uiState.update {
            it.copy(
                servers = searchRepository.availableServers(),
                items = response.items,
                failures = response.failures,
                isSearching = false
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}

private val DEFAULT_FEDERATED_SEARCH_TYPES = setOf(
    SearchMediaType.MOVIE,
    SearchMediaType.SERIES,
    SearchMediaType.EPISODE
)
