package com.jellycine.app.feature.dashboard.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.jellycine.app.util.JellyfinPosterImage
import com.jellycine.app.util.ImageSkeleton
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.model.UserDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    onItemClick: (BaseItemDto) -> Unit = {},
    onLogout: () -> Unit = {},
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

    // Auto-rotation effect with longer delay for better performance
    LaunchedEffect(featuredItems) {
        if (featuredItems.isNotEmpty() && featuredItems.size > 1) {
            while (true) {
                delay(8000) // Increased to 8 seconds to reduce performance impact
                val nextIndex = (currentIndex + 1) % featuredItems.size
                currentIndex = nextIndex

                // Smooth scroll animation with better easing
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
    
    // Handle manual scroll to update current index
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (featuredItems.isNotEmpty()) {
            currentIndex = listState.firstVisibleItemIndex
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black) // AMOLED black background
    ) {
        // Section Header with user profile avatar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (!storedUsername.isNullOrBlank()) "Welcome $storedUsername" else "Welcome",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // User profile avatar
            UserProfileAvatar(
                imageUrl = userProfileImageUrl,
                userName = storedUsername,
                onClick = onLogout,
                modifier = Modifier.size(40.dp)
            )
        }
        
        when {
            isLoading -> {
                FeatureTabSkeleton()
            }
            
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp) // Reduced height
                        .background(Color.Black), // AMOLED black background
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚠️",
                            fontSize = 32.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Failed to load featured content",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error ?: "Unknown error",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )

                    }
                }
            }
            
            featuredItems.isNotEmpty() -> {
                // Horizontal scrolling carousel with smooth auto-rotation
                @OptIn(ExperimentalFoundationApi::class)
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp), // No gap for seamless scrolling
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
                ) {
                    items(
                        count = featuredItems.size,
                        key = { index -> featuredItems[index].id ?: index }
                    ) { index ->
                        FeatureCard(
                            item = featuredItems[index],
                            mediaRepository = mediaRepository,
                            onClick = { onItemClick(featuredItems[index]) },
                            modifier = Modifier.fillParentMaxWidth() // Each item takes full width
                        )
                    }
                }
            }
            
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp) // Reduced height
                        .background(Color.Black), // AMOLED black background
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No featured content available",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
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
    
    // Get image URL with optimized quality for smooth performance
    LaunchedEffect(item.id) {
        val itemId = item.id
        if (itemId != null) {
            // For episodes, get the series backdrop instead
            val actualItemId = if (item.type == "Episode" && !item.seriesId.isNullOrBlank()) {
                item.seriesId!!
            } else {
                itemId
            }

            // Try to get backdrop image first, fallback to primary if not available
            var backdropUrl = mediaRepository.getBackdropImageUrl(
                itemId = actualItemId,
                width = 800, // Optimized width for feature cards
                height = 450, // Optimized height for feature cards
                quality = 75 // Reduced quality for faster loading
            ).first()

            // If backdrop is not available, fallback to primary image
            if (backdropUrl.isNullOrEmpty()) {
                backdropUrl = mediaRepository.getImageUrl(
                    itemId = actualItemId,
                    width = 800,
                    height = 450,
                    quality = 75
                ).first()
            }

            imageUrl = backdropUrl
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    Card(
        modifier = modifier
            .fillMaxWidth() // Full width for better coverage
            .height(450.dp), // Optimized height for better performance
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Track image loading state directly
            var imageState by remember(item.id, imageUrl) {
                mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
            }

            // Always show the image component
            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(300)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .allowHardware(false)
                        .build(),
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        imageState = state
                    }
                )
            }

            // Remove individual card skeleton - only use main FeatureTabSkeleton
            // No skeleton overlay in individual cards

            // Enhanced Gradient Overlay for better text readability - always show when image URL exists
            if (!imageUrl.isNullOrEmpty()) {
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
                            ),
                            RoundedCornerShape(20.dp)
                        )
                )
            }

            // Content - always show when image URL exists
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
                    fontSize = 18.sp, // Increased font size
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp // Increased font size
                        )
                    }

                    item.officialRating?.let { rating ->
                        Text(
                            text = " • $rating",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp // Increased font size
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Play button
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Play",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Details button
                    OutlinedButton(
                        onClick = onClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text(
                            text = "Details",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
private fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Float = 12f
) {
    Box(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(cornerRadius.dp)
            )
    )
}

@Composable
private fun FeatureTabSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        // Reduced spacing to move skeleton up and fill the gap
        Spacer(modifier = Modifier.height(16.dp)) // Smaller gap between Welcome and skeleton

        // Feature card skeleton moved to top - matches actual FeatureCard layout
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background image skeleton
                ImageSkeleton(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp)
                )

                // Gradient overlay to match actual card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            ),
                            RoundedCornerShape(20.dp)
                        )
                )

                // Content skeleton at bottom - matches actual layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Title skeleton (2 lines max)
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(18.dp),
                        cornerRadius = 4f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(18.dp),
                        cornerRadius = 4f
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Year and rating skeleton
                    SkeletonBox(
                        modifier = Modifier
                            .width(100.dp)
                            .height(14.dp),
                        cornerRadius = 4f
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Genres skeleton
                    SkeletonBox(
                        modifier = Modifier
                            .width(140.dp)
                            .height(12.dp),
                        cornerRadius = 4f
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons skeleton - matches actual Play and Details buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Play button skeleton
                        SkeletonBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            cornerRadius = 8f
                        )

                        // Details button skeleton
                        SkeletonBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            cornerRadius = 8f
                        )
                    }
                }
            }
        }
    }
}
