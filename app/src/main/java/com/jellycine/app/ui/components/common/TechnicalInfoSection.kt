package com.jellycine.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSourceInfo
import com.jellycine.data.model.MediaStream
import com.jellycine.detail.CodecUtils
import com.jellycine.app.ui.theme.JellyBlue

@Composable
fun TechnicalInfoSection(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {}
) {
    var selectedVideo by remember { mutableStateOf("1080p") }
    var selectedAudio by remember { mutableStateOf("English - Dolby...") }
    var selectedSubtitle by remember { mutableStateOf("Forced") }

    val videoOptions = remember(item) {
        item.mediaStreams?.filter { it.type == "Video" }?.mapNotNull { stream ->
            val resolution = if (stream.width != null && stream.height != null) {
                "${stream.width}x${stream.height}"
            } else "Unknown"
            resolution
        }?.distinct() ?: listOf("1080p")
    }

    val audioOptions = remember(item) {
        item.mediaStreams?.filter { it.type == "Audio" }?.mapNotNull { stream ->
            val language = stream.language?.let { 
                when(it) {
                    "eng" -> "English"
                    "spa" -> "Spanish"
                    "fre" -> "French"
                    else -> it.uppercase()
                }
            } ?: "Unknown"
            val codec = when(stream.codec?.lowercase()) {
                "eac3" -> "Dolby Digital+"
                "ac3" -> "Dolby Digital"
                "dts" -> "DTS"
                "truehd" -> "Dolby TrueHD"
                "aac" -> "AAC"
                else -> stream.codec?.uppercase() ?: "Unknown"
            }
            val channels = when(stream.channels) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> stream.channels?.toString() ?: ""
            }
            "$language - $codec ${if (channels.isNotEmpty()) channels else ""}".trim()
        }?.distinct() ?: listOf("English - Dolby Digital+ 5.1")
    }

    val subtitleOptions = remember(item) {
        val options = mutableListOf("Forced", "Off")
        item.mediaStreams?.filter { it.type == "Subtitle" }?.forEach { stream ->
            val language = stream.language?.let { 
                when(it) {
                    "eng" -> "English"
                    "spa" -> "Spanish"
                    "fre" -> "French"
                    else -> it.uppercase()
                }
            } ?: "Unknown"
            options.add(language)
        }
        options.distinct()
    }

    // Set initial selections
    LaunchedEffect(item) {
        if (videoOptions.isNotEmpty()) {
            selectedVideo = videoOptions.first()
        }
        if (audioOptions.isNotEmpty()) {
            selectedAudio = audioOptions.first().let { 
                if (it.length > 15) "${it.take(12)}..." else it 
            }
        }
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier.padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Modern Selection Categories
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ModernSelectionChip(
                    title = "Video",
                    selectedOption = selectedVideo,
                    options = videoOptions,
                    onOptionSelected = { selectedVideo = it },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                ModernSelectionChip(
                    title = "Audio", 
                    selectedOption = selectedAudio,
                    options = audioOptions.map { if (it.length > 15) "${it.take(12)}..." else it },
                    onOptionSelected = { selectedAudio = it },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                ModernSelectionChip(
                    title = "Subtitle",
                    selectedOption = selectedSubtitle,
                    options = subtitleOptions,
                    onOptionSelected = { selectedSubtitle = it },
                    modifier = Modifier.weight(1f)
                )
            }

            // Play and Download Buttons
            item.runTimeTicks?.let { ticks ->
                val userData = item.userData
                val playbackPositionTicks = userData?.playbackPositionTicks ?: 0L
                val isPartiallyWatched = playbackPositionTicks > 0L && playbackPositionTicks < ticks
                
                val displayText = if (isPartiallyWatched) {
                    val remainingTicks = ticks - playbackPositionTicks
                    "Resume • ${CodecUtils.formatRuntime(remainingTicks)}"
                } else {
                    CodecUtils.formatRuntime(ticks)
                }
                
                val watchProgress = if (ticks > 0) {
                    (playbackPositionTicks.toFloat() / ticks.toFloat()).coerceIn(0f, 1f)
                } else 0f
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassPlayButton(
                        text = displayText,
                        isResume = isPartiallyWatched,
                        progress = watchProgress,
                        onClick = onPlayClick,
                        modifier = Modifier.weight(1f)
                    )
                    
                    GlassDownloadButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernSelectionChip(
    title: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { 
                    if (options.size > 1) {
                        val currentIndex = options.indexOf(selectedOption)
                        val nextIndex = (currentIndex + 1) % options.size
                        onOptionSelected(options[nextIndex])
                    }
                },
            color = Color(0xFF2A2A2A),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedOption,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GlassPlayButton(
    text: String,
    isResume: Boolean = false,
    progress: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JellyBlue.copy(alpha = 0.8f),
                            JellyBlue.copy(alpha = 0.9f)
                        )
                    )
                )
        )
        
        // Subtle glass morphism overlay (before progress)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.05f)
                        )
                    )
                )
        )
        
        if (isResume && progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.9f))
            )
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = if (isResume) "Resume" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassDownloadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // JellyBlue glass background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JellyBlue.copy(alpha = 0.8f),
                            JellyBlue.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // Enhanced glass effect overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.1f)
                        )
                    )
                )
        )
        
        // Modern download icon
        Icon(
            imageVector = Icons.Rounded.FileDownload,
            contentDescription = "Download",
            tint = Color.Black,
            modifier = Modifier.size(24.dp)
        )
    }
}


@Preview(showBackground = true)
@Composable
fun TechnicalInfoSectionPreview() {
    // Create mock data for preview
    val mockItem = BaseItemDto(
        id = "preview-id",
        name = "Waterfalls",
        type = "Movie",
        mediaSources = listOf(
            MediaSourceInfo(
                id = "source-1",
                size = 7079215104L // 6.6 GB
            )
        ),
        mediaStreams = listOf(
            // Video stream
            MediaStream(
                index = 0,
                type = "Video",
                codec = "hevc",
                width = 3840,
                height = 1920,
                videoRange = "HDR",
                videoRangeType = "DOVI"
            ),
            // Audio stream 1
            MediaStream(
                index = 1,
                type = "Audio",
                codec = "eac3",
                channels = 6,
                language = "eng",
                isDefault = true,
                title = "DD+ 5.1"
            ),
            // Audio stream 2
            MediaStream(
                index = 2,
                type = "Audio",
                codec = "ac3",
                channels = 2,
                language = "spa",
                isDefault = false,
                title = "DD 2.0"
            )
        )
    )

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TechnicalInfoSection(
                item = mockItem,
                onDownloadClick = {}
            )
        }
    }
}