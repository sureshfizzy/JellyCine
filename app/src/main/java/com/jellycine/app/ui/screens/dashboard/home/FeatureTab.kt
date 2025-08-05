package com.jellycine.app.ui.screens.dashboard.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.CachePolicy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.app.util.image.ImageSkeleton
import com.jellycine.app.ui.components.common.*
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.model.UserDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius

private object CachedData {
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
        return continueWatchingItems.isEmpty() || System.currentTimeMillis() - continueWatchingLastLoadTime > 300_000
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

@Composable
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
    val authRepository = remember { com.jellycine.data.repository.AuthRepositoryProvider.getInstance(context) }

    // User information state - use stored credentials
    var storedUsername by remember { mutableStateOf(CachedData.username) }
    var userProfileImageUrl by remember { mutableStateOf(CachedData.userImageUrl) }
    var hasLoadedUser by remember { mutableStateOf(!CachedData.username.isNullOrBlank()) }
    
    // Auto-rotation state
    var currentIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    // Load user information - use stored username and fetch image
    LaunchedEffect(hasLoadedUser) {
        if (hasLoadedUser) return@LaunchedEffect // Skip if already loaded

        try {
            // Get stored username from AuthRepository
            val username = authRepository.getUsername().first()
            if (!username.isNullOrBlank()) {
                storedUsername = username
                // Get user profile image URL
                userProfileImageUrl = mediaRepository.getUserProfileImageUrl()
                CachedData.updateUserData(storedUsername, userProfileImageUrl) // Update cache
                hasLoadedUser = true
            } else {
                // If no username stored, try to get from current user
                val userResult = mediaRepository.getCurrentUser()
                userResult.fold(
                    onSuccess = { user ->
                        storedUsername = user.name ?: "User"
                        userProfileImageUrl = mediaRepository.getUserProfileImageUrl()
                        CachedData.updateUserData(storedUsername, userProfileImageUrl) // Update cache
                        hasLoadedUser = true
                    },
                    onFailure = {
                        storedUsername = "User"
                        hasLoadedUser = true
                    }
                )
            }
        } catch (e: Exception) {
            // Fallback to default username if stored username is not available
            storedUsername = "User"
            hasLoadedUser = true
        }
    }

    // Auto-rotation with longer delay and better performance
    LaunchedEffect(featuredItems) {
        if (featuredItems.isNotEmpty() && featuredItems.size > 1) {
            while (true) {
                delay(8000)
                val nextIndex = (currentIndex + 1) % featuredItems.size
                currentIndex = nextIndex

                // Smooth scroll animation
                try {
                    listState.animateScrollToItem(
                        index = nextIndex,
                        scrollOffset = 0
                    )
                } catch (e: Exception) {
                    // Handle any animation interruptions gracefully
                }
            }
        }
    }

