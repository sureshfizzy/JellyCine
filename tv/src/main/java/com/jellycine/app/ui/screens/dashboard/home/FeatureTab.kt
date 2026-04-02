package com.jellycine.app.ui.screens.dashboard.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.*
import com.jellycine.app.R
import com.jellycine.app.ui.screens.auth.ProfileImageLoader
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.PersistedHomeSnapshot
import com.jellycine.data.repository.AuthRepository.ActiveSessionSnapshot
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object CachedData {
    var featuredItems: List<BaseItemDto> = emptyList()
    var lastLoadTime: Long = 0
    private var _isCurrentlyLoading: Boolean = false

    val isCurrentlyLoading: Boolean get() = _isCurrentlyLoading

    fun shouldRefresh(): Boolean {
        return featuredItems.isEmpty() || System.currentTimeMillis() - lastLoadTime > 300_000
    }

    fun updateFeaturedItems(items: List<BaseItemDto>) {
        featuredItems = items
        lastLoadTime = System.currentTimeMillis()
        _isCurrentlyLoading = false
    }

    fun clearAllCache() {
        featuredItems = emptyList()
        lastLoadTime = 0
        _isCurrentlyLoading = false
    }

    fun markAsLoading(loading: Boolean) {
        _isCurrentlyLoading = loading
    }
}

private data class FeatureCardImages(
    val lowBackdropUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val versionKey: String? = null
)

private fun FeatureCardImages?.isHeroReady(): Boolean {
    val hasBackdrop = !this?.backdropUrl.isNullOrBlank() || !this?.lowBackdropUrl.isNullOrBlank()
    val hasLogo = !this?.logoUrl.isNullOrBlank()
    return hasBackdrop && hasLogo
}

