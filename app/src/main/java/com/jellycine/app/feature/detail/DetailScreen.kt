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
import android.content.Context

@Composable
fun LoadingAnimation(
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
                    LoadingAnimation()
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

    // Get device audio capabilities
    val deviceCapabilities = rememberAudioCapabilities(context)

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(0.dp)
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
                                    text = CodecUtils.formatRuntime(ticks),
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

                // Codecs Info section
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

                    CodecUtils.getResolutionInfo(item)?.let { resolution ->
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
                    CodecUtils.getVideoCodecInfo(item)?.let { codec ->
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
                        val audioDisplayInfo = CodecUtils.getAudioDisplayInfo(audioStream)
                        val spatialInfo = CodecCapabilityManager.detectSpatialAudio(
                            audioStream.codec?.uppercase() ?: "",
                            audioStream.channels ?: 0,
                            audioStream.channelLayout,
                            audioStream.title,
                            audioStream.profile
                        )

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
                                        // Spatial audio indicator or Dolby symbol or audio icon
                                        when {
                                            spatialInfo.contains("Dolby Atmos") -> {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_dolby_atmos),
                                                    contentDescription = "Dolby Atmos",
                                                    tint = Color.Unspecified,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            spatialInfo.contains("DTS:X") -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.SurroundSound,
                                                    contentDescription = "DTS:X",
                                                    tint = Color(0xFFFF5722),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            spatialInfo.isNotEmpty() -> {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_spatial_audio),
                                                    contentDescription = "Spatial Audio",
                                                    tint = Color.Unspecified,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            CodecCapabilityManager.isDolbyAudio(audioStream) -> {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_dolby_logo),
                                                    contentDescription = "Dolby Audio",
                                                    tint = Color.Unspecified,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                                    contentDescription = "Audio",
                                                    tint = Color(0xFF2196F3),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
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
                    item.mediaStreams?.firstOrNull { it.type == "Video" }?.let { videoStream ->
                        val hdrInfo = CodecCapabilityManager.detectHDRFormat(videoStream)
                        if (hdrInfo.isNotEmpty()) {
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
                                        when {
                                            hdrInfo.contains("Dolby Vision") -> {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_dolby_logo),
                                                    contentDescription = hdrInfo,
                                                    tint = Color.Unspecified,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.HighQuality,
                                                    contentDescription = hdrInfo,
                                                    tint = when {
                                                        hdrInfo.contains("HDR10+") -> Color(0xFF00BCD4)
                                                        else -> Color(0xFFFFEB3B)
                                                    },
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = hdrInfo,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Device Audio Capability Info
                    if (deviceCapabilities.connectedAudioDevice != "Unknown") {
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
                                    Icon(
                                        imageVector = when (deviceCapabilities.connectedAudioDevice) {
                                            "HDMI" -> Icons.Rounded.Tv
                                            "Bluetooth" -> Icons.Rounded.Bluetooth
                                            "USB Headset" -> Icons.Rounded.Headphones
                                            "Wired Headphones", "Wired Headset" -> Icons.Rounded.Headphones
                                            else -> Icons.Rounded.Speaker
                                        },
                                        contentDescription = "Audio Device",
                                        tint = if (deviceCapabilities.canProcessSpatialAudio) Color(0xFF4CAF50) else Color(0xFF757575),
                                        modifier = Modifier.size(16.dp)
                                    )

                                    Column {
                                        Text(
                                            text = deviceCapabilities.connectedAudioDevice,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (deviceCapabilities.canProcessSpatialAudio) {
                                            Text(
                                                text = "Spatial Audio Ready",
                                                fontSize = 11.sp,
                                                color = Color(0xFF4CAF50),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Spatial Audio Badge
                    item.mediaStreams?.firstOrNull { it.type == "Audio" }?.let { audioStream ->
                        val spatialInfo = CodecCapabilityManager.detectSpatialAudio(
                            audioStream.codec?.uppercase() ?: "",
                            audioStream.channels ?: 0,
                            audioStream.channelLayout,
                            audioStream.title,
                            audioStream.profile
                        )

                        if (spatialInfo.isNotEmpty()) {
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
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_spatial_audio),
                                            contentDescription = "Spatial Audio",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(20.dp)
                                        )

                                        Text(
                                            text = "Spatial Audio",
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
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

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
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

                // Overview Section
                item.overview?.let { overview ->
                    Text(
                        text = "Overview",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
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

                // Technical Information Section
                Text(
                    text = "Technical Information",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
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
                            CodecUtils.getFileSize(size)?.let { formattedSize ->
                                ModernFileInfoRow(
                                    label = "File Size",
                                    value = formattedSize,
                                    icon = Icons.Rounded.Storage
                                )
                            }
                        }

                        item.mediaStreams?.firstOrNull { it.type == "Video" }?.let { videoStream ->
                            val resolution = CodecUtils.getResolutionInfo(item)
                            val codec = CodecUtils.getVideoCodecInfo(item)
                            val videoInfo = buildString {
                                if (!resolution.isNullOrEmpty()) append(resolution)
                                if (!codec.isNullOrEmpty()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(codec)
                                }
                                val hdrInfo = CodecCapabilityManager.detectHDRFormat(videoStream)
                                if (hdrInfo.isNotEmpty()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(hdrInfo)
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
                            val primaryTrack = audioStreams.find { it.isDefault == true } ?: audioStreams.first()
                            val spatialInfo = CodecCapabilityManager.detectSpatialAudio(
                                primaryTrack.codec?.uppercase() ?: "",
                                primaryTrack.channels ?: 0,
                                primaryTrack.channelLayout,
                                primaryTrack.title,
                                primaryTrack.profile
                            )

                            val audioInfo = if (audioStreams.size == 1) {
                                CodecUtils.getEnhancedAudioInfo(audioStreams.first())
                            } else {
                                val primaryInfo = CodecUtils.getEnhancedAudioInfo(primaryTrack)
                                if (audioStreams.size > 1) {
                                    "$primaryInfo + ${audioStreams.size - 1} more"
                                } else {
                                    primaryInfo
                                }
                            }

                            if (audioInfo.isNotEmpty()) {
                                ModernFileInfoRow(
                                    label = if (spatialInfo.isNotEmpty()) "Audio (Spatial)" else "Audio",
                                    value = audioInfo,
                                    icon = if (spatialInfo.isNotEmpty()) Icons.Rounded.SurroundSound else Icons.Rounded.AudioFile
                                )
                            }

                            // Add dedicated spatial audio info if detected
                            if (spatialInfo.isNotEmpty()) {
                                ModernFileInfoRow(
                                    label = "Spatial Audio",
                                    value = spatialInfo,
                                    icon = Icons.Rounded.SurroundSound
                                )
                            }
                        }

                        val subtitleStreams = item.mediaStreams?.filter { it.type == "Subtitle" }
                        if (!subtitleStreams.isNullOrEmpty()) {
                            val subtitleInfo = when {
                                subtitleStreams.size == 1 -> {
                                    val stream = subtitleStreams.first()
                                    val language = stream.language ?: stream.displayTitle ?: "Unknown"
                                    val codec = CodecUtils.getSubtitleCodecName(stream.codec)
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

                // Cast Section
                val actors = item.people?.filter { it.type == "Actor" }?.take(8) ?: emptyList()
                if (actors.isNotEmpty()) {
                    Text(
                        text = "Cast",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
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
            studios = null,
            mediaStreams = listOf(
                MediaStream(
                    type = "Video",
                    codec = "h264",
                    width = 1920,
                    height = 1080
                ),
                MediaStream(
                    type = "Audio",
                    codec = "aac",
                    channels = 2,
                    language = "eng"
                )
            )
        )

        DetailContent(
            item = mockItem,
            onBackPressed = {},
            onPlayClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DetailScreenLongRatingPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "The Dark Knight",
            overview = "When the menace known as the Joker wreaks havoc and chaos on the people of Gotham, Batman must accept one of the greatest psychological and physical tests of his ability to fight injustice.",
            productionYear = 2008,
            runTimeTicks = 9120000000L, // 2h 32m
            communityRating = 9.0f,
            officialRating = "Not Rated", // Long rating text
            genres = listOf("Action", "Crime", "Drama", "Thriller"),
            userData = null,
            people = null,
            studios = null,
            mediaStreams = listOf(
                MediaStream(
                    type = "Video",
                    codec = "h264",
                    width = 3840,
                    height = 2160,
                    videoRange = "HDR"
                ),
                MediaStream(
                    type = "Audio",
                    codec = "eac3",
                    channels = 6,
                    language = "eng",
                    title = "Dolby Digital+ 5.1"
                ),
                MediaStream(
                    type = "Subtitle",
                    codec = "subrip",
                    language = "eng"
                )
            )
        )

        DetailContent(
            item = mockItem,
            onBackPressed = {},
            onPlayClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DetailScreenNoGenresPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "Mystery Movie",
            overview = "A mysterious film with no genre information available.",
            productionYear = 2023,
            runTimeTicks = 5400000000L, // 1h 30m
            communityRating = 6.5f,
            officialRating = "PG-13",
            genres = null, // No genres
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

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DetailScreenManyGenresPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "Genre Overload",
            overview = "A movie that somehow fits into every possible genre category.",
            productionYear = 2024,
            runTimeTicks = 7200000000L, // 2h
            communityRating = 8.2f,
            officialRating = "R",
            genres = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller"),
            userData = null,
            people = null,
            studios = null,
            mediaStreams = listOf(
                MediaStream(
                    type = "Video",
                    codec = "hevc",
                    width = 1920,
                    height = 1080
                ),
                MediaStream(
                    type = "Audio",
                    codec = "truehd",
                    channels = 8,
                    language = "eng",
                    title = "Dolby TrueHD 7.1 Atmos"
                ),
                MediaStream(
                    type = "Audio",
                    codec = "ac3",
                    channels = 6,
                    language = "spa"
                ),
                MediaStream(
                    type = "Subtitle",
                    codec = "ass",
                    language = "eng"
                ),
                MediaStream(
                    type = "Subtitle",
                    codec = "subrip",
                    language = "spa"
                )
            )
        )

        DetailContent(
            item = mockItem,
            onBackPressed = {},
            onPlayClick = {}
        )
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