package com.jellycine.app.feature.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.jellycine.app.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jellycine.app.util.JellyfinPosterImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CoolLoadingAnimation(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Canvas(
        modifier = modifier.size(60.dp)
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 4

        rotate(rotation, pivot = center) {
            for (i in 0 until 8) {
                val angle = (i * 45f) * (kotlin.math.PI / 180f)
                val x = centerX + cos(angle) * radius * scale
                val y = centerY + sin(angle) * radius * scale
                val circleRadius = (8f - i) * 2f * scale

                drawCircle(
                    color = color.copy(alpha = 0.7f - (i * 0.08f)),
                    radius = circleRadius,
                    center = androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())
                )
            }
        }
    }
}

@Composable
fun DetailScreenContainer(
    itemId: String,
    onBackPressed: () -> Unit = {},
    onPlayClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var item by remember { mutableStateOf<BaseItemDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(itemId) {
        try {
            isLoading = true
            error = null

            val result = mediaRepository.getItemById(itemId)
            result.fold(
                onSuccess = { fetchedItem ->
                    item = fetchedItem
                    isLoading = false
                },
                onFailure = { exception ->
                    error = exception.message
                    isLoading = false
                }
            )
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CoolLoadingAnimation()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            }
        }
        error != null -> {
            LaunchedEffect(Unit) {
                onBackPressed()
            }
        }
        item != null -> {
            DetailScreen(
                item = item!!,
                onBackPressed = onBackPressed,
                onPlayClick = onPlayClick
            )
        }
        else -> {
            LaunchedEffect(Unit) {
                onBackPressed()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: BaseItemDto,
    onBackPressed: () -> Unit = {},
    onPlayClick: () -> Unit = {}
) {
    DetailContent(
        item = item,
        onBackPressed = onBackPressed,
        onPlayClick = onPlayClick
    )
}

@Composable
fun DetailContent(
    item: BaseItemDto,
    onBackPressed: () -> Unit = {},
    onPlayClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }
    var posterImageUrl by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember { mutableStateOf(item.userData?.isFavorite == true) }
    var showFullOverview by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        val itemId = item.id
        if (itemId != null) {
            backdropImageUrl = mediaRepository.getBackdropImageUrl(
                itemId = itemId,
                width = 1200,
                height = 675,
                quality = 95
            ).first()

            posterImageUrl = mediaRepository.getImageUrl(
                itemId = itemId,
                imageType = "Primary",
                width = 400,
                height = 600,
                quality = 95
            ).first()
        }
    }

    fun formatRuntime(ticks: Long?): String {
        if (ticks == null) return ""
        val minutes = (ticks / 10000000) / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
    }

    fun getVideoCodecInfo(item: BaseItemDto): String? {
        val videoStream = item.mediaStreams?.find { it.type == "Video" }
        return videoStream?.codec?.uppercase()
    }

    fun getAudioCodecInfo(item: BaseItemDto): String? {
        val audioStream = item.mediaStreams?.find { it.type == "Audio" }
        return audioStream?.codec?.uppercase()
    }

    fun getResolutionInfo(item: BaseItemDto): String? {
        val videoStream = item.mediaStreams?.find { it.type == "Video" }
        return if (videoStream?.width != null && videoStream.height != null) {
            "${videoStream.width}x${videoStream.height}"
        } else null
    }

    fun formatFileSize(bytes: Long?): String? {
        if (bytes == null || bytes <= 0) return null
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1) {
            String.format("%.1f GB", gb)
        } else {
            val mb = bytes / (1024.0 * 1024.0)
            String.format("%.0f MB", mb)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {

                JellyfinPosterImage(
                    imageUrl = backdropImageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    context = context,
                    contentScale = ContentScale.Crop
                )


                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Black
                                )
                            )
                        )
                )


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackPressed,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { isFavorite = !isFavorite },
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) Color.Red else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }


        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-85).dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {

                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        JellyfinPosterImage(
                            imageUrl = posterImageUrl,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            context = context,
                            contentScale = ContentScale.Crop
                        )
                    }


                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = item.name ?: "Unknown Title",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 32.sp
                        )


                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            item.productionYear?.let { year ->
                                Text(
                                    text = year.toString(),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }


                            item.runTimeTicks?.let { ticks ->
                                Text(
                                    text = formatRuntime(ticks),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }

                        // Rating on separate line to prevent layout breaking
                        item.officialRating?.let { rating ->
                            Surface(
                                color = Color(0xFF1A1A1A),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = rating,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                        }


                        item.communityRating?.let { rating ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = "Rating",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = String.format("%.1f", rating),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }


                    }
                }


                item.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        items(genres) { genre ->
                            Surface(
                                color = Color(0xFF2A2A2A),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Text(
                                    text = genre,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Codecs Info section - moved here to eliminate gap
                Text(
                    text = "Codecs Info",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {

                    getResolutionInfo(item)?.let { resolution ->
                        item {
                            Surface(
                                color = Color(0xFF1A1A1A),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.HighQuality,
                                        contentDescription = "Video Quality",
                                        tint = Color(0xFF00C853),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = when {
                                            resolution.contains("3840") -> "4K"
                                            resolution.contains("1920") -> "HD"
                                            resolution.contains("1280") -> "720p"
                                            else -> resolution
                                        },
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Video Codec
                    getVideoCodecInfo(item)?.let { codec ->
                        item {
                            Surface(
                                color = Color(0xFF1A1A1A),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VideoLibrary,
                                        contentDescription = "Video Codec",
                                        tint = Color(0xFFE91E63),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = codec,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    item.mediaStreams?.firstOrNull { it.type == "Audio" }?.let { audioStream ->
                        val audioDisplayInfo = getAudioDisplayInfo(audioStream)
                        if (audioDisplayInfo.isNotEmpty()) {
                            item {
                                Surface(
                                    color = Color(0xFF1A1A1A),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        // Dolby symbol or audio icon
                                        if (isDolbyAudio(audioStream)) {
                                            // Use vector drawable Dolby logo
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_dolby_logo),
                                                contentDescription = "Dolby Audio",
                                                tint = Color.Unspecified, // Keep original blue and white colors
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                                contentDescription = "Audio",
                                                tint = Color(0xFF2196F3),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Text(
                                            text = audioDisplayInfo,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Audio Channels
                    item.mediaStreams?.firstOrNull { it.type == "Audio" }?.channels?.let { channels ->
                        item {
                            Surface(
                                color = Color(0xFF1A1A1A),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SurroundSound,
                                        contentDescription = "Audio Channels",
                                        tint = Color(0xFF9C27B0),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = when (channels) {
                                            1 -> "Mono"
                                            2 -> "Stereo"
                                            6 -> "5.1"
                                            8 -> "7.1"
                                            else -> "${channels}ch"
                                        },
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // HDR Support
                    item.mediaStreams?.firstOrNull { it.type == "Video" }?.videoRange?.let { videoRange ->
                        if (videoRange == "HDR") {
                            item {
                                Surface(
                                    color = Color(0xFF1A1A1A),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.HighQuality,
                                            contentDescription = "HDR",
                                            tint = Color(0xFFFFEB3B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "HDR",
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Subtitles
                    item.mediaStreams?.any { it.type == "Subtitle" }?.let { hasSubtitles ->
                        if (hasSubtitles) {
                            item {
                                Surface(
                                    color = Color(0xFF1A1A1A),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Subtitles,
                                            contentDescription = "Subtitles",
                                            tint = Color(0xFFFF9800),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "CC",
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons Section
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 32.dp)
            ) {
                    // Play Button - Primary Action
                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0080FF)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Play",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Download Button
                    OutlinedButton(
                        onClick = { /* TODO: Download */ },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                            containerColor = Color.Transparent
                        ),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Download",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
            }
        }

        // Overview Section
        item {
            item.overview?.let { overview ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 24.dp)
                ) {
                    Text(
                        text = "Overview",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A1A)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                text = overview,
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 22.sp,
                                maxLines = if (showFullOverview) Int.MAX_VALUE else 4,
                                overflow = if (showFullOverview) TextOverflow.Visible else TextOverflow.Ellipsis
                            )

                            if (overview.length > 200) {
                                TextButton(
                                    onClick = { showFullOverview = !showFullOverview },
                                    modifier = Modifier.padding(top = 8.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFF0080FF)
                                    )
                                ) {
                                    Text(
                                        text = if (showFullOverview) "Show Less" else "Read More",
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

        // Technical Information Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp)
            ) {
                Text(
                    text = "Technical Information",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item.mediaSources?.firstOrNull()?.size?.let { size ->
                            formatFileSize(size)?.let { formattedSize ->
                                ModernFileInfoRow(
                                    label = "File Size",
                                    value = formattedSize,
                                    icon = Icons.Rounded.Storage
                                )
                            }
                        }

                        item.mediaStreams?.firstOrNull { it.type == "Video" }?.let { videoStream ->
                            val resolution = getResolutionInfo(item)
                            val codec = getVideoCodecInfo(item)
                            val videoInfo = buildString {
                                if (!resolution.isNullOrEmpty()) append(resolution)
                                if (!codec.isNullOrEmpty()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(codec)
                                }
                                if (videoStream.videoRange == "HDR") {
                                    if (isNotEmpty()) append(" • ")
                                    append("HDR")
                                }
                            }

                            if (videoInfo.isNotEmpty()) {
                                ModernFileInfoRow(
                                    label = "Video Quality",
                                    value = videoInfo,
                                    icon = Icons.Rounded.VideoFile
                                )
                            }
                        }

                        val audioStreams = item.mediaStreams?.filter { it.type == "Audio" } ?: emptyList()
                        if (audioStreams.isNotEmpty()) {
                            val audioInfo = if (audioStreams.size == 1) {
                                getEnhancedAudioInfo(audioStreams.first())
                            } else {
                                val primaryTrack = audioStreams.find { it.isDefault == true } ?: audioStreams.first()
                                val primaryInfo = getEnhancedAudioInfo(primaryTrack)
                                if (audioStreams.size > 1) {
                                    "$primaryInfo + ${audioStreams.size - 1} more"
                                } else {
                                    primaryInfo
                                }
                            }

                            if (audioInfo.isNotEmpty()) {
                                ModernFileInfoRow(
                                    label = "Audio",
                                    value = audioInfo,
                                    icon = Icons.Rounded.AudioFile
                                )
                            }
                        }

                        val subtitleStreams = item.mediaStreams?.filter { it.type == "Subtitle" }
                        if (!subtitleStreams.isNullOrEmpty()) {
                            val subtitleInfo = when {
                                subtitleStreams.size == 1 -> {
                                    val stream = subtitleStreams.first()
                                    val language = stream.language ?: stream.displayTitle ?: "Unknown"
                                    val codec = getSubtitleCodecDisplayName(stream.codec)
                                    "${language.uppercase()} • $codec"
                                }
                                subtitleStreams.size <= 3 -> {
                                    subtitleStreams.joinToString(", ") { stream ->
                                        val language = stream.language ?: stream.displayTitle ?: "Unknown"
                                        language.uppercase()
                                    }
                                }
                                else -> {
                                    val languages = subtitleStreams.take(3).map { stream ->
                                        (stream.language ?: stream.displayTitle ?: "Unknown").uppercase()
                                    }.distinct()
                                    "${languages.joinToString(", ")} + ${subtitleStreams.size - 3} more"
                                }
                            }

                            ModernFileInfoRow(
                                label = "Subtitles",
                                value = subtitleInfo,
                                icon = Icons.Rounded.Subtitles
                            )
                        }
                    }
                }
            }
        }

        // Cast Section
        item {
            val actors = item.people?.filter { it.type == "Actor" }?.take(8) ?: emptyList()

            if (actors.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 24.dp)
                ) {
                    Text(
                        text = "Cast",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        items(actors) { person ->
                            CastMemberCard(
                                person = person,
                                mediaRepository = mediaRepository
                            )
                        }
                    }
                }
            }
        }

        // Bottom Spacing
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun CastMemberCard(
    person: BaseItemPerson,
    mediaRepository: MediaRepository
) {
    var personImageUrl by remember(person.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(person.id) {
        if (person.id != null) {
            personImageUrl = getPersonImageUrl(person.id, mediaRepository).first()
        }
    }

    Card(
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Card(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                if (personImageUrl != null) {
                    AsyncImage(
                        model = personImageUrl,
                        contentDescription = person.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = person.name,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = person.name ?: "Unknown",
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )

            person.role?.let { role ->
                Text(
                    text = role,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ModernFileInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = Color(0xFF2A2A2A)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = Color(0xFF0080FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FileInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DetailScreenPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "Seal Team",
            overview = "After his best friend is killed in a shark attack, Quinn, a lovable yet tenacious seal assembles a SEAL TEAM to fight back against a gang of sharks overtaking the neighborhood.",
            productionYear = 2021,
            runTimeTicks = 6000000000L, // 1h 40m
            communityRating = 7.6f,
            officialRating = "TV-Y7",
            genres = listOf("Animation", "Family", "Adventure"),
            userData = null,
            people = null,
            studios = null
        )

        DetailContent(
            item = mockItem,
            onBackPressed = {},
            onPlayClick = {}
        )
    }
}

private fun getEnhancedAudioInfo(audioStream: MediaStream): String {
    val codec = audioStream.codec?.uppercase() ?: ""
    val language = audioStream.language ?: audioStream.displayTitle ?: "ENG"
    val channels = audioStream.channels ?: 0
    val channelLayout = audioStream.channelLayout
    val title = audioStream.title
    val profile = audioStream.profile

    val spatialAudioInfo = detectSpatialAudio(codec, channels, channelLayout, title, profile)
    val channelInfo = when {
        spatialAudioInfo.isNotEmpty() -> spatialAudioInfo
        channels > 0 -> when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${channels}ch"
        }
        else -> ""
    }

    return buildString {
        // Language
        if (language.isNotEmpty() && language.uppercase() != "UNKNOWN") {
            append(language.uppercase())
        }

        // Codec with spatial audio enhancement
        if (codec.isNotEmpty()) {
            if (isNotEmpty()) append(" - ")
            append(getDisplayCodecName(codec, spatialAudioInfo.isNotEmpty()))
        }

        // Channel layout
        if (channelInfo.isNotEmpty()) {
            if (isNotEmpty()) append(" + ")
            append(channelInfo)
        }

        // Default indicator
        if (audioStream.isDefault == true) {
            if (isNotEmpty()) append(" - Default")
        }
    }
}

private fun detectSpatialAudio(codec: String, channels: Int, channelLayout: String?, title: String?, profile: String?): String {
    val codecLower = codec.lowercase()
    val titleLower = title?.lowercase() ?: ""
    val layoutLower = channelLayout?.lowercase() ?: ""
    val profileLower = profile?.lowercase() ?: ""

    // Enhanced Dolby Atmos detection
    when {
        // TrueHD with Atmos - more comprehensive detection
        codecLower.contains("truehd") && (
            titleLower.contains("atmos") ||
            layoutLower.contains("atmos") ||
            profileLower.contains("atmos") ||
            titleLower.contains("dolby atmos") ||
            // Common Atmos indicators in metadata
            channels > 8 // TrueHD with more than 8 channels is likely Atmos
        ) -> return "Dolby Atmos"

        // E-AC-3 with Atmos
        (codecLower.contains("eac3") || codecLower.contains("e-ac-3") || codecLower.contains("ec-3")) && (
            titleLower.contains("atmos") ||
            layoutLower.contains("atmos") ||
            profileLower.contains("atmos") ||
            titleLower.contains("dolby atmos") ||
            channels > 8 // E-AC-3 with more than 8 channels is likely Atmos
        ) -> return "Dolby Atmos"

        // DTS:X detection
        titleLower.contains("dts:x") ||
        layoutLower.contains("dts:x") ||
        profileLower.contains("dts:x") ||
        titleLower.contains("dtsx") -> return "DTS:X"

        // Object-based audio detection by channel layout patterns
        layoutLower.contains("7.1.4") || layoutLower.contains("(7.1.4)") -> return "7.1.4 (Spatial)"
        layoutLower.contains("5.1.4") || layoutLower.contains("(5.1.4)") -> return "5.1.4 (Spatial)"
        layoutLower.contains("7.1.2") || layoutLower.contains("(7.1.2)") -> return "7.1.2 (Spatial)"
        layoutLower.contains("5.1.2") || layoutLower.contains("(5.1.2)") -> return "5.1.2 (Spatial)"

        // Detect by codec + high channel count (common for spatial audio)
        (codecLower.contains("truehd") || codecLower.contains("eac3")) && channels >= 10 -> return "Dolby Atmos"
        codecLower.contains("dts") && channels >= 10 -> return "DTS:X"

        // Generic spatial audio for high channel counts
        channels >= 12 -> return "${channels}ch (Spatial)"
    }

    return ""
}

private fun getDisplayCodecName(codec: String, isSpatial: Boolean): String {
    return when (codec.uppercase()) {
        "TRUEHD" -> if (isSpatial) "TrueHD" else "TrueHD"
        "EAC3", "E-AC-3" -> if (isSpatial) "E-AC-3" else "E-AC-3"
        "AC3", "AC-3" -> "AC-3"
        "DTS" -> "DTS"
        "DTSHD" -> "DTS-HD"
        "AAC" -> "AAC"
        "MP3" -> "MP3"
        "FLAC" -> "FLAC"
        "PCM" -> "PCM"
        "OPUS" -> "Opus"
        "VORBIS" -> "Vorbis"
        else -> codec.uppercase()
    }
}

private fun getAudioDisplayInfo(audioStream: MediaStream): String {
    val codec = audioStream.codec?.uppercase() ?: ""
    val channels = audioStream.channels ?: 0
    val channelLayout = audioStream.channelLayout
    val title = audioStream.title
    val profile = audioStream.profile

    val spatialInfo = detectSpatialAudio(codec, channels, channelLayout, title, profile)
    if (spatialInfo.isNotEmpty()) {
        return when {
            spatialInfo.contains("Dolby Atmos") -> "Dolby Digital+ - $spatialInfo"
            spatialInfo.contains("DTS:X") -> "DTS - $spatialInfo"
            else -> "$spatialInfo"
        }
    }

    return buildString {
        when (codec) {
            "EAC3", "E-AC-3" -> append("Dolby Digital+")
            "AC3", "AC-3" -> append("Dolby Digital")
            "TRUEHD" -> append("Dolby TrueHD")
            "DTS" -> append("DTS")
            "DTSHD" -> append("DTS-HD")
            "AAC" -> append("AAC")
            "MP3" -> append("MP3")
            "FLAC" -> append("FLAC")
            "PCM" -> append("PCM")
            else -> append(codec)
        }

        // Add channel info
        val channelInfo = when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> if (channels > 0) "${channels}ch" else ""
        }

        if (channelInfo.isNotEmpty()) {
            append(" - $channelInfo")
        }

        // Add default indicator if needed
        if (audioStream.isDefault == true) {
            append(" - Default")
        }
    }
}

// Check if audio is Dolby-based for special icon
private fun isDolbyAudio(audioStream: MediaStream): Boolean {
    val codec = audioStream.codec?.uppercase() ?: ""
    val title = audioStream.title?.lowercase() ?: ""
    val profile = audioStream.profile?.lowercase() ?: ""

    return codec.contains("EAC3") ||
           codec.contains("E-AC-3") ||
           codec.contains("AC3") ||
           codec.contains("AC-3") ||
           codec.contains("TRUEHD") ||
           title.contains("dolby") ||
           profile.contains("dolby") ||
           title.contains("atmos")
}

private fun getSubtitleCodecDisplayName(codec: String?): String {
    return when (codec?.uppercase()) {
        "SUBRIP", "SRT" -> "SRT"
        "ASS", "SSA" -> "ASS"
        "VTT", "WEBVTT" -> "VTT"
        "PGSSUB", "PGS" -> "PGS"
        "DVDSUB" -> "DVDSUB"
        "MOV_TEXT" -> "MOV"
        "TTML" -> "TTML"
        null, "" -> "SRT"
        else -> codec.uppercase()
    }
}

private fun getPersonImageUrl(personId: String?, mediaRepository: MediaRepository): Flow<String?> {
    return if (personId != null) {
        mediaRepository.getImageUrl(
            itemId = personId,
            imageType = "Primary",
            width = 120,
            height = 120,
            quality = 90
        )
    } else {
        flowOf(null)
    }
}