@Composable
fun FeatureTab(
    modifier: Modifier = Modifier,
    featuredItems: List<BaseItemDto> = emptyList(),
    isLoading: Boolean = true,
    error: String? = null,
    selectedCategory: String = HomeCategory.HOME,
    verticalParallaxOffsetPx: Float = 0f,
    onItemClick: (BaseItemDto) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onCategorySelected: (String) -> Unit = {},
    sidebarFocusRequester: FocusRequester? = null,
    initialChipFocusRequester: FocusRequester? = null,
    lastChipFocusRequester: FocusRequester? = null,
    heroActionFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    onHeroZoneFocused: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val profileFocusRequester = remember { FocusRequester() }
    val resolvedHeroActionFocusRequester = heroActionFocusRequester ?: remember { FocusRequester() }
    val userFallback = stringResource(R.string.settings_unknown_user)
    var persistedHomeSnapshot by remember {
        mutableStateOf<PersistedHomeSnapshot?>(mediaRepository.getPersistedHomeSnapshot())
    }
    val sessionSnapshot by authRepository.observeActiveSession().collectAsState(
        initial = ActiveSessionSnapshot(
            serverName = null,
            serverUrl = null,
            serverType = null,
            username = null,
            savedServers = emptyList(),
            activeServerId = null
        )
    )
    val currentUsername = sessionSnapshot.username ?: persistedHomeSnapshot?.username
    val currentServerUrl = sessionSnapshot.serverUrl ?: persistedHomeSnapshot?.serverUrl
    var displayUsername by rememberSaveable(currentUsername, currentServerUrl) {
        mutableStateOf(currentUsername ?: persistedHomeSnapshot?.username ?: userFallback)
    }
    var userProfileImageUrl by rememberSaveable(currentUsername, currentServerUrl) {
        mutableStateOf<String?>(persistedHomeSnapshot?.profileImageUrl)
    }

    val imageCacheByItemId = remember { mutableStateMapOf<String, FeatureCardImages>() }
    var stableFeaturedItems by remember(selectedCategory) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    val metadataQualifiedFeaturedItems = remember(featuredItems) {
        derivedStateOf {
            featuredItems.filter(::hasFeatureHeroAssets)
        }
    }
    val displayFeaturedItems = remember(metadataQualifiedFeaturedItems.value, imageCacheByItemId) {
        derivedStateOf {
            metadataQualifiedFeaturedItems.value.filter { item ->
                val itemId = item.id ?: return@filter false
                val cachedImages = imageCacheByItemId[itemId] ?: return@filter false
                val hasBackdrop = !cachedImages.backdropUrl.isNullOrBlank() ||
                    !cachedImages.lowBackdropUrl.isNullOrBlank()
                val hasLogo = !cachedImages.logoUrl.isNullOrBlank()
                hasBackdrop && hasLogo
            }
        }
    }
    val CurrentAssetsReady = remember(metadataQualifiedFeaturedItems.value, imageCacheByItemId) {
        metadataQualifiedFeaturedItems.value.isNotEmpty() &&
            metadataQualifiedFeaturedItems.value.all { candidate ->
                imageCacheByItemId[candidate.id.orEmpty()].isHeroReady()
            }
    }
    val resolvedFeaturedItems = remember(
        metadataQualifiedFeaturedItems.value,
        displayFeaturedItems.value,
        stableFeaturedItems,
        CurrentAssetsReady
    ) {
        derivedStateOf {
            val targetItems = metadataQualifiedFeaturedItems.value
            if (targetItems.isEmpty()) return@derivedStateOf stableFeaturedItems

            val fallbackItems = if (stableFeaturedItems.isNotEmpty()) stableFeaturedItems else targetItems
            if (CurrentAssetsReady || fallbackItems.isEmpty()) return@derivedStateOf targetItems

            buildList {
                targetItems.forEachIndexed { index, targetItem ->
                    val fallbackAtIndex = fallbackItems.getOrNull(index)
                    val isTargetReady = imageCacheByItemId[targetItem.id.orEmpty()].isHeroReady()

                    when {
                        index < 2 && fallbackAtIndex != null -> add(fallbackAtIndex)
                        isTargetReady -> add(targetItem)
                        fallbackAtIndex != null -> add(fallbackAtIndex)
                        else -> add(targetItem)
                    }
                }
            }.distinctBy { it.id ?: it.name.orEmpty() }
        }
    }

    LaunchedEffect(CurrentAssetsReady, metadataQualifiedFeaturedItems.value) {
        if (CurrentAssetsReady && metadataQualifiedFeaturedItems.value.isNotEmpty()) {
            stableFeaturedItems = metadataQualifiedFeaturedItems.value
        } else if (stableFeaturedItems.isEmpty() && metadataQualifiedFeaturedItems.value.isNotEmpty()) {
            stableFeaturedItems = metadataQualifiedFeaturedItems.value
        }
    }

    val featuredKeys = remember(resolvedFeaturedItems.value) {
        resolvedFeaturedItems.value.mapIndexed { index, item -> item.id ?: item.name ?: index.toString() }
    }
    val isResolvingFeatureAssets = remember(
        isLoading,
        featuredItems,
        metadataQualifiedFeaturedItems.value,
        resolvedFeaturedItems.value
    ) {
        !isLoading &&
            featuredItems.isNotEmpty() &&
            metadataQualifiedFeaturedItems.value.isNotEmpty() &&
            resolvedFeaturedItems.value.isEmpty()
    }
    var autoScroll by rememberSaveable(selectedCategory) { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp.dp * 0.68f).coerceIn(440.dp, 720.dp)

    var currentHeroIndex by rememberSaveable(selectedCategory) { mutableStateOf(0) }

    LaunchedEffect(currentServerUrl, currentUsername) {
        persistedHomeSnapshot = mediaRepository.loadPersistedHomeSnapshot()
    }

    LaunchedEffect(currentUsername, currentServerUrl) {
        val activeUsername = currentUsername ?: persistedHomeSnapshot?.username
        displayUsername = activeUsername?.takeIf { it.isNotBlank() } ?: "User"

        val persistedProfileUrl = persistedHomeSnapshot?.profileImageUrl
        if (!persistedProfileUrl.isNullOrBlank()) {
            userProfileImageUrl = persistedProfileUrl
        }

        val user = withContext(Dispatchers.IO) {
            try {
                mediaRepository.getCurrentUser().getOrNull()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        currentCoroutineContext().ensureActive()
        val profileUrl = withContext(Dispatchers.IO) {
            try {
                mediaRepository.getUserProfileImageUrl(user?.primaryImageTag)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        currentCoroutineContext().ensureActive()
        userProfileImageUrl = profileUrl
        authRepository.updateActiveServerProfileImage(
            profileImageUrl = profileUrl ?: persistedProfileUrl
        )
    }

    LaunchedEffect(featuredItems, isLoading) {
        CachedData.markAsLoading(isLoading)
        if (featuredItems.isNotEmpty()) {
            CachedData.updateFeaturedItems(featuredItems)
        }
    }

    LaunchedEffect(metadataQualifiedFeaturedItems.value) {
        if (metadataQualifiedFeaturedItems.value.isEmpty()) return@LaunchedEffect

        val imageLoader = context.imageLoader
        coroutineScope {
            metadataQualifiedFeaturedItems.value.forEach { item ->
                val itemId = item.id ?: return@forEach
                val versionKey = listOfNotNull(
                    item.imageTagFor(imageType = "Backdrop", targetItemId = itemId),
                    item.imageTagFor(imageType = "Logo", targetItemId = itemId)
                ).distinct().takeIf { it.isNotEmpty() }?.joinToString("|")
                val cachedImages = imageCacheByItemId[itemId]
                if (cachedImages != null && cachedImages.versionKey == versionKey) return@forEach

                launch(Dispatchers.IO) {
                    val backdropTag = item.imageTagFor(
                        imageType = "Backdrop",
                        targetItemId = itemId
                    )
                    val logoTag = item.imageTagFor(
                        imageType = "Logo",
                        targetItemId = itemId
                    )
                    val lowBackdropUrl = mediaRepository.getImageUrlString(
                        itemId = itemId,
                        imageType = "Backdrop",
                        width = 640,
                        height = 360,
                        quality = 70,
                        imageTag = backdropTag
                    )
                    val backdropUrl = mediaRepository.getImageUrlString(
                        itemId = itemId,
                        imageType = "Backdrop",
                        width = 1600,
                        height = 900,
                        quality = 90,
                        imageTag = backdropTag
                    )
                    val logoUrl = mediaRepository.getImageUrlString(
                        itemId = itemId,
                        imageType = "Logo",
                        width = 720,
                        height = 320,
                        quality = 90,
                        imageTag = logoTag
                    )

                    withContext(Dispatchers.Main) {
                        imageCacheByItemId[itemId] = FeatureCardImages(
                            lowBackdropUrl = lowBackdropUrl,
                            backdropUrl = backdropUrl,
                            logoUrl = logoUrl,
                            versionKey = versionKey
                        )
                    }

                    if (!lowBackdropUrl.isNullOrBlank()) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data(lowBackdropUrl)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .crossfade(false)
                                .allowHardware(true)
                                .allowRgb565(true)
                                .build()
                        )
                    }

                    if (!logoUrl.isNullOrBlank()) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data(logoUrl)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .crossfade(false)
                                .allowHardware(true)
                                .allowRgb565(true)
                                .build()
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(featuredKeys) {
        if (featuredKeys.isEmpty()) {
            autoScroll = false
            currentHeroIndex = 0
        } else {
            currentHeroIndex = currentHeroIndex.coerceIn(0, featuredKeys.lastIndex)
        }
    }

    LaunchedEffect(featuredKeys, isLoading, resolvedFeaturedItems.value.size) {
        if (autoScroll || isLoading) return@LaunchedEffect
        if (resolvedFeaturedItems.value.isNotEmpty()) {
            autoScroll = true
        }
    }

    LaunchedEffect(featuredKeys, isLoading, autoScroll) {
        if (isLoading || resolvedFeaturedItems.value.size <= 1 || !autoScroll) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            currentHeroIndex = (currentHeroIndex + 1) % resolvedFeaturedItems.value.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
        ) {
            when {
                resolvedFeaturedItems.value.isNotEmpty() -> {
                    val activeItem = resolvedFeaturedItems.value.getOrNull(currentHeroIndex)
                    if (activeItem != null) {
                        FeatureHeroCard(
                            item = activeItem,
                            verticalParallaxOffsetPx = verticalParallaxOffsetPx,
                            images = activeItem.id?.let { imageCacheByItemId[it] },
                            headerFocusRequester = initialChipFocusRequester,
                            entryActionFocusRequester = resolvedHeroActionFocusRequester,
                            belowContentFocusRequester = contentFocusRequester,
                            onHeroZoneFocused = onHeroZoneFocused,
                            onAdvanceToNextFeature = {
                                if (resolvedFeaturedItems.value.size > 1) {
                                    currentHeroIndex =
                                        (currentHeroIndex + 1) % resolvedFeaturedItems.value.size
                                    resolvedHeroActionFocusRequester.requestFocus()
                                }
                            },
                            onClick = { onItemClick(activeItem) },
                            heroHeight = heroHeight,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                isLoading || isResolvingFeatureAssets -> FeatureHeroSkeleton(heroHeight = heroHeight)

                !error.isNullOrBlank() -> FeatureHeroError(error = error, heroHeight = heroHeight)

                else -> FeatureHeroError(error = "No featured content available", heroHeight = heroHeight)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryBubbleTabs(
                        selectedCategory = selectedCategory,
                        onCategorySelected = onCategorySelected,
                        sidebarFocusRequester = sidebarFocusRequester,
                        initialChipFocusRequester = initialChipFocusRequester,
                        profileFocusRequester = profileFocusRequester,
                        lastChipFocusRequester = lastChipFocusRequester,
                        onHeroZoneFocused = onHeroZoneFocused,
                        contentFocusRequester = resolvedHeroActionFocusRequester
                    )
                }

                UserProfileAvatar(
                    imageUrl = userProfileImageUrl,
                    serverTypeRaw = sessionSnapshot.serverType,
                    onClick = onProfileClick,
                    modifier = Modifier
                        .focusRequester(profileFocusRequester)
                        .focusProperties {
                            left = lastChipFocusRequester ?: initialChipFocusRequester ?: FocusRequester.Default
                            down = resolvedHeroActionFocusRequester
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.key == Key.DirectionDown
                            ) {
                                resolvedHeroActionFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                onHeroZoneFocused?.invoke()
                            }
                        }
                        .align(Alignment.TopEnd)
                        .size(34.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0f),
                            Color.Black.copy(alpha = 0.40f),
                            Color.Black.copy(alpha = 0.72f),
                            Color.Black
                        )
                    )
                )
        )
    }
}

private fun hasFeatureHeroAssets(item: BaseItemDto): Boolean {
    val hasLogo = item.parentLogoImageTag?.isNotBlank() == true ||
        item.imageTags
            ?.any { (type, tag) -> type.equals("Logo", ignoreCase = true) && tag.isNotBlank() } == true

    val hasBackdrop = item.backdropImageTags?.any { it.isNotBlank() } == true ||
        item.parentBackdropImageTags?.any { it.isNotBlank() } == true ||
        item.imageTags
            ?.any { (type, tag) -> type.equals("Backdrop", ignoreCase = true) && tag.isNotBlank() } == true

    return hasLogo && hasBackdrop
}

@Composable
private fun CategoryBubbleTabs(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    sidebarFocusRequester: FocusRequester? = null,
    initialChipFocusRequester: FocusRequester? = null,
    profileFocusRequester: FocusRequester? = null,
    lastChipFocusRequester: FocusRequester? = null,
    onHeroZoneFocused: (() -> Unit)? = null,
    contentFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedCornerShape(18.dp)
    val glassGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.12f),
            Color.White.copy(alpha = 0.05f)
        )
    )
    val glassBorder = Color.White.copy(alpha = 0.22f)
    var focusedCategory by remember(selectedCategory) { mutableStateOf(selectedCategory) }
    val lastIndex = HomeCategory.all.lastIndex

    Row(
        modifier = modifier
            .clip(containerShape)
            .background(glassGradient)
            .border(1.dp, glassBorder, containerShape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HomeCategory.all.forEachIndexed { index, category ->
            val isSelected = selectedCategory == category
            val isFocused = focusedCategory == category
            val itemShape = RoundedCornerShape(14.dp)
            val itemScale by animateFloatAsState(
                targetValue = if (isFocused) 1.04f else 1f,
                label = "category_tab_scale"
            )
            val itemBackground by animateColorAsState(
                targetValue = when {
                    isFocused && isSelected -> Color.White
                    isFocused -> Color.White.copy(alpha = 0.18f)
                    isSelected -> Color.White
                    else -> Color.Transparent
                },
                label = "category_tab_background"
            )
            val itemBorder by animateColorAsState(
                targetValue = when {
                    isFocused -> Color.White.copy(alpha = 0.95f)
                    isSelected -> Color.White.copy(alpha = 0.28f)
                    else -> Color.Transparent
                },
                label = "category_tab_border"
            )
            val textColor by animateColorAsState(
                targetValue = when {
                    isSelected -> Color(0xFF10131A)
                    isFocused -> Color.White
                    else -> Color.White.copy(alpha = 0.82f)
                },
                label = "category_tab_text"
            )
            Row(
                modifier = Modifier
                    .then(
                        if (index == 0 && initialChipFocusRequester != null) {
                            Modifier.focusRequester(initialChipFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        scaleX = itemScale
                        scaleY = itemScale
                    }
                    .clip(itemShape)
                    .background(itemBackground)
                    .border(1.5.dp, itemBorder, itemShape)
                    .focusProperties {
                        if (index == 0 && sidebarFocusRequester != null) {
                            left = sidebarFocusRequester
                        }
                        if (index == lastIndex && profileFocusRequester != null) {
                            right = profileFocusRequester
                        }
                        if (contentFocusRequester != null) {
                            down = contentFocusRequester
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.DirectionDown &&
                            contentFocusRequester != null
                        ) {
                            contentFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            focusedCategory = category
                            onHeroZoneFocused?.invoke()
                        }
                    }
                    .then(
                        if (index == lastIndex && lastChipFocusRequester != null) {
                            Modifier.focusRequester(lastChipFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onCategorySelected(category) }
                    .focusable()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(HomeCategory.titleRes(category)),
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun UserProfileAvatar(
    imageUrl: String?,
    serverTypeRaw: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        ProfileImageLoader(
            imageUrl = imageUrl,
            serverTypeRaw = serverTypeRaw,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun FeatureHeroCard(
    item: BaseItemDto,
    verticalParallaxOffsetPx: Float,
    images: FeatureCardImages?,
    headerFocusRequester: FocusRequester?,
    entryActionFocusRequester: FocusRequester?,
    belowContentFocusRequester: FocusRequester?,
    onHeroZoneFocused: (() -> Unit)?,
    onAdvanceToNextFeature: (() -> Unit)?,
    onClick: () -> Unit,
    heroHeight: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val itemName = item.name ?: stringResource(R.string.search_result_unknown_title)
    var contentVisible by remember(item.id) { mutableStateOf(false) }
    var playButtonFocused by remember(item.id) { mutableStateOf(false) }
    var moreInfoButtonFocused by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) { contentVisible = true }
    val logoAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        label = "hero_logo_alpha"
    )
    val metaAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        label = "hero_meta_alpha"
    )
    val metaOffset by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 10f,
        label = "hero_meta_offset"
    )

    val lowBackdropUrl = images?.lowBackdropUrl ?: images?.backdropUrl
    val backdropUrl = images?.backdropUrl
    val logoUrl = images?.logoUrl
    var lowResImage by remember(item.id, lowBackdropUrl) { mutableStateOf(false) }
    val backdropParallaxShift = remember(verticalParallaxOffsetPx) { verticalParallaxOffsetPx * 0.4f }
    val localPlayFocusRequester = remember(item.id) { FocusRequester() }
    val playFocusRequester = entryActionFocusRequester ?: localPlayFocusRequester
    val moreInfoFocusRequester = remember(item.id) { FocusRequester() }
    val heroActionsActive = playButtonFocused || moreInfoButtonFocused
    val heroDetailsAlpha by animateFloatAsState(
        targetValue = if (heroActionsActive) 1f else 0f,
        label = "hero_details_alpha"
    )
    val heroDetailsShift by animateFloatAsState(
        targetValue = if (heroActionsActive) 0f else 12f,
        label = "hero_details_shift"
    )
    val heroActionsAlpha by animateFloatAsState(
        targetValue = if (heroActionsActive) 1f else 0f,
        label = "hero_actions_alpha"
    )
    val heroActionsScale by animateFloatAsState(
        targetValue = if (heroActionsActive) 1f else 0.96f,
        label = "hero_actions_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!lowBackdropUrl.isNullOrBlank()) {
                val lowPainter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(lowBackdropUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .allowHardware(true)
                        .allowRgb565(true)
                        .build()
                )
                val lowState by lowPainter.state.collectAsState()
                LaunchedEffect(lowState) {
                    if (lowState is AsyncImagePainter.State.Success ||
                        lowState is AsyncImagePainter.State.Error
                    ) {
                        lowResImage = true
                    }
                }

                Image(
                    painter = lowPainter,
                    contentDescription = itemName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            translationY = backdropParallaxShift,
                            scaleX = 1.14f,
                            scaleY = 1.14f
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }

            if (!backdropUrl.isNullOrBlank() && lowResImage) {
                val highPainter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(backdropUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .allowHardware(true)
                        .allowRgb565(true)
                        .build()
                )
                val highState by highPainter.state.collectAsState()
                val highResImage = highState is AsyncImagePainter.State.Success
                val highAlpha by animateFloatAsState(
                    targetValue = if (highResImage) 1f else 0f,
                    label = "hero_backdrop_high_alpha"
                )

                Image(
                    painter = highPainter,
                    contentDescription = itemName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            translationY = backdropParallaxShift,
                            alpha = highAlpha,
                            scaleX = 1.14f,
                            scaleY = 1.14f
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.42f),
                                0.18f to Color.Black.copy(alpha = 0.24f),
                                0.40f to Color.Black.copy(alpha = 0.08f),
                                1.0f to Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.12f),
                                0.50f to Color.Transparent,
                                0.82f to Color.Black.copy(alpha = 0.34f),
                                1.0f to Color.Black.copy(alpha = 0.58f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.46f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val radius = size.maxDimension * 0.72f
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f)
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = radius
                        )
                        onDrawBehind { drawRect(brush) }
                    }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.34f)
                    .widthIn(max = 380.dp)
                    .padding(start = 42.dp, end = 20.dp, top = 20.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (!logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(logoUrl)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .crossfade(false)
                        .allowHardware(true)
                        .allowRgb565(false)
                        .build(),
                        contentDescription = stringResource(
                            R.string.feature_logo_content_description,
                            itemName
                        ),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(88.dp)
                            .fillMaxWidth(0.88f)
                            .graphicsLayer(
                                alpha = logoAlpha
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .height(42.dp)
                            .background(Color.Black, RoundedCornerShape(4.dp))
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val heroBadgeText = when (item.type) {
                        "Movie" -> stringResource(R.string.movies)
                        "Series" -> stringResource(R.string.tv_shows)
                        else -> item.type?.takeIf { it.isNotBlank() } ?: stringResource(R.string.home)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = heroBadgeText,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    item.runTimeTicks
                        ?.takeIf { it > 0L }
                        ?.let { runtimeTicks ->
                            val totalMinutes = (runtimeTicks / 600_000_000L).toInt()
                            if (totalMinutes > 0) {
                                val hours = totalMinutes / 60
                                val minutes = totalMinutes % 60
                                val runtimeText = if (hours > 0) {
                                    "${hours}h ${minutes}m"
                                } else {
                                    "${minutes}m"
                                }
                                Text(
                                    text = runtimeText,
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                }

                val ratingText = item.communityRating?.let { String.format("%.1f", it) }
                val resolvedYear = item.productionYear ?: item.premiereDate
                    ?.take(4)
                    ?.toIntOrNull()
                val genres = item.genres.orEmpty().take(3)

                val certificateText = item.officialRating?.takeIf { it.isNotBlank() }
                val hasMetaRow = !ratingText.isNullOrBlank() || resolvedYear != null || genres.isNotEmpty() || !certificateText.isNullOrBlank()
                if (hasMetaRow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(
                                alpha = metaAlpha,
                                translationY = metaOffset
                            ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!ratingText.isNullOrBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFE84B3C),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = ratingText,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        val trailingMetaText = buildList {
                            resolvedYear?.let { add(it.toString()) }
                            if (genres.isNotEmpty()) {
                                add(genres.take(2).joinToString(separator = "/"))
                            }
                            certificateText?.let { add(it) }
                        }.joinToString(separator = "  ")

                        if (trailingMetaText.isNotBlank()) {
                            Text(
                                text = trailingMetaText,
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item.overview
                    ?.takeIf { it.isNotBlank() }
                    ?.let { overview ->
                        Text(
                            text = overview,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    alpha = heroDetailsAlpha,
                                    translationY = heroDetailsShift
                                )
                        )
                    }

                Row(
                    modifier = Modifier.graphicsLayer(
                        alpha = heroActionsAlpha,
                        scaleX = heroActionsScale,
                        scaleY = heroActionsScale,
                        translationY = heroDetailsShift
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeatureHeroActionButton(
                        text = stringResource(R.string.play),
                        icon = Icons.Rounded.PlayArrow,
                        isPrimary = true,
                        onFocusChanged = { isFocused ->
                            playButtonFocused = isFocused
                            if (isFocused) {
                                onHeroZoneFocused?.invoke()
                            }
                        },
                        modifier = Modifier
                            .focusRequester(playFocusRequester)
                            .focusProperties {
                                up = headerFocusRequester ?: FocusRequester.Default
                                right = moreInfoFocusRequester
                                down = belowContentFocusRequester ?: FocusRequester.Default
                            },
                        onClick = onClick
                    )

                    FeatureHeroActionButton(
                        text = stringResource(R.string.dashboard_more_info),
                        isPrimary = false,
                        onFocusChanged = { isFocused ->
                            moreInfoButtonFocused = isFocused
                            if (isFocused) {
                                onHeroZoneFocused?.invoke()
                            }
                        },
                        onRightPressed = onAdvanceToNextFeature,
                        modifier = Modifier
                            .focusRequester(moreInfoFocusRequester)
                            .focusProperties {
                                up = headerFocusRequester ?: FocusRequester.Default
                                left = playFocusRequester
                                down = belowContentFocusRequester ?: FocusRequester.Default
                            },
                        onClick = onClick
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureHeroActionButton(
    text: String,
    isPrimary: Boolean,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onRightPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    var isFocused by remember(text, isPrimary) { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .widthIn(min = 104.dp)
            .clip(shape)
            .background(
                when {
                    isPrimary -> Color.White
                    isFocused -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.14f)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isPrimary -> Color.Transparent
                    else -> Color.White.copy(alpha = 0.14f)
                },
                shape = shape
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                onFocusChanged?.invoke(state.isFocused)
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionRight &&
                    onRightPressed != null
                ) {
                    onRightPressed()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon?.let { imageVector ->
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (isPrimary) Color.Black else Color.White,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = text,
            color = if (isPrimary) Color.Black else Color.White,
            fontSize = 9.sp,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeatureHeroSkeleton(heroHeight: Dp) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    }
}

@Composable
private fun FeatureHeroError(error: String, heroHeight: Dp) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.76f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}
