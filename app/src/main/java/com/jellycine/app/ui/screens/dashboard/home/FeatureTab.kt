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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.jellycine.app.R
import com.jellycine.app.util.image.ImageSkeleton
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object CachedData {
    var featuredItems: List<BaseItemDto> = emptyList()
    var continueWatchingItems: List<BaseItemDto> = emptyList()
    var username: String? = null
    var userImageUrl: String? = null
    var lastLoadTime: Long = 0
    var continueWatchingLastLoadTime: Long = 0
    private var _isCurrentlyLoading: Boolean = false

    val isCurrentlyLoading: Boolean get() = _isCurrentlyLoading

    fun shouldRefresh(): Boolean {
        return featuredItems.isEmpty() || System.currentTimeMillis() - lastLoadTime > 300_000
    }

    fun shouldRefreshContinueWatching(): Boolean {
        return continueWatchingItems.isEmpty() ||
            System.currentTimeMillis() - continueWatchingLastLoadTime > 300_000
    }

    fun updateFeaturedItems(items: List<BaseItemDto>) {
        featuredItems = items
        lastLoadTime = System.currentTimeMillis()
        _isCurrentlyLoading = false
    }

    fun updateContinueWatchingItems(items: List<BaseItemDto>) {
        continueWatchingItems = items
        continueWatchingLastLoadTime = System.currentTimeMillis()
    }

    fun updateUserData(name: String?, imageUrl: String?) {
        username = name
        userImageUrl = imageUrl
    }

    fun clearCache() {
        featuredItems = emptyList()
        continueWatchingItems = emptyList()
        lastLoadTime = 0
        continueWatchingLastLoadTime = 0
        _isCurrentlyLoading = false
    }

    fun clearAllCache() {
        featuredItems = emptyList()
        continueWatchingItems = emptyList()
        username = null
        userImageUrl = null
        lastLoadTime = 0
        continueWatchingLastLoadTime = 0
        _isCurrentlyLoading = false
    }

    fun markAsLoading(loading: Boolean) {
        _isCurrentlyLoading = loading
    }
}

private data class FeatureCardImages(
    val lowBackdropUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?
)

