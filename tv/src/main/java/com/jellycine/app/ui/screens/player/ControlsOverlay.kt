package com.jellycine.app.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jellycine.shared.R
import com.jellycine.detail.SpatializationResult
import com.jellycine.player.core.ChapterMarker
import java.util.Locale
import kotlin.math.abs

private const val TV_SEEK_BAR_HEIGHT_DP = 14
private const val TV_SEEK_STEP_FRACTION = 0.01f

@Composable
fun ControlsOverlay(
    title: String,
    mediaLogoUrl: String? = null,
    seasonEpisodeLabel: String? = null,
    chapterMarkers: List<ChapterMarker> = emptyList(),
    isPlaying: Boolean,
    currentPosition: Long,
    bufferedPosition: Long = 0L,
    duration: Long,
    onBackClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    spatializationResult: SpatializationResult? = null,
    isSpatialAudioEnabled: Boolean = false,
    isHdrEnabled: Boolean = false,
    onShowMediaInfo: () -> Unit = {},
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
    canPlayPreviousEpisode: Boolean = false,
    canPlayNextEpisode: Boolean = false,
    onPlayPreviousEpisode: () -> Unit = {},
    onPlayNextEpisode: () -> Unit = {},
    onScrubStateChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val playPauseFocus = remember { FocusRequester() }

    val overlayGradient = remember {
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.65f),
                Color.Transparent,
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.80f)
            )
        )
    }
    val logoRequest = remember(context, mediaLogoUrl) {
        mediaLogoUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(coil3.size.Size(480, 140))
                .build()
        }
    }

    LaunchedEffect(Unit) {
        playPauseFocus.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(overlayGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 32.dp)
                .align(Alignment.TopStart)
        ) {
            if (logoRequest != null) {
                AsyncImage(
                    model = logoRequest,
                    contentDescription = "$title logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(28.dp)
                        .widthIn(min = 28.dp, max = 220.dp)
                )
            } else if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!seasonEpisodeLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = seasonEpisodeLabel,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canPlayPreviousEpisode) {
                TvIconButton(
                    onClick = onPlayPreviousEpisode,
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous_episode),
                    size = 46.dp,
                    iconSize = 32.dp
                )
            }

            TvIconButton(
                onClick = onSeekBackward,
                icon = replayIcon(seekBackwardSeconds),
                contentDescription = "Rewind $seekBackwardSeconds seconds",
                size = 48.dp,
                iconSize = 34.dp
            )

            TvPlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPause,
                focusRequester = playPauseFocus
            )

            TvIconButton(
                onClick = onSeekForward,
                icon = replayforwardIcon(seekForwardSeconds),
                contentDescription = "Forward $seekForwardSeconds seconds",
                size = 48.dp,
                iconSize = 34.dp,
                iconModifier = Modifier.graphicsLayer {
                    if (seekForwardSeconds != 5 &&
                        seekForwardSeconds != 10 &&
                        seekForwardSeconds != 30
                    ) {
                        scaleX = -1f
                    }
                }
            )

            if (canPlayNextEpisode) {
                TvIconButton(
                    onClick = onPlayNextEpisode,
                    icon = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.player_next_episode),
                    size = 46.dp,
                    iconSize = 32.dp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .padding(bottom = 36.dp)
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isHdrEnabled) {
                    Surface(
                        color = Color(0xFFFFB300).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HdrOn,
                            contentDescription = "HDR",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.padding(4.dp).size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (isSpatialAudioEnabled) {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_spatial_audio),
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Spatial",
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                TvSmallActionButton(
                    onClick = onShowMediaInfo,
                    icon = Icons.Outlined.Info,
                    contentDescription = "Media Info",
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.width(6.dp))
                TvSmallActionButton(
                    onClick = onCycleAspectRatio,
                    icon = Icons.Outlined.AspectRatio,
                    contentDescription = "Aspect Ratio"
                )
                if (showPlaybackSettingsButton) {
                    Spacer(modifier = Modifier.width(6.dp))
                    TvSmallActionButton(
                        onClick = onShowPlaybackSettings,
                        icon = Icons.Outlined.Settings,
                        contentDescription = "Playback Settings"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                TvSmallActionButton(
                    onClick = onShowAudioTrackSelection,
                    icon = Icons.Outlined.Audiotrack,
                    contentDescription = "Audio Tracks"
                )
                Spacer(modifier = Modifier.width(6.dp))
                TvSmallActionButton(
                    onClick = onShowSubtitleTrackSelection,
                    icon = Icons.Outlined.Subtitles,
                    contentDescription = "Subtitles"
                )
            }

            TvSeekBar(
                progress = if (duration > 0 && currentPosition >= 0) {
                    (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else 0f,
                bufferedProgress = if (duration > 0 && bufferedPosition >= 0) {
                    (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else 0f,
                duration = duration,
                chapterMarkers = chapterMarkers,
                onSeek = onSeek,
                onScrubStateChange = onScrubStateChange
            )

            Text(
                text = "${formatTime(currentPosition)} / ${formatTime(if (duration > 0) duration else 0L)}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TvPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(64.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.10f),
                shape = CircleShape
            )
            .then(
                if (isFocused) Modifier.border(2.5.dp, Color.White, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .height(28.dp)
                        .background(Color.White, RoundedCornerShape(3.dp))
                )
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .height(28.dp)
                        .background(Color.White, RoundedCornerShape(3.dp))
                )
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun TvIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 56.dp,
    iconSize: Dp = 36.dp,
    iconModifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                shape = CircleShape
            )
            .then(
                if (isFocused) Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = iconModifier.size(iconSize)
        )
    }
}

@Composable
private fun TvSmallActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = Color.White.copy(alpha = 0.85f)
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(36.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                shape = CircleShape
            )
            .then(
                if (isFocused) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.White else tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TvSeekBar(
    progress: Float,
    bufferedProgress: Float = 0f,
    duration: Long,
    chapterMarkers: List<ChapterMarker>,
    onSeek: (Float) -> Unit,
    onScrubStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (!isScrubbing) {
            scrubProgress = progress.coerceIn(0f, 1f)
        }
    }

    val trackHeight by animateFloatAsState(
        targetValue = if (isFocused) 0.9f else 0.5f,
        label = "tvSeekTrackHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TV_SEEK_BAR_HEIGHT_DP.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (!it.isFocused && isScrubbing) {
                    isScrubbing = false
                    scrubProgress = progress.coerceIn(0f, 1f)
                    onScrubStateChange(false)
                }
            }
            .onPreviewKeyEvent { event ->
                if (!isFocused) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.DirectionLeft -> {
                        if (!isScrubbing) {
                            isScrubbing = true
                            scrubProgress = progress.coerceIn(0f, 1f)
                            onScrubStateChange(true)
                        }
                        scrubProgress = (scrubProgress - TV_SEEK_STEP_FRACTION).coerceIn(0f, 1f)
                        true
                    }
                    Key.DirectionRight -> {
                        if (!isScrubbing) {
                            isScrubbing = true
                            scrubProgress = progress.coerceIn(0f, 1f)
                            onScrubStateChange(true)
                        }
                        scrubProgress = (scrubProgress + TV_SEEK_STEP_FRACTION).coerceIn(0f, 1f)
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (isScrubbing) {
                            isScrubbing = false
                            onSeek(scrubProgress)
                            onScrubStateChange(false)
                        }
                        true
                    }
                    Key.Back -> {
                        if (isScrubbing) {
                            isScrubbing = false
                            scrubProgress = progress.coerceIn(0f, 1f)
                            onScrubStateChange(false)
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        val renderedProgress = scrubProgress.coerceIn(0f, 1f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TV_SEEK_BAR_HEIGHT_DP.dp)
                .align(Alignment.Center)
        ) {
            val yOffset = size.height / 2
            val trackInset = 4.dp.toPx()
            val trackStart = Offset(trackInset, yOffset)
            val trackEnd = Offset(size.width - trackInset, yOffset)
            val currentTrackHeight = size.height * trackHeight
            val markerSpacingPx = 8.dp.toPx()
            val markerStrokeWidth = 2.dp.toPx()
            val markerVerticalInset = 2.dp.toPx()

            // Background full track
            drawLine(
                color = if (isFocused) Color.White.copy(alpha = 0.35f)
                else Color.White.copy(alpha = 0.20f),
                start = trackStart,
                end = trackEnd,
                strokeWidth = currentTrackHeight,
                cap = StrokeCap.Round
            )

            // Buffered progress track
            val clampedBufferedProgress = bufferedProgress.coerceIn(0f, 1f)
            if (clampedBufferedProgress > 0f) {
                val bufferedX = trackStart.x + (trackEnd.x - trackStart.x) * clampedBufferedProgress
                drawLine(
                    color = if (isFocused) Color.White.copy(alpha = 0.60f)
                    else Color.White.copy(alpha = 0.45f),
                    start = trackStart,
                    end = Offset(bufferedX, yOffset),
                    strokeWidth = currentTrackHeight,
                    cap = StrokeCap.Round
                )
            }

            // Played progress track
            if (renderedProgress > 0f) {
                val progressX = trackStart.x + (trackEnd.x - trackStart.x) * renderedProgress
                drawLine(
                    color = Color.White,
                    start = trackStart,
                    end = Offset(progressX, yOffset),
                    strokeWidth = currentTrackHeight,
                    cap = StrokeCap.Round
                )

                if (isFocused) {
                    drawCircle(
                        color = Color.White,
                        radius = size.height * 0.65f,
                        center = Offset(progressX, yOffset)
                    )
                }
            }

            if (duration > 0L && chapterMarkers.isNotEmpty()) {
                var lastMarkerX = Float.NEGATIVE_INFINITY
                chapterMarkers.forEach { marker ->
                    val markerProgress = (marker.positionMs.toFloat() / duration.toFloat())
                        .coerceIn(0f, 1f)
                    if (markerProgress <= 0f || markerProgress >= 1f) return@forEach

                    val markerX = trackStart.x + (trackEnd.x - trackStart.x) * markerProgress
                    if (abs(markerX - lastMarkerX) < markerSpacingPx) return@forEach

                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(markerX, yOffset - currentTrackHeight / 2f + markerVerticalInset),
                        end = Offset(markerX, yOffset + currentTrackHeight / 2f - markerVerticalInset),
                        strokeWidth = markerStrokeWidth,
                        cap = StrokeCap.Round
                    )
                    lastMarkerX = markerX
                }
            }
        }

        AnimatedVisibility(
            visible = isScrubbing && duration > 0L,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.90f),
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Text(
                    text = formatTime((duration * renderedProgress).toLong()),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
    name = "TV Controls - Playing",
    showBackground = true,
    widthDp = 960,
    heightDp = 540,
    backgroundColor = 0xFF000000
)
@Composable
fun ControlsOverlayPreviewPlaying() {
    ControlsOverlay(
        title = "The Matrix Reloaded",
        chapterMarkers = listOf(
            ChapterMarker(positionMs = 540000L, label = "Chapter 1"),
            ChapterMarker(positionMs = 1740000L, label = "Chapter 2"),
            ChapterMarker(positionMs = 3120000L, label = "Chapter 3")
        ),
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
        onShowAudioTrackSelection = { },
        onShowSubtitleTrackSelection = { },
        onCycleAspectRatio = { },
        onSeekBackward = { },
        onSeekForward = { },
        canPlayPreviousEpisode = true,
        canPlayNextEpisode = true
    )
}

@Preview(
    name = "TV Controls - Paused",
    showBackground = true,
    widthDp = 960,
    heightDp = 540,
    backgroundColor = 0xFF000000
)
@Composable
fun ControlsOverlayPreviewPaused() {
    ControlsOverlay(
        title = "Inception",
        chapterMarkers = listOf(
            ChapterMarker(positionMs = 600000L, label = "Dream 1"),
            ChapterMarker(positionMs = 2520000L, label = "Dream 2"),
            ChapterMarker(positionMs = 5100000L, label = "Dream 3")
        ),
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
        onShowAudioTrackSelection = { },
        onShowSubtitleTrackSelection = { },
        onCycleAspectRatio = { },
        onSeekBackward = { },
        onSeekForward = { }
    )
}
