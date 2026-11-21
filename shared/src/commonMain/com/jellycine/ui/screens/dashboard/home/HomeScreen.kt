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
import com.jellycine.data.model.ContentType
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import com.jellycine.resources.Res
import com.jellycine.resources.dashboard_failed_next_up
import com.jellycine.resources.dashboard_next_up
import com.jellycine.data.model.SeerrCatalog
import com.jellycine.preferences.Preferences
import com.jellycine.ui.components.common.UserProfileAvatar
import kotlinx.coroutines.flow.flowOf

@Composable
fun HomeScreen(
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
    preferences: Preferences? = null,
    listState: LazyListState = rememberLazyListState(),
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (ContentType, String?, String) -> Unit = { _, _, _ -> }
) {
    val openViewAll: (String, String?, String) -> Unit = { type, parentId, title ->
        val contentType = ContentType.entries.firstOrNull { it.name == type.uppercase() } ?: ContentType.ALL
        onNavigateToViewAll(contentType, parentId, title)
    }
    val disablePosterEnhancers = com.jellycine.util.image.rememberDisablePosterEnhancers(preferences, authRepository)
    val viewModel: HomeViewModel = viewModel { HomeViewModel(mediaRepository, authRepository) }
    val state by viewModel.uiState.collectAsState()
    var showAccountOverview by remember { mutableStateOf(false) }
    val seerrStudiosEnabled by (preferences?.SeerrStudiosEnabled() ?: flowOf(false))
        .collectAsState(initial = preferences?.isSeerrStudiosEnabled() ?: false)
    val seerrNetworksEnabled by (preferences?.SeerrNetworksEnabled() ?: flowOf(false))
        .collectAsState(initial = preferences?.isSeerrNetworksEnabled() ?: false)
    val featureCarouselEnabled by (preferences?.FeatureCarouselEnabled() ?: flowOf(true))
        .collectAsState(initial = preferences?.isFeatureCarouselEnabled() ?: true)
    val featureCarouselHeight by (preferences?.FeatureCarouselHeight() ?: flowOf(Preferences.FEATURE_CAROUSEL_HEIGHT_LARGE))
        .collectAsState(initial = preferences?.getFeatureCarouselHeight() ?: Preferences.FEATURE_CAROUSEL_HEIGHT_LARGE)
    val autoplayTrailersEnabled by (preferences?.autoplayTrailersEnabled() ?: flowOf(false))
        .collectAsState(initial = preferences?.isAutoplayTrailersEnabled() ?: false)
    LaunchedEffect(featureCarouselEnabled) {
        if (!featureCarouselEnabled && state.selectedCategory != HomeCategory.HOME) {
            viewModel.selectCategory(HomeCategory.HOME)
        }
    }
    var localStudioNames by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(seerrStudiosEnabled, seerrNetworksEnabled) {
        if ((seerrStudiosEnabled || seerrNetworksEnabled) && localStudioNames == null) {
            localStudioNames = mediaRepository.getAvailableStudioNames()
        }
    }
    val seerrStudios = remember(seerrStudiosEnabled, localStudioNames) {
        if (seerrStudiosEnabled) availableInLibrary(SeerrCatalog.popularStudios(limit = 12), localStudioNames) else emptyList()
    }
    val seerrNetworks = remember(seerrNetworksEnabled, localStudioNames) {
        if (seerrNetworksEnabled) availableInLibrary(SeerrCatalog.popularNetworks(), localStudioNames) else emptyList()
    }
    val isHomeCategory = state.selectedCategory == HomeCategory.HOME
    val parallaxOffsetPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
        }
    }

    val profileAvatar: @Composable () -> Unit = {
        UserProfileAvatar(
            imageUrl = state.profileImageUrl,
            serverTypeRaw = state.serverType,
            imageHeaders = state.imageHeaders,
            onClick = { showAccountOverview = true },
            modifier = Modifier.size(34.dp)
        )
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
                if (featureCarouselEnabled) {
                    FeatureTab(
                        mediaRepository = mediaRepository,
                        imageHeaders = state.imageHeaders,
                        featuredItems = state.featured,
                        isLoading = state.isLoading,
                        selectedCategory = state.selectedCategory,
                        carouselHeight = featureCarouselHeight,
                        autoplayTrailersEnabled = autoplayTrailersEnabled,
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
                                profileAvatar()
                            }
                        }
                    )
                } else {
                    BrandHeader(
                        serverName = state.serverName,
                        modifier = Modifier.statusBarsPadding(),
                        actions = { profileAvatar() }
                    )
                }
            }

            if (isHomeCategory && state.libraries.isNotEmpty()) {
                item(key = "libraries") {
                    HomeLibrariesRow(
                        libraries = state.libraries,
                        mediaRepository = mediaRepository,
                        imageHeaders = state.imageHeaders,
                        disablePosterEnhancers = disablePosterEnhancers,
                        onLibraryClick = { library ->
                            openViewAll(
                                viewAllContentType(library.collectionType),
                                library.id,
                                library.name ?: "Library"
                            )
                        }
                    )
                }
            }

            if (isHomeCategory && seerrStudios.isNotEmpty()) {
                item(key = "seerr_studios") {
                    SeerrCatalogSection(
                        title = "Studios",
                        items = seerrStudios,
                        onItemClick = { studio -> openViewAll("SEERR_STUDIO", studio.id, studio.name) }
                    )
                }
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

            if (isHomeCategory && seerrNetworks.isNotEmpty()) {
                item(key = "seerr_networks") {
                    SeerrCatalogSection(
                        title = "Networks",
                        items = seerrNetworks,
                        onItemClick = { network -> openViewAll("SEERR_NETWORK", network.id, network.name) }
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
                        disablePosterEnhancers = disablePosterEnhancers,
                        onItemClick = onItemClick,
                        onNavigateToViewAll = openViewAll
                    )
                }
            }

            if (state.selectedCategory == HomeCategory.TV_SHOWS) {
                item(key = "tv_genres") {
                    TVShowGenreSections(
                        mediaRepository = mediaRepository,
                        imageHeaders = state.imageHeaders,
                        disablePosterEnhancers = disablePosterEnhancers,
                        onItemClick = onItemClick,
                        onNavigateToViewAll = openViewAll
                    )
                }
            }

            items(if (isHomeCategory) state.sections else emptyList(), key = { it.library.id.orEmpty() }) { section ->
                BurstLibrarySection(
                    section = section.toUiSection(),
                    mediaRepository = mediaRepository,
                    disablePosterEnhancers = disablePosterEnhancers,
                    imageHeaders = state.imageHeaders,
                    onItemClick = onItemClick,
                    onNavigateToViewAll = openViewAll
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