@Composable
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class)
fun FeatureTab(
    modifier: Modifier = Modifier,
    featuredItems: List<BaseItemDto> = emptyList(),
    isLoading: Boolean = true,
    error: String? = null,
    selectedCategory: String = "Home",
    onItemClick: (BaseItemDto) -> Unit = {},
    onLogout: () -> Unit = {},
    onCategorySelected: (String) -> Unit = {},
    refreshTrigger: Int = 0
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }

    val currentUsername by authRepository.getUsername().collectAsState(initial = CachedData.username)
    var displayUsername by rememberSaveable(currentUsername) {
        mutableStateOf(currentUsername ?: CachedData.username ?: "User")
    }
    var userProfileImageUrl by rememberSaveable(currentUsername) {
        mutableStateOf(
            if (currentUsername == CachedData.username) {
                CachedData.userImageUrl
            } else {
                null
            }
        )
    }

    val featuredRowState = rememberLazyListState()
    val featuredFlingBehavior = rememberSnapFlingBehavior(lazyListState = featuredRowState)
    val imageCacheByItemId = remember { mutableStateMapOf<String, FeatureCardImages>() }
    val featuredKeys = remember(featuredItems) {
        featuredItems.mapIndexed { index, item -> item.id ?: "${item.name ?: "item"}_$index" }
    }
    val infiniteStartIndex = remember(featuredKeys) {
        if (featuredKeys.isEmpty()) 0 else (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % featuredKeys.size)
    }
    var autoScrollReady by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp.dp * 0.76f).coerceIn(520.dp, 820.dp)

    LaunchedEffect(currentUsername, refreshTrigger) {
        val resolvedUsername = currentUsername ?: CachedData.username
        displayUsername = resolvedUsername?.takeIf { it.isNotBlank() } ?: "User"

        if (CachedData.username == displayUsername && !CachedData.userImageUrl.isNullOrBlank()) {
            userProfileImageUrl = CachedData.userImageUrl
            return@LaunchedEffect
        }

        val profileUrl = withContext(Dispatchers.IO) {
            runCatching { mediaRepository.getUserProfileImageUrl() }.getOrNull()
        }
        userProfileImageUrl = profileUrl
        CachedData.updateUserData(displayUsername, profileUrl)
    }

    LaunchedEffect(featuredItems, isLoading) {
        CachedData.markAsLoading(isLoading)
        if (featuredItems.isNotEmpty()) {
            CachedData.updateFeaturedItems(featuredItems)
        }
    }

    LaunchedEffect(featuredKeys, isLoading) {
        if (isLoading || featuredItems.size <= 1) return@LaunchedEffect
        runCatching { featuredRowState.scrollToItem(infiniteStartIndex) }
    }

    LaunchedEffect(featuredKeys) {
        val validIds = featuredItems.mapNotNull { it.id }.toSet()
        imageCacheByItemId.keys
            .filter { it !in validIds }
            .forEach { imageCacheByItemId.remove(it) }

        if (featuredItems.isEmpty()) return@LaunchedEffect

        val imageLoader = context.imageLoader
        coroutineScope {
            featuredItems.forEach { item ->
                val itemId = item.id ?: return@forEach
                if (imageCacheByItemId[itemId] != null) return@forEach

                launch(Dispatchers.IO) {
                    val lowBackdropUrl = mediaRepository.getImageUrlString(
                        itemId = itemId,
                        imageType = "Backdrop",
                        width = 640,
                        height = 360,
                        quality = 70
                    )
                    val backdropUrl = mediaRepository.getImageUrlString(
                        itemId = itemId,
                        imageType = "Backdrop",
                        width = 1600,
                        height = 900,
                        quality = 90
                    )
                    val logoUrl = mediaRepository.getImageUrlString(
                        itemId = itemId,
                        imageType = "Logo",
                        width = 720,
                        height = 320,
                        quality = 90
                    )

                    withContext(Dispatchers.Main) {
                        imageCacheByItemId[itemId] = FeatureCardImages(
                            lowBackdropUrl = lowBackdropUrl,
                            backdropUrl = backdropUrl,
                            logoUrl = logoUrl
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

    LaunchedEffect(featuredKeys, isLoading, imageCacheByItemId.size) {
        if (autoScrollReady || isLoading) return@LaunchedEffect
        if (imageCacheByItemId.isNotEmpty()) {
            autoScrollReady = true
        }
    }

    LaunchedEffect(featuredKeys, isLoading, autoScrollReady) {
        if (isLoading || featuredItems.size <= 1 || !autoScrollReady) return@LaunchedEffect
        while (true) {
            delay(6500L)
            val nextIndex = featuredRowState.firstVisibleItemIndex + 1
            runCatching {
                featuredRowState.animateScrollToItem(index = nextIndex)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
        ) {
            when {
                featuredItems.isNotEmpty() -> {
                    LazyRow(
                        state = featuredRowState,
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        flingBehavior = featuredFlingBehavior
                    ) {
                        items(
                            count = Int.MAX_VALUE,
                            key = { index ->
                                val item = featuredItems[index % featuredItems.size]
                                "${item.id ?: item.name ?: "feature_item"}_$index"
                            }
                        ) { index ->
                            val item = featuredItems[index % featuredItems.size]
                            val cachedImages = item.id?.let { imageCacheByItemId[it] }
                            FeatureHeroCard(
                                item = item,
                                images = cachedImages,
                                onClick = { onItemClick(item) },
                                heroHeight = heroHeight,
                                modifier = Modifier.fillParentMaxWidth()
                            )
                        }
                    }
                }

                isLoading -> FeatureHeroSkeleton(heroHeight = heroHeight)

                !error.isNullOrBlank() -> FeatureHeroError(error = error, heroHeight = heroHeight)

                else -> FeatureHeroError(error = "No featured content available", heroHeight = heroHeight)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryChipMenu(
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )

                UserProfileAvatar(
                    imageUrl = userProfileImageUrl,
                    userName = displayUsername,
                    onClick = {},
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun CategoryChipMenu(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("Home", "Movies", "TV Shows")
    val menuOptions = remember(selectedCategory) { categories.filterNot { it == selectedCategory } }
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "category_arrow"
    )
    val pillShape = RoundedCornerShape(18.dp)
    val glassGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.12f),
            Color.White.copy(alpha = 0.05f)
        )
    )
    val glassBorder = Color.White.copy(alpha = 0.22f)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(glassGradient)
                .border(1.dp, glassBorder, pillShape)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.jellycine_logo),
                contentDescription = "JellyCine",
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = selectedCategory,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer(rotationZ = arrowRotation)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.Transparent,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            border = null,
            modifier = Modifier.background(Color.Transparent)
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                menuOptions.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(pillShape)
                            .background(glassGradient)
                            .border(1.dp, glassBorder, pillShape)
                            .clickable {
                                expanded = false
                                onCategorySelected(category)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = category,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer(rotationZ = -90f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureHeroCard(
    item: BaseItemDto,
    images: FeatureCardImages?,
    onClick: () -> Unit,
    heroHeight: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var contentVisible by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) { contentVisible = true }
    val logoAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        label = "hero_logo_alpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0.96f,
        label = "hero_logo_scale"
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

    Card(
        onClick = onClick,
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
                val lowState = lowPainter.state
                LaunchedEffect(lowState) {
                    if (lowState is AsyncImagePainter.State.Success ||
                        lowState is AsyncImagePainter.State.Error
                    ) {
                        lowResImage = true
                    }
                }

                Image(
                    painter = lowPainter,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = 1.04f,
                            scaleY = 1.04f
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
                val highResImage = highPainter.state is AsyncImagePainter.State.Success
                val highAlpha by animateFloatAsState(
                    targetValue = if (highResImage) 1f else 0f,
                    label = "hero_backdrop_high_alpha"
                )

                Image(
                    painter = highPainter,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            alpha = highAlpha,
                            scaleX = 1.04f,
                            scaleY = 1.04f
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.58f),
                                0.45f to Color.Transparent,
                                0.70f to Color.Black.copy(alpha = 0.78f),
                                1.0f to Color.Black.copy(alpha = 0.98f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                            .allowRgb565(true)
                            .build(),
                        contentDescription = "${item.name} logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(96.dp)
                            .fillMaxWidth(0.82f)
                            .graphicsLayer(
                                alpha = logoAlpha,
                                scaleX = logoScale,
                                scaleY = logoScale
                            )
                    )
                } else if (images != null || item.id == null) {
                    Text(
                        text = item.name ?: "Unknown title",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 31.sp,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(30.dp)
                            .background(Color.Black, RoundedCornerShape(4.dp))
                    )
                }

                val ratingText = item.communityRating?.let { String.format("%.1f", it) }
                val resolvedYear = item.productionYear ?: item.premiereDate
                    ?.take(4)
                    ?.toIntOrNull()
                val genres = item.genres.orEmpty().take(3)

                val certificateText = item.officialRating?.takeIf { it.isNotBlank() }
                if (!ratingText.isNullOrBlank() || resolvedYear != null || genres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer(
                            alpha = metaAlpha,
                            translationY = metaOffset
                        )
                    ) {
                        if (!ratingText.isNullOrBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFE84B3C),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = ratingText,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (resolvedYear != null) {
                            Text(
                                text = resolvedYear.toString(),
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (genres.isNotEmpty()) {
                            Text(
                                text = genres.joinToString(separator = "/"),
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (!certificateText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .graphicsLayer(
                                alpha = metaAlpha,
                                translationY = metaOffset
                            )
                    ) {
                        Text(
                            text = certificateText,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileAvatar(
    imageUrl: String?,
    userName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .build(),
                contentDescription = "Profile picture of $userName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initials = userName
                .orEmpty()
                .split(" ")
                .mapNotNull { token -> token.firstOrNull()?.uppercase() }
                .take(2)
                .joinToString(separator = "")
                .ifBlank { "U" }

            Text(
                text = initials,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
