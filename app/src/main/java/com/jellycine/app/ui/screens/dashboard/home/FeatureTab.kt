package com.jellycine.app.ui.screens.dashboard.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.jellycine.app.util.image.ImageSkeleton
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
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
    val backdropUrl: String?,
    val logoUrl: String?
)

@Composable
@Suppress("UNUSED_PARAMETER")
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
    val imageCacheByItemId = remember { mutableStateMapOf<String, FeatureCardImages>() }
    val featuredKeys = remember(featuredItems) {
        featuredItems.mapIndexed { index, item -> item.id ?: "${item.name ?: "item"}_$index" }
    }

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
        runCatching { featuredRowState.scrollToItem(0) }
        while (true) {
            delay(6500L)
            val currentIndex = featuredRowState.firstVisibleItemIndex
                .coerceIn(0, featuredItems.lastIndex)
            val nextIndex = (currentIndex + 1) % featuredItems.size
            runCatching {
                featuredRowState.animateScrollToItem(index = nextIndex)
            }
        }
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
                    val backdropUrl = mediaRepository.getImageUrlString(
                        itemId = itemId,
                        imageType = "Backdrop",
                        width = 1280,
                        height = 720,
                        quality = 90
                    )
                    val logoUrl = mediaRepository.getImageUrlString(
                        itemId = itemId,
                        imageType = "Logo",
                        width = 520,
                        height = 240,
                        quality = 90
                    )

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

                    withContext(Dispatchers.Main) {
                        imageCacheByItemId[itemId] = FeatureCardImages(
                            backdropUrl = backdropUrl,
                            logoUrl = logoUrl
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 48.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Welcome $displayUsername",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            UserProfileAvatar(
                imageUrl = userProfileImageUrl,
                userName = displayUsername,
                onClick = {},
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(34.dp)
            )
        }

        CategoryPills(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            featuredItems.isNotEmpty() -> {
                LazyRow(
                    state = featuredRowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = featuredItems,
                        key = { item -> item.id ?: item.name ?: "feature_item" }
                    ) { item ->
                        val cachedImages = item.id?.let { imageCacheByItemId[it] }
                        FeatureHeroCard(
                            item = item,
                            images = cachedImages,
                            onClick = { onItemClick(item) },
                            modifier = Modifier.fillParentMaxWidth()
                        )
                    }
                }
            }

            isLoading -> FeatureHeroSkeleton()

            !error.isNullOrBlank() -> FeatureHeroError(error = error)

            else -> FeatureHeroError(error = "No featured content available")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CategoryPills(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("Home", "Movies", "TV Shows")

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(categories) { category ->
            Surface(
                color = if (category == selectedCategory) Color.White else Color.Transparent,
                border = if (category == selectedCategory) null else BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clickable { onCategorySelected(category) }
            ) {
                Text(
                    text = category,
                    color = if (category == selectedCategory) Color.Black else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (category == selectedCategory) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureHeroCard(
    item: BaseItemDto,
    images: FeatureCardImages?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val backdropUrl = images?.backdropUrl
    val logoUrl = images?.logoUrl

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(390.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(backdropUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .allowHardware(true)
                        .allowRgb565(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ImageSkeleton(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.86f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            .height(72.dp)
                            .fillMaxWidth(0.78f)
                    )
                } else if (images != null || item.id == null) {
                    Text(
                        text = item.name ?: "Unknown title",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 31.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(30.dp)
                            .background(Color(0xFF2C2D32), RoundedCornerShape(4.dp))
                    )
                }

                val metadataText = buildString {
                    val resolvedYear = item.productionYear ?: item.premiereDate
                        ?.take(4)
                        ?.toIntOrNull()
                    resolvedYear?.let { append(it.toString()) }
                    if (!item.type.isNullOrBlank()) {
                        if (isNotEmpty()) append(" | ")
                        append(
                            when (item.type) {
                                "Series" -> "TV Series"
                                else -> item.type
                            }
                        )
                    }
                }

                if (metadataText.isNotBlank()) {
                    Text(
                        text = metadataText,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val genres = item.genres.orEmpty().take(3)
                if (genres.isNotEmpty()) {
                    Text(
                        text = genres.joinToString(separator = " | "),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
private fun FeatureHeroSkeleton() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ImageSkeleton(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(30.dp)
                        .background(Color(0xFF2C2D32), RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(14.dp)
                        .background(Color(0xFF2C2D32), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun FeatureHeroError(error: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
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