    // Preload images for better performance
    LaunchedEffect(featuredItems) {
        if (featuredItems.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                featuredItems.take(3).forEach { item ->
                    try {
                        item.id?.let { itemId ->
                            async {
                                mediaRepository.getImageUrl(
                                    itemId = itemId,
                                    imageType = "Backdrop",
                                    width = 1200,
                                    height = 680,
                                    quality = 95
                                ).first()
                            }
                            async {
                                mediaRepository.getImageUrl(
                                    itemId = itemId,
                                    imageType = "Logo",
                                    width = 400,
                                    height = 200,
                                    quality = 95
                                ).first()
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }
    
    // Handle manual scroll to update current index
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (featuredItems.isNotEmpty()) {
            currentIndex = listState.firstVisibleItemIndex
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        // Top header with logo and user avatar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 48.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (!storedUsername.isNullOrBlank()) "Welcome $storedUsername" else "Welcome",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // User profile avatar
            UserProfileAvatar(
                imageUrl = userProfileImageUrl,
                userName = storedUsername,
                onClick = onLogout,
                modifier = Modifier.size(32.dp)
            )
        }

        // Category pills
        CategoryPills(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Featured content card
        when {
            isLoading && featuredItems.isEmpty() -> {
                ModernFeatureCardSkeleton()
            }

            error != null && featuredItems.isEmpty() -> {
                ModernErrorCard(error = error)
            }

            featuredItems.isNotEmpty() -> {
                // Modern featured card
                @OptIn(ExperimentalFoundationApi::class)
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
                ) {
                    items(
                        count = featuredItems.size,
                        key = { index -> featuredItems[index].id ?: index }
                    ) { index ->
                        ModernFeatureCard(
                            item = featuredItems[index],
                            mediaRepository = mediaRepository,
                            onClick = { onItemClick(featuredItems[index]) },
                            modifier = Modifier.fillParentMaxWidth()
                        )
                    }
                }
            }

            else -> {
                ModernErrorCard(error = "No featured content available")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernFeatureCard(
    item: BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageUrl by remember(item.id, item.seriesId) { mutableStateOf<String?>(null) }
    var logoUrl by remember(item.id, item.seriesId) { mutableStateOf<String?>(null) }

    // Get backdrop image URL and logo with background threading
    LaunchedEffect(item.id) {
        val itemId = item.id
        if (itemId != null) {
            withContext(Dispatchers.IO) {
                try {
                    val backdropDeferred = async {
                        mediaRepository.getImageUrl(
                            itemId = itemId,
                            imageType = "Backdrop",
                            width = 1200,
                            height = 680,
                            quality = 95
                        ).first()
                    }

                    val logoDeferred = async {
                        mediaRepository.getImageUrl(
                            itemId = itemId,
                            imageType = "Logo",
                            width = 400,
                            height = 200,
                            quality = 95
                        ).first()
                    }

                    // Wait for both to complete
                    val backdrop = backdropDeferred.await()
                    val logo = logoDeferred.await()

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        imageUrl = backdrop
                        logoUrl = logo
                    }
                } catch (e: Exception) {
                    // Handle errors gracefully
                    android.util.Log.e("FeatureCard", "Error loading images for $itemId", e)
                }
            }
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background image
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                context = context,
                contentScale = ContentScale.Crop
            )

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Content at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo or Title - centered and larger
                if (!logoUrl.isNullOrEmpty()) {
                    JellyfinPosterImage(
                        imageUrl = logoUrl,
                        contentDescription = "${item.name} logo",
                        modifier = Modifier
                            .height(80.dp)
                            .fillMaxWidth(0.8f),
                        context = context,
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = item.name ?: "Unknown Title",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp
                    )
                }

                val typeText = when (item.type) {
                    "Movie" -> "Movie"
                    "Series" -> "TV Series"
                    else -> item.type ?: "Media"
                }

                val genreText = item.genres?.take(3)?.joinToString(" • ") ?: ""
                val displayText = if (genreText.isNotEmpty()) {
                    "$typeText • $genreText"
                } else {
                    typeText
                }

                Text(
                    text = displayText,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Play button
                    Button(
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play",
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // View Details button
                    Button(
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "View details",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Details",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingBadge(
    rating: Float,
    type: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = type,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = when (type) {
                    "RT" -> "${rating.toInt()}%"
                    "IMDB" -> String.format("%.1f", rating)
                    else -> rating.toString()
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FeatureCard(
    item: BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageUrl by remember(item.id, item.seriesId) { mutableStateOf<String?>(null) }
    
    // Get image URL
    val actualItemId = remember(item.id, item.type, item.seriesId) {
        if (item.type == "Episode" && !item.seriesId.isNullOrBlank()) {
            item.seriesId!!
        } else {
            item.id
        }
    }

    LaunchedEffect(actualItemId) {
        if (actualItemId != null) {
            // Use IO dispatcher for network operations
            withContext(Dispatchers.IO) {
                try {
                    // Try to get backdrop image first, fallback to primary if not available
                    var backdropUrl = mediaRepository.getBackdropImageUrl(
                        itemId = actualItemId,
                        width = 1200,
                        height = 680,
                        quality = 95
                    ).first()

                    // If backdrop is not available, fallback to primary image
                    if (backdropUrl.isNullOrEmpty()) {
                        backdropUrl = mediaRepository.getImageUrl(
                            itemId = actualItemId,
                            width = 1200,
                            height = 680,
                            quality = 95
                        ).first()
                    }

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        imageUrl = backdropUrl
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FeatureCard", "Error loading image for $actualItemId", e)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(600.dp)
            .clickable { onClick() }
    ) {
        // Track image loading state directly
        var imageState by remember(item.id, imageUrl) {
            mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
        }

            // Edge-to-edge immersive image
            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(200)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .allowHardware(true) // Enable hardware acceleration
                        .allowRgb565(true) // Better memory efficiency
                        .build(),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(), // No clipping for edge-to-edge
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        imageState = state
                    }
                )
            }

            // Remove individual card skeleton - only use main FeatureTabSkeleton
            // No skeleton overlay in individual cards

            // Multi-layer gradient overlay for immersive experience
            if (!imageUrl.isNullOrEmpty()) {
                // Top gradient for header area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )

                // Bottom gradient for content area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.9f)
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
            }

            // Main content at the bottom
            if (!imageUrl.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                Text(
                    text = item.name ?: "Unknown Title",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 32.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }

                    item.officialRating?.let { rating ->
                        Text(
                            text = " • $rating",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }

                item.genres?.take(2)?.let { genres ->
                    if (genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = genres.joinToString(" • "),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Play and Details buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Play button
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Details button
                    OutlinedButton(
                        onClick = onClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                            containerColor = Color.Black.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            2.dp,
                            Color.White.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Details",
                            color = Color.White,
                            fontSize = 16.sp,
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
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrEmpty()) {
            // Show user profile image
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = "Profile picture of $userName",
                modifier = Modifier.fillMaxSize(),
                context = context
            )
        } else {
            // Show initials as fallback
            val initials = userName?.let { name ->
                name.split(" ")
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .take(2)
                    .joinToString("")
            } ?: "JU"

            Text(
                text = initials,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Skeleton Components for Feature Tab
@Composable
private fun CategoryPills(
    selectedCategory: String = "Home",
    onCategorySelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val categories = listOf("Home", "Movies", "TV Shows")

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categories) { category ->
            CategoryPill(
                text = category,
                isSelected = category == selectedCategory,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.White else Color.Transparent
        ),
        border = if (!isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)) else null,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Float = 12f
) {
    Box(
        modifier = modifier
            .background(
                color = Color(0xFF2A2A2A), // Dark grey instead of white with alpha
                shape = RoundedCornerShape(cornerRadius.dp)
            )
    )
}

@Composable
private fun ModernFeatureCardSkeleton() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            ImageSkeleton(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp)
            )

            // Content skeleton at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(32.dp),
                    cornerRadius = 4f
                )

                SkeletonBox(
                    modifier = Modifier
                        .width(200.dp)
                        .height(16.dp),
                    cornerRadius = 4f
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        cornerRadius = 8f
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        cornerRadius = 8f
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernErrorCard(error: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 48.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = "Unable to load content",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = error ?: "Unknown error occurred",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


