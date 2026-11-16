package com.jellycine.ui.screens.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import com.jellycine.resources.Res
import com.jellycine.resources.dashboard_failed_next_up
import com.jellycine.resources.dashboard_next_up
import com.jellycine.ui.components.common.UserProfileAvatar

@Composable
fun HomeScreen(
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onItemClick: (BaseItemDto) -> Unit = {}
) {
    val viewModel: HomeViewModel = viewModel { HomeViewModel(mediaRepository, authRepository) }
    val state by viewModel.uiState.collectAsState()
    var showAccountOverview by remember { mutableStateOf(false) }
    val isHomeCategory = state.selectedCategory == HomeCategory.HOME
    val parallaxOffsetPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "header") {
                FeatureTab(
                    mediaRepository = mediaRepository,
                    imageHeaders = state.imageHeaders,
                    featuredItems = state.featured,
                    isLoading = state.isLoading,
                    selectedCategory = state.selectedCategory,
                    verticalParallaxOffsetPx = parallaxOffsetPx,
                    onItemClick = onItemClick,
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryChipMenu(
                                selectedCategory = state.selectedCategory,
                                onCategorySelected = viewModel::selectCategory
                            )
                            UserProfileAvatar(
                                imageUrl = state.profileImageUrl,
                                serverTypeRaw = state.serverType,
                                imageHeaders = state.imageHeaders,
                                onClick = { showAccountOverview = true },
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                )
            }

            if (isHomeCategory && state.continueWatching.isNotEmpty()) {
                item(key = "continue_watching") {
                    ContinueWatchingSection(
                        items = state.continueWatching,
                        isLoading = false,
                        error = null,
                        mediaRepository = mediaRepository,
                        imageHeaders = state.imageHeaders,
                        onItemClick = onItemClick
                    )
                }
            }

            if (isHomeCategory && state.nextUp.isNotEmpty()) {
                item(key = "next_up") {
                    ContinueWatchingSection(
                        titleRes = Res.string.dashboard_next_up,
                        errorMessageRes = Res.string.dashboard_failed_next_up,
                        items = state.nextUp,
                        isLoading = false,
                        error = null,
                        mediaRepository = mediaRepository,
                        imageHeaders = state.imageHeaders,
                        onItemClick = onItemClick
                    )
                }
            }

            if (state.selectedCategory == HomeCategory.MOVIES) {
                item(key = "movies_genres") {
                    MovieGenreSections(
                        mediaRepository = mediaRepository,
                        imageHeaders = state.imageHeaders,
                        disablePosterEnhancers = false,
                        onItemClick = onItemClick
                    )
                }
            }

            if (state.selectedCategory == HomeCategory.TV_SHOWS) {
                item(key = "tv_genres") {
                    TVShowGenreSections(
                        mediaRepository = mediaRepository,
                        imageHeaders = state.imageHeaders,
                        disablePosterEnhancers = false,
                        onItemClick = onItemClick
                    )
                }
            }

            items(if (isHomeCategory) state.sections else emptyList(), key = { it.library.id.orEmpty() }) { section ->
                BurstLibrarySection(
                    section = section.toUiSection(),
                    mediaRepository = mediaRepository,
                    disablePosterEnhancers = false,
                    imageHeaders = state.imageHeaders,
                    onItemClick = onItemClick
                )
            }
        }

        if (showAccountOverview) {
            AccountOverview(
                userName = state.username,
                serverName = state.serverName,
                profileImageUrl = state.profileImageUrl,
                imageHeaders = state.imageHeaders,
                serverTypeRaw = state.serverType,
                canChangeUser = false,
                onDismiss = { showAccountOverview = false },
                onChangeUser = {},
                onLogout = {
                    showAccountOverview = false
                    viewModel.logout()
                }
            )
        }
    }
}
