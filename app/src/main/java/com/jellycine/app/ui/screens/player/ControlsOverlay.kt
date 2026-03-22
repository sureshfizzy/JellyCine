package com.jellycine.app.ui.screens.player

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.jellycine.app.R
import com.jellycine.detail.SpatializationResult
import com.jellycine.player.core.PlayerConstants.PROGRESS_BAR_HEIGHT_DP
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ControlsOverlay(
    title: String,
    mediaLogoUrl: String? = null,
    seasonEpisodeLabel: String? = null,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onBackClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    spatializationResult: SpatializationResult? = null,
    isSpatialAudioEnabled: Boolean = false,
    isHdrEnabled: Boolean = false,
    onShowMediaInfo: () -> Unit = {},
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    currentStreamingQuality: String = "",
    showPlaybackSettingsButton: Boolean = true,
    onShowPlaybackSettings: () -> Unit = {},
    onShowAudioTrackSelection: () -> Unit = {},
    onShowSubtitleTrackSelection: () -> Unit = {},
    onCycleAspectRatio: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    seekBackwardSeconds: Int = 30,
    seekForwardSeconds: Int = 30,
    onScrubStateChange: (Boolean) -> Unit = {}
) {
    var scrubPreviewProgress by remember { mutableStateOf<Float?>(null) }
    val displayedPosition = scrubPreviewProgress
        ?.takeIf { duration > 0L }
        ?.let { (duration * it).toLong() }
        ?: currentPosition

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        // Top section - Title and action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isLocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!mediaLogoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = mediaLogoUrl,
                            contentDescription = "$title logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(24.dp)
                                .widthIn(min = 24.dp, max = 180.dp)
                                .padding(end = 12.dp)
                        )
                    } else {
                        if (title.isNotBlank()) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(end = 12.dp),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Media Information Button
                    IconButton(onClick = onShowMediaInfo) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Media Information",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onCycleAspectRatio) {
                        Icon(
                            imageVector = Icons.Outlined.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (showPlaybackSettingsButton) {
                        IconButton(onClick = onShowPlaybackSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Playback Settings ($currentStreamingQuality)",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    IconButton(onClick = onShowAudioTrackSelection) {
                        Icon(
                            imageVector = Icons.Outlined.Audiotrack,
                            contentDescription = "Audio Tracks",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onShowSubtitleTrackSelection) {
                        Icon(
                            imageVector = Icons.Outlined.Subtitles,
                            contentDescription = "Subtitles",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onToggleLock) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = "Lock",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Unlock",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Center controls
        if (!isLocked) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Fast rewind
            IconButton(
                onClick = onSeekBackward,
                modifier = Modifier.size(58.dp)
            ) {
                Icon(
                    imageVector = replayIcon(seekBackwardSeconds),
                    contentDescription = "Replay $seekBackwardSeconds seconds",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            // Play/Pause button
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(74.dp)
            ) {
                if (isPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(42.dp)
                                .background(Color.White, RoundedCornerShape(7.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(42.dp)
                                .background(Color.White, RoundedCornerShape(7.dp))
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Fast Forward
            IconButton(
                onClick = onSeekForward,
                modifier = Modifier.size(58.dp)
            ) {
                Icon(
                    imageVector = replayforwardIcon(seekForwardSeconds),
                    contentDescription = "Forward $seekForwardSeconds seconds",
                    tint = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            if (seekForwardSeconds != 5 &&
                                seekForwardSeconds != 10 &&
                                seekForwardSeconds != 30
                            ) {
                                scaleX = -1f
                            }
                        }
                )
            }
        }
        }

        // Bottom section - Time and seekbar
        if (!isLocked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.Bottom
            ) {
                if (!seasonEpisodeLabel.isNullOrBlank()) {
                    Text(
                        text = seasonEpisodeLabel,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatTime(displayedPosition)} - ${formatTime(if (duration > 0) duration else 0L)}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (isSpatialAudioEnabled || isHdrEnabled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isHdrEnabled) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.HdrOn,
                                        contentDescription = "HDR",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            if (isSpatialAudioEnabled) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.ic_spatial_audio),
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(14.dp)
                                    )

                                    Text(
                                        text = "Spatial Audio",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Seekbar (full width)
                SeekBar(
                    progress = if (duration > 0 && currentPosition >= 0) {
                        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f,
                    duration = duration,
                    onSeek = onSeek,
                    onScrubProgressChange = {
                        scrubPreviewProgress = it
                        onScrubStateChange(it != null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun replayIcon(seconds: Int): ImageVector {
    return when (seconds) {
        5 -> Icons.Filled.Replay5
        10 -> Icons.Filled.Replay10
        30 -> Icons.Filled.Replay30
        else -> Icons.Filled.Replay
    }
}

private fun replayforwardIcon(seconds: Int): ImageVector {
    return when (seconds) {
        5 -> Icons.Filled.Forward5
        10 -> Icons.Filled.Forward10
        30 -> Icons.Filled.Forward30
        else -> Icons.Filled.Replay
    }
}

@Composable
private fun SeekBar(
    progress: Float,
    duration: Long,
    onSeek: (Float) -> Unit,
    onScrubProgressChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    var scrubProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var dragActive by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val trackHeightFraction by animateFloatAsState(
        targetValue = if (dragActive) 0.95f else 0.55f,
        label = "seekTrackHeight"
    )
    val thumbRadiusFraction by animateFloatAsState(
        targetValue = if (dragActive) 0.52f else 0.36f,
        label = "seekThumbRadius"
    )
    val bubbleYOffsetPx = with(density) { (-42).dp.roundToPx() }

    LaunchedEffect(progress) {
        if (!dragActive) {
            scrubProgress = progress.coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .height(PROGRESS_BAR_HEIGHT_DP.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInteropFilter { event ->
                if (widthPx <= 0) return@pointerInteropFilter false

                val newProgress = (event.x / widthPx.toFloat()).coerceIn(0f, 1f)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragActive = true
                        scrubProgress = newProgress
                        onScrubProgressChange(scrubProgress)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        scrubProgress = newProgress
                        onScrubProgressChange(scrubProgress)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        scrubProgress = newProgress
                        dragActive = false
                        onSeek(scrubProgress)
                        onScrubProgressChange(null)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        dragActive = false
                        scrubProgress = progress.coerceIn(0f, 1f)
                        onScrubProgressChange(null)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val renderedProgress = scrubProgress.coerceIn(0f, 1f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_BAR_HEIGHT_DP.dp)
                .align(Alignment.BottomCenter)
        ) {
            val yOffset = size.height / 2
            val trackInset = 2.dp.toPx()
            val trackStart = Offset(trackInset, yOffset)
            val trackEnd = Offset(size.width - trackInset, yOffset)
            val trackHeight = size.height * trackHeightFraction

            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = trackStart,
                end = trackEnd,
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            if (renderedProgress > 0f) {
                val progressX = trackStart.x + (trackEnd.x - trackStart.x) * renderedProgress
                drawLine(
                    color = Color.White.copy(alpha = 0.95f),
                    start = trackStart,
                    end = Offset(progressX, yOffset),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = Color.White,
                    radius = size.height * thumbRadiusFraction,
                    center = Offset(progressX, yOffset)
                )
            }
        }

        AnimatedVisibility(
            visible = dragActive && duration > 0L && widthPx > 0,
            modifier = Modifier
                .align(Alignment.TopStart)
                .wrapContentSize(unbounded = true)
                .zIndex(1f)
                .offset {
                    val thumbCenterX = (widthPx * renderedProgress).roundToInt()
                    IntOffset(thumbCenterX - 32.dp.roundToPx(), bubbleYOffsetPx)
                },
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 4.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Text(
                    text = formatTime((duration * renderedProgress).toLong()),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Preview(
    name = "Controls - Playing",
    showBackground = true,
    widthDp = 800,
    heightDp = 450,
    backgroundColor = 0xFF000000
)
@Composable
fun ControlsOverlayPreviewPlaying() {
    ControlsOverlay(
        title = "The Matrix Reloaded",
        isPlaying = true,
        currentPosition = 1800000L,
        duration = 8280000L,
        onBackClick = { },
        onPlayPause = { },
        onSeek = { },
        spatializationResult = SpatializationResult(
            canSpatialize = true,
            reason = "Content and device support spatial audio",
            spatialFormat = "Dolby Atmos"
        ),
        isSpatialAudioEnabled = true,
        onShowMediaInfo = { },
        isLocked = false,
        onToggleLock = { },
        onShowAudioTrackSelection = { },
        onShowSubtitleTrackSelection = { },
        onCycleAspectRatio = { },
        onSeekBackward = { },
        onSeekForward = { }
    )
}

@Preview(
    name = "Controls - Paused",
    showBackground = true,
    widthDp = 800,
    heightDp = 450,
    backgroundColor = 0xFF000000
)
@Composable
fun ControlsOverlayPreviewPaused() {
    ControlsOverlay(
        title = "Inception",
        isPlaying = false,
        currentPosition = 3600000L,
        duration = 8880000L,
        onBackClick = { },
        onPlayPause = { },
        onSeek = { },
        spatializationResult = SpatializationResult(
            canSpatialize = true,
            reason = "Content and device support spatial audio",
            spatialFormat = "DTS:X"
        ),
        isSpatialAudioEnabled = false,
        onShowMediaInfo = { },
        isLocked = false,
        onToggleLock = { },
        onShowAudioTrackSelection = { },
        onShowSubtitleTrackSelection = { },
        onCycleAspectRatio = { },
        onSeekBackward = { },
        onSeekForward = { }
    )
}

@Preview(
    name = "Seekbar",
    showBackground = true,
    widthDp = 400,
    heightDp = 50,
    backgroundColor = 0xFF000000
)
@Composable
fun SeekBarPreview() {
    SeekBar(
        progress = 0.35f,
        duration = 7200000L,
        onSeek = { },
        onScrubProgressChange = { },
        modifier = Modifier.padding(16.dp)
    )
}
