package com.vela.app.ui.screens.dashboard.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.runtime.mutableStateListOf
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
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.*
import com.vela.shared.R
import com.vela.app.ui.screens.auth.ProfileImageLoader
import com.vela.shared.util.image.imageTagFor
import com.vela.data.model.BaseItemDto
import com.vela.data.model.PersistedHomeSnapshot
import com.vela.data.repository.AuthRepository.ActiveSessionSnapshot
import com.vela.data.repository.AuthRepositoryProvider
import com.vela.data.repository.MediaRepositoryProvider
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
    val preloadedItemIds = remember { mutableStateListOf<String>() }
    var stableFeaturedItems by remember(selectedCategory) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    val metadataQualifiedFeaturedItems = remember(featuredItems) {
        derivedStateOf {
            featuredItems.filter(::hasFeatureHeroAssets)
        }
    }
    val currentAssetsReady by remember(metadataQualifiedFeaturedItems.value) {
        derivedStateOf {
            metadataQualifiedFeaturedItems.value.isNotEmpty() &&
                metadataQualifiedFeaturedItems.value.all { candidate ->
                    candidate.id.orEmpty() in preloadedItemIds
                }
        }
    }
    val resolvedFeaturedItems = remember(
        metadataQualifiedFeaturedItems.value,
        stableFeaturedItems,
        currentAssetsReady
    ) {
        derivedStateOf {
            val targetItems = metadataQualifiedFeaturedItems.value
            if (targetItems.isEmpty()) return@derivedStateOf stableFeaturedItems
            if (currentAssetsReady) return@derivedStateOf targetItems
            if (stableFeaturedItems.isEmpty()) return@derivedStateOf targetItems
            stableFeaturedItems
        }
    }

    LaunchedEffect(currentAssetsReady, metadataQualifiedFeaturedItems.value) {
        if (currentAssetsReady && metadataQualifiedFeaturedItems.value.isNotEmpty()) {
            stableFeaturedItems = metadataQualifiedFeaturedItems.value
        }
    }

    val featuredKeys = remember(resolvedFeaturedItems.value) {
        resolvedFeaturedItems.value.indices.map { it.toString() }
    }
    val isFeatureAssets = remember(
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
    val heroHeight = (configuration.screenHeightDp.dp * 0.62f).coerceIn(380.dp, 620.dp)

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

                    var backdropLoaded = lowBackdropUrl.isNullOrBlank()
                    var logoLoaded = logoUrl.isNullOrBlank()

                    if (!lowBackdropUrl.isNullOrBlank()) {
                        val result = imageLoader.execute(
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
                        backdropLoaded = result is SuccessResult
                    }

                    if (!backdropUrl.isNullOrBlank()) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data(backdropUrl)
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
                        val result = imageLoader.execute(
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
                        logoLoaded = result is SuccessResult
                    }

                    if (backdropLoaded && logoLoaded) {
                        withContext(Dispatchers.Main) {
                            preloadedItemIds.add(itemId)
                        }
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
            try {
                resolvedHeroActionFocusRequester.requestFocus()
            } catch (_: Exception) {}
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

                isLoading || isFeatureAssets -> FeatureHeroSkeleton(heroHeight = heroHeight)

                !error.isNullOrBlank() -> FeatureHeroError(error = error, heroHeight = heroHeight)

                else -> FeatureHeroError(error = "No featured content available", heroHeight = heroHeight)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 48.dp, end = 16.dp, top = 12.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                        .size(30.dp)
                )
            }
        }

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
    val lastIndex = HomeCategory.all.lastIndex

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeCategory.all.forEachIndexed { index, category ->
            val isSelected = selectedCategory == category
            var isFocused by remember(category) { mutableStateOf(false) }
            val textColor by animateColorAsState(
                targetValue = when {
                    isFocused -> Color.White
                    isSelected -> Color.White
                    else -> Color.White.copy(alpha = 0.60f)
                },
                label = "category_tab_text"
            )
            Column(
                modifier = Modifier
                    .then(
                        if (index == 0 && initialChipFocusRequester != null) {
                            Modifier.focusRequester(initialChipFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent
                    )
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
                        isFocused = state.isFocused
                        if (state.isFocused) {
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(HomeCategory.titleRes(category)),
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(if (isSelected) 18.dp else 0.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.80f) else Color.Transparent
                        )
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
    var contentVisible by remember { mutableStateOf(true) }
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
    val favoriteFocusRequester = remember(item.id) { FocusRequester() }
    val moreInfoFocusRequester = remember(item.id) { FocusRequester() }

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
                        .crossfade(false)
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
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            translationY = backdropParallaxShift,
                            scaleX = 1.06f,
                            scaleY = 1.06f
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
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            translationY = backdropParallaxShift,
                            alpha = highAlpha,
                            scaleX = 1.06f,
                            scaleY = 1.06f
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.60f),
                                0.10f to Color.Black.copy(alpha = 0.30f),
                                0.22f to Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.80f),
                                0.18f to Color.Black.copy(alpha = 0.60f),
                                0.35f to Color.Black.copy(alpha = 0.30f),
                                0.50f to Color.Transparent
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
                                0.0f to Color.Transparent,
                                0.55f to Color.Black.copy(alpha = 0.20f),
                                0.75f to Color.Black.copy(alpha = 0.55f),
                                0.90f to Color.Black.copy(alpha = 0.85f),
                                1.0f to Color.Black
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.42f)
                    .widthIn(max = 460.dp)
                    .padding(start = 42.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        alignment = Alignment.CenterStart,
                        modifier = Modifier
                            .height(56.dp)
                            .fillMaxWidth(0.80f)
                            .graphicsLayer(alpha = logoAlpha)
                    )
                } else {
                    Text(
                        text = itemName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 24.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

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
                    val resolvedYear = item.productionYear ?: item.premiereDate
                        ?.take(4)
                        ?.toIntOrNull()

                    resolvedYear?.let { year ->
                        Text(
                            text = year.toString(),
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (item.type == "Series") {
                        val seasonCount = item.childCount
                        if (seasonCount != null && seasonCount > 0) {
                            Text(
                                text = if (seasonCount == 1) "1 Season" else "$seasonCount Seasons",
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
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
                                        color = Color.White.copy(alpha = 0.90f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                    }

                    val firstGenre = item.genres.orEmpty().firstOrNull()
                    if (!firstGenre.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.20f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = firstGenre,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    val ratingValue = item.communityRating
                    if (ratingValue != null && ratingValue > 0f) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val fullStars = (ratingValue / 2f).toInt().coerceIn(0, 5)
                            repeat(fullStars) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB800),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format("%.1f", ratingValue),
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                item.overview
                    ?.takeIf { it.isNotBlank() }
                    ?.let { overview ->
                        Text(
                            text = overview,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.90f)
                        )
                    }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeatureHeroPlayButton(
                        text = stringResource(R.string.play),
                        onFocusChanged = { isFocused ->
                            if (isFocused) onHeroZoneFocused?.invoke()
                        },
                        modifier = Modifier
                            .focusRequester(playFocusRequester)
                            .focusProperties {
                                up = headerFocusRequester ?: FocusRequester.Default
                                right = favoriteFocusRequester
                                down = belowContentFocusRequester ?: FocusRequester.Default
                            },
                        onClick = onClick
                    )

                    FeatureHeroCircleButton(
                        icon = Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        onFocusChanged = { isFocused ->
                            if (isFocused) onHeroZoneFocused?.invoke()
                        },
                        modifier = Modifier
                            .focusRequester(favoriteFocusRequester)
                            .focusProperties {
                                up = headerFocusRequester ?: FocusRequester.Default
                                left = playFocusRequester
                                right = moreInfoFocusRequester
                                down = belowContentFocusRequester ?: FocusRequester.Default
                            },
                        onClick = {}
                    )

                    FeatureHeroCircleButton(
                        icon = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.dashboard_more_info),
                        onFocusChanged = { isFocused ->
                            if (isFocused) onHeroZoneFocused?.invoke()
                        },
                        onRightPressed = onAdvanceToNextFeature,
                        modifier = Modifier
                            .focusRequester(moreInfoFocusRequester)
                            .focusProperties {
                                up = headerFocusRequester ?: FocusRequester.Default
                                left = favoriteFocusRequester
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
private fun FeatureHeroPlayButton(
    text: String,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        label = "play_btn_scale"
    )

    Row(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(999.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.95f))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(999.dp)
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                onFocusChanged?.invoke(state.isFocused)
            }
            .clickable(onClick = onClick)
            .focusable()
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeatureHeroCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onRightPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1f,
        label = "circle_btn_scale"
    )

    Box(
        modifier = modifier
            .size(34.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .background(
                if (isFocused) Color.White.copy(alpha = 0.28f)
                else Color.White.copy(alpha = 0.12f)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.30f),
                shape = CircleShape
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
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
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