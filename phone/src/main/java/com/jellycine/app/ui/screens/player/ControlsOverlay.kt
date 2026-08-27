package com.jellycine.app.ui.screens.player

import android.content.res.Configuration
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jellycine.shared.R
import com.jellycine.detail.SpatializationResult
import com.jellycine.player.core.ChapterMarker
import com.jellycine.player.core.PlayerConstants.PROGRESS_BAR_HEIGHT_DP
import com.jellycine.player.core.PlayerConstants.PROGRESS_BAR_HIT_HEIGHT_DP
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ControlsOverlay(
    title: String,
    mediaLogoUrl: String? = null,
    seasonEpisodeLabel: String? = null,
    chapterMarkers: List<ChapterMarker> = emptyList(),
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
    hdrFormat: String = "",
    onShowMediaInfo: () -> Unit = {},
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    currentStreamingQuality: String = "",
    showPlaybackSettingsButton: Boolean = true,
    onShowPlaybackSettings: () -> Unit = {},
    onShowAudioTrackSelection: () -> Unit = {},
    onShowSubtitleTrackSelection: () -> Unit = {},
    onCycleAspectRatio: () -> Unit = {},
    onToggleOrientation: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    seekBackwardSeconds: Int = 30,
    seekForwardSeconds: Int = 30,
    canPlayPreviousEpisode: Boolean = false,
    canPlayNextEpisode: Boolean = false,
    onPlayPreviousEpisode: () -> Unit = {},
    onPlayNextEpisode: () -> Unit = {},
    onScrubStateChange: (Boolean) -> Unit = {},
    onLiveSeek: (Float) -> Unit = {},
    onEnterPip: () -> Unit = {},
    onToggleHardwareDecoding: () -> Unit = {},
    onShowChapters: () -> Unit = {},
    onNudgeSpeed: (Float) -> Unit = {},
    playbackSpeed: Float = 1f,
    hardwareDecodingEnabled: Boolean = true,
    onUserInteraction: () -> Unit = {},
    onBackgroundClick: () -> Unit = {},
    onScreenshot: () -> Unit = {}
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val context = LocalContext.current
    var scrubPreviewProgress by remember { mutableStateOf<Float?>(null) }
    val overlayGradient = remember {
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.7f),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.8f)
            )
        )
    }
    val logoRequest = remember(context, mediaLogoUrl) {
        mediaLogoUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(coil3.size.Size(320, 96))
                .build()
        }
    }
    val displayedPosition = scrubPreviewProgress
        ?.takeIf { duration > 0L }
        ?.let { (duration * it).toLong() }
        ?: currentPosition
    val hdrBadgeLabel = remember(isHdrEnabled, hdrFormat) {
        if (isHdrEnabled) osdHdrLabel(hdrFormat) else ""
    }
    val useHdr10PlusBadge = hdrBadgeLabel == "HDR10+"
    val useHdr10Badge = hdrBadgeLabel == "HDR10"
    val useHdrBadge = hdrBadgeLabel == "HDR"
    val useDolbyVisionBadge = hdrBadgeLabel == "DV"
    val hdrChipShape = RoundedCornerShape(999.dp)
    val hdrChipColor = if (useDolbyVisionBadge) Color.White else Color(0xFFE8E8E8)
    val hdrChipHorizontalPadding = if (useDolbyVisionBadge) 5.dp else 8.dp
    val hdrChipVerticalPadding = if (useDolbyVisionBadge) 2.dp else 4.dp

        PortraitPlayerOverlay(
            title = title,
            seasonEpisodeLabel = seasonEpisodeLabel,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            displayedPosition = displayedPosition,
            chapterMarkers = chapterMarkers,
            isLocked = isLocked,
            showPlaybackSettingsButton = showPlaybackSettingsButton,
            onBackClick = onBackClick,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            onLiveSeek = onLiveSeek,
            onScrubProgressChange = {
                scrubPreviewProgress = it
                onScrubStateChange(it != null)
            },
            onToggleLock = onToggleLock,
            onShowMediaInfo = onShowMediaInfo,
            onShowPlaybackSettings = onShowPlaybackSettings,
            onShowAudioTrackSelection = onShowAudioTrackSelection,
            onShowSubtitleTrackSelection = onShowSubtitleTrackSelection,
            onCycleAspectRatio = onCycleAspectRatio,
            onToggleOrientation = onToggleOrientation,
            onTitleClick = onTitleClick,
            onSeekBackward = onSeekBackward,
            onSeekForward = onSeekForward,
            seekBackwardSeconds = seekBackwardSeconds,
            seekForwardSeconds = seekForwardSeconds,
            onEnterPip = onEnterPip,
            onToggleHardwareDecoding = onToggleHardwareDecoding,
            onShowChapters = onShowChapters,
            onNudgeSpeed = onNudgeSpeed,
            playbackSpeed = playbackSpeed,
            hardwareDecodingEnabled = hardwareDecodingEnabled,
            canPlayPreviousEpisode = canPlayPreviousEpisode,
            canPlayNextEpisode = canPlayNextEpisode,
            onPlayPreviousEpisode = onPlayPreviousEpisode,
            onPlayNextEpisode = onPlayNextEpisode,
            onUserInteraction = onUserInteraction,
            onBackgroundClick = onBackgroundClick,
            onScreenshot = onScreenshot,
            isScrubbing = scrubPreviewProgress != null,
            landscape = !isPortrait,
            modifier = modifier
        )
}

@Composable
private fun PortraitPlayerOverlay(
    title: String,
    seasonEpisodeLabel: String?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    displayedPosition: Long,
    chapterMarkers: List<ChapterMarker>,
    isLocked: Boolean,
    showPlaybackSettingsButton: Boolean,
    onBackClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onLiveSeek: (Float) -> Unit,
    onScrubProgressChange: (Float?) -> Unit,
    onToggleLock: () -> Unit,
    onShowMediaInfo: () -> Unit,
    onShowPlaybackSettings: () -> Unit,
    onShowAudioTrackSelection: () -> Unit,
    onShowSubtitleTrackSelection: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onToggleOrientation: () -> Unit,
    onTitleClick: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    seekBackwardSeconds: Int = 30,
    seekForwardSeconds: Int = 30,
    onEnterPip: () -> Unit = {},
    onToggleHardwareDecoding: () -> Unit = {},
    onShowChapters: () -> Unit = {},
    onNudgeSpeed: (Float) -> Unit = {},
    playbackSpeed: Float = 1f,
    hardwareDecodingEnabled: Boolean = true,
    canPlayPreviousEpisode: Boolean = false,
    canPlayNextEpisode: Boolean = false,
    onPlayPreviousEpisode: () -> Unit = {},
    onPlayNextEpisode: () -> Unit = {},
    onUserInteraction: () -> Unit = {},
    onBackgroundClick: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    isScrubbing: Boolean = false,
    landscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showOverflow by remember { mutableStateOf(false) }
    val progress = if (duration > 0 && currentPosition >= 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val chromeInteraction = remember { MutableInteractionSource() }
    val headline = seasonEpisodeLabel?.takeIf { it.isNotBlank() } ?: title
    val iconTint = Color.White
    val disabledTint = Color.White.copy(alpha = 0.35f)
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val fittedVideoWidth = screenHeight * 16f / 9f
    val landscapeSideGutter = if (landscape) {
        ((screenWidth - fittedVideoWidth) / 2f).coerceAtLeast(0.dp)
    } else {
        0.dp
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (!isLocked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(
                            if (landscape) {
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                            } else {
                                WindowInsetsSides.Top
                            }
                        )
                    )
                    .padding(
                        start = PlayerChromeInset,
                        end = if (landscape) {
                            landscapeSideGutter + PlayerChromeInset
                        } else {
                            PlayerChromeInset
                        },
                        top = PlayerChromeTopGap
                    )
                    .align(Alignment.TopCenter)
                    .clickable(
                        interactionSource = chromeInteraction,
                        indication = null,
                        onClick = onUserInteraction
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (landscape) {
                    Arrangement.SpaceBetween
                } else {
                    Arrangement.spacedBy(10.dp)
                }
            ) {
                PlayerGlassIconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back_button),
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                PlayerGlass(
                    modifier = Modifier.height(PlayerGlassButtonSize),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerChromeIconButton(
                            onClick = onToggleHardwareDecoding,
                            modifier = Modifier
                                .height(40.dp)
                                .widthIn(min = 48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.player_hw_plus),
                                color = if (hardwareDecodingEnabled) {
                                    Color(0xFF4CAF50)
                                } else {
                                    Color.White.copy(alpha = 0.55f)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        PlayerChromeIconButton(onClick = onEnterPip) {
                            Icon(
                                imageVector = Icons.Outlined.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.player_pip),
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        PlayerChromeIconButton(onClick = onCycleAspectRatio) {
                            Icon(
                                imageVector = Icons.Outlined.AspectRatio,
                                contentDescription = stringResource(R.string.player_settings_start_maximized),
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (!landscape) {
                            PlayerChromeIconButton(onClick = onScreenshot) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = stringResource(R.string.player_screenshot),
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        PlayerChromeIconButton(onClick = onShowChapters) {
                            Icon(
                                imageVector = Icons.Outlined.Bookmarks,
                                contentDescription = stringResource(R.string.player_chapters),
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        PlayerChromeIconButton(onClick = onToggleOrientation) {
                            Icon(
                                imageVector = Icons.Outlined.ScreenRotation,
                                contentDescription = stringResource(R.string.player_cd_toggle_orientation),
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Box {
                            PlayerChromeIconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = stringResource(R.string.player_more),
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xE61C1C1E),
                                tonalElevation = 0.dp,
                                shadowElevation = 8.dp
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.player_media_info),
                                            color = Color.White
                                        )
                                    },
                                    onClick = {
                                        showOverflow = false
                                        onShowMediaInfo()
                                    }
                                )
                                if (showPlaybackSettingsButton) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.player_settings_streaming_quality),
                                                color = Color.White
                                            )
                                        },
                                        onClick = {
                                            showOverflow = false
                                            onShowPlaybackSettings()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            OverlayPlaybackStats(
                modifier = Modifier
                    .align(if (landscape) Alignment.TopEnd else Alignment.TopStart)
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(
                            WindowInsetsSides.Top + if (landscape) {
                                WindowInsetsSides.End
                            } else {
                                WindowInsetsSides.Start
                            }
                        )
                    )
                    .padding(
                        top = PlayerGlassButtonSize + PlayerChromeTopGap + 6.dp,
                        start = if (landscape) 0.dp else PlayerChromeInset + PlayerGlassButtonSize + 10.dp,
                        end = if (landscape) landscapeSideGutter + PlayerChromeInset else 0.dp
                    )
            )
        }

        if (isScrubbing && duration > 0L) {
            SeekTimeHud(
                positionMs = displayedPosition,
                durationMs = duration,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                    .padding(top = 64.dp)
            )
        }

        if (isLocked) {
            PlayerGlassIconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .then(
                        if (landscape) {
                            Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = PlayerChromeInset)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.player_unlock),
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (!isLocked) {
            if (landscape) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                        .padding(start = PlayerChromeInset),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlayerGlassIconButton(onClick = onScreenshot) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = stringResource(R.string.player_screenshot),
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    PlayerGlassIconButton(onClick = onToggleLock) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = stringResource(R.string.player_lock),
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                OverlayTransportButtons(
                    isPlaying = isPlaying,
                    seekBackwardSeconds = seekBackwardSeconds,
                    seekForwardSeconds = seekForwardSeconds,
                    iconTint = iconTint,
                    onSeekBackward = onSeekBackward,
                    onPlayPause = onPlayPause,
                    onSeekForward = onSeekForward,
                    modifier = Modifier.align(Alignment.Center)
                )
                OverlaySpeedControl(
                    speed = playbackSpeed,
                    onNudgeSpeed = onNudgeSpeed,
                    vertical = true,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                        .padding(end = PlayerChromeInset)
                )
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = PlayerChromeInset)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onUserInteraction
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PlayerGlassIconButton(onClick = onToggleLock) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = stringResource(R.string.player_lock),
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    OverlayTransportButtons(
                        isPlaying = isPlaying,
                        seekBackwardSeconds = seekBackwardSeconds,
                        seekForwardSeconds = seekForwardSeconds,
                        iconTint = iconTint,
                        onSeekBackward = onSeekBackward,
                        onPlayPause = onPlayPause,
                        onSeekForward = onSeekForward
                    )
                    OverlaySpeedControl(
                        speed = playbackSpeed,
                        onNudgeSpeed = onNudgeSpeed,
                        vertical = true
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .then(
                        if (landscape) {
                            Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                                )
                            )
                        } else {
                            Modifier.navigationBarsPadding()
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onUserInteraction
                    )
                    .padding(
                        // 横屏底部控制区按整块屏幕占位，不能跟随 16:9 视频画幅向内收缩。
                        start = PlayerChromeInset,
                        end = PlayerChromeInset,
                        bottom = PlayerChromeBottomGap
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayerGlassIconButton(
                        onClick = onPlayPreviousEpisode,
                        enabled = canPlayPreviousEpisode,
                        size = 40.dp
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.player_previous_episode),
                            tint = if (canPlayPreviousEpisode) iconTint else disabledTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    PlayerGlassIconButton(
                        onClick = onPlayNextEpisode,
                        enabled = canPlayNextEpisode,
                        size = 40.dp
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.player_next_episode),
                            tint = if (canPlayNextEpisode) iconTint else disabledTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = headline,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onTitleClick)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (!landscape) {
                    PlayerGlass(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PlayerGlassButtonSize),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        SeekBar(
                            progress = progress,
                            duration = duration,
                            chapterMarkers = chapterMarkers,
                            onSeek = onSeek,
                            onLiveSeek = onLiveSeek,
                            onScrubProgressChange = onScrubProgressChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                PlayerGlass(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PlayerGlassButtonSize),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${formatTime(displayedPosition)}  ·  ${formatTime(if (duration > 0) duration else 0L)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        if (landscape) {
                            SeekBar(
                                progress = progress,
                                duration = duration,
                                chapterMarkers = chapterMarkers,
                                onSeek = onSeek,
                                onLiveSeek = onLiveSeek,
                                onScrubProgressChange = onScrubProgressChange,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        if (showPlaybackSettingsButton) {
                            PlayerChromeIconButton(onClick = onShowPlaybackSettings) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = stringResource(R.string.player_settings_streaming_quality),
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        PlayerChromeIconButton(onClick = onShowAudioTrackSelection) {
                            Icon(
                                imageVector = Icons.Outlined.MusicNote,
                                contentDescription = stringResource(R.string.player_dialog_audio_title),
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        PlayerChromeIconButton(onClick = onShowSubtitleTrackSelection) {
                            Icon(
                                imageVector = Icons.Outlined.ClosedCaption,
                                contentDescription = stringResource(R.string.player_dialog_subtitles_title),
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
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
    chapterMarkers: List<ChapterMarker>,
    onSeek: (Float) -> Unit,
    onLiveSeek: (Float) -> Unit = {},
    onScrubProgressChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    var scrubProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var dragActive by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    var lastLiveSeekAt by remember { mutableLongStateOf(0L) }
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
            .height(PROGRESS_BAR_HIT_HEIGHT_DP.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInteropFilter { event ->
                if (widthPx <= 0) return@pointerInteropFilter false

                val newProgress = (event.x / widthPx.toFloat()).coerceIn(0f, 1f)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragActive = true
                        scrubProgress = newProgress
                        lastLiveSeekAt = SystemClock.uptimeMillis()
                        onScrubProgressChange(scrubProgress)
                        onLiveSeek(scrubProgress)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        scrubProgress = newProgress
                        onScrubProgressChange(scrubProgress)
                        val now = SystemClock.uptimeMillis()
                        if (now - lastLiveSeekAt >= 80L) {
                            lastLiveSeekAt = now
                            onLiveSeek(scrubProgress)
                        }
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
            },
        contentAlignment = Alignment.Center
    ) {
        val renderedProgress = scrubProgress.coerceIn(0f, 1f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_BAR_HEIGHT_DP.dp)
                .align(Alignment.Center)
        ) {
            val yOffset = size.height / 2
            val trackInset = 2.dp.toPx()
            val trackStart = Offset(trackInset, yOffset)
            val trackEnd = Offset(size.width - trackInset, yOffset)
            val trackHeight = size.height * trackHeightFraction
            val markerSpacingPx = 6.dp.toPx()
            val markerStrokeWidth = 1.5.dp.toPx()
            val markerVerticalInset = 1.dp.toPx()
            var progressX: Float? = null

            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = trackStart,
                end = trackEnd,
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            if (renderedProgress > 0f) {
                progressX = trackStart.x + (trackEnd.x - trackStart.x) * renderedProgress
                drawLine(
                    color = Color.White.copy(alpha = 0.95f),
                    start = trackStart,
                    end = Offset(progressX, yOffset),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
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
                        color = Color.White.copy(alpha = 0.95f),
                        start = Offset(markerX, yOffset - trackHeight / 2f + markerVerticalInset),
                        end = Offset(markerX, yOffset + trackHeight / 2f - markerVerticalInset),
                        strokeWidth = markerStrokeWidth,
                        cap = StrokeCap.Round
                    )
                    lastMarkerX = markerX
                }
            }

            progressX?.let { thumbX ->
                drawCircle(
                    color = Color.White,
                    radius = size.height * thumbRadiusFraction,
                    center = Offset(thumbX, yOffset)
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

private fun formatPlaybackSpeed(speed: Float): String {
    val hundredths = (speed * 100f).toInt()
    return if (hundredths % 10 == 0) {
        String.format(Locale.US, "%.1fx", speed)
    } else {
        String.format(Locale.US, "%.2fx", speed)
    }
}

@Composable
private fun HardwarePlusButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(
            text = stringResource(R.string.player_hw_plus),
            color = if (enabled) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}



@Composable
private fun OverlayPlaybackStats(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val batteryPercent = remember {
        val manager = context.getSystemService(android.content.Context.BATTERY_SERVICE)
            as? android.os.BatteryManager
        manager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }
    var clock by remember {
        mutableStateOf(
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            kotlinx.coroutines.delay(1_000)
        }
    }
    val batteryText = if (batteryPercent >= 0) "${batteryPercent}%" else "--%"
    Text(
        text = "$batteryText  $clock",
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

@Composable
private fun OverlayTransportButtons(
    isPlaying: Boolean,
    seekBackwardSeconds: Int,
    seekForwardSeconds: Int,
    iconTint: Color,
    onSeekBackward: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PlayerGlassIconButton(
            onClick = onSeekBackward,
            size = PlayerGlassSeekSize
        ) {
            Icon(
                imageVector = replayIcon(seekBackwardSeconds),
                contentDescription = stringResource(
                    R.string.player_cd_seek_backward,
                    seekBackwardSeconds
                ),
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
        PlayerGlassIconButton(
            onClick = onPlayPause,
            size = PlayerGlassPlaySize
        ) {
            Icon(
                imageVector = if (isPlaying) {
                    Icons.Filled.Pause
                } else {
                    Icons.Rounded.PlayArrow
                },
                contentDescription = if (isPlaying) {
                    stringResource(R.string.pause)
                } else {
                    stringResource(R.string.play)
                },
                tint = iconTint,
                modifier = Modifier.size(if (isPlaying) 32.dp else 38.dp)
            )
        }
        PlayerGlassIconButton(
            onClick = onSeekForward,
            size = PlayerGlassSeekSize
        ) {
            Icon(
                imageVector = replayforwardIcon(seekForwardSeconds),
                contentDescription = stringResource(
                    R.string.player_cd_seek_forward,
                    seekForwardSeconds
                ),
                tint = iconTint,
                modifier = Modifier
                    .size(26.dp)
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

@Composable
private fun OverlaySpeedControl(
    speed: Float,
    onNudgeSpeed: (Float) -> Unit,
    vertical: Boolean = false,
    modifier: Modifier = Modifier
) {
    val decreaseButton = @Composable {
        PlayerChromeIconButton(
            onClick = { onNudgeSpeed(-0.25f) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.player_speed_decrease),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    val increaseButton = @Composable {
        PlayerChromeIconButton(
            onClick = { onNudgeSpeed(0.25f) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.player_speed_increase),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    val speedLabel = @Composable {
        Text(
            text = formatPlaybackSpeed(speed),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    PlayerGlass(
        modifier = modifier.then(if (vertical) Modifier.width(48.dp) else Modifier),
        shape = RoundedCornerShape(999.dp)
    ) {
        if (vertical) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                decreaseButton()
                speedLabel()
                increaseButton()
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                decreaseButton()
                speedLabel()
                increaseButton()
            }
        }
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
        isLocked = false,
        onToggleLock = { },
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
    name = "Controls - Portrait",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
    backgroundColor = 0xFF000000,
    device = "spec:width=1080px,height=2340px,dpi=440"
)
@Composable
fun ControlsOverlayPreviewPortrait() {
    ControlsOverlay(
        title = "翻滚跌落的男子",
        seasonEpisodeLabel = "S1:E1206 - 翻滚跌落的男子",
        chapterMarkers = listOf(
            ChapterMarker(positionMs = 240000L, label = "Intro"),
            ChapterMarker(positionMs = 900000L, label = "Chapter 2")
        ),
        isPlaying = true,
        currentPosition = 484000L,
        duration = 1480000L,
        onBackClick = { },
        onPlayPause = { },
        onSeek = { },
        isLocked = false,
        onToggleLock = { },
        onShowAudioTrackSelection = { },
        onShowSubtitleTrackSelection = { },
        onCycleAspectRatio = { },
        onSeekBackward = { },
        onSeekForward = { },
        seekBackwardSeconds = 10,
        seekForwardSeconds = 10,
        canPlayPreviousEpisode = true,
        canPlayNextEpisode = true,
        playbackSpeed = 1f,
        hardwareDecodingEnabled = true
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
        chapterMarkers = listOf(
            ChapterMarker(positionMs = 900000L, label = "Intro"),
            ChapterMarker(positionMs = 2400000L, label = "Chapter 2"),
            ChapterMarker(positionMs = 4800000L, label = "Chapter 3"),
            ChapterMarker(positionMs = 6300000L, label = "Credits")
        ),
        onSeek = { },
        onScrubProgressChange = { },
        modifier = Modifier.padding(16.dp)
    )
}

private fun osdHdrLabel(format: String): String {
    val trimmedFormat = format.trim()
    return when {
        trimmedFormat.contains("dolby vision", ignoreCase = true) -> "DV"
        trimmedFormat.contains("hdr10+", ignoreCase = true) -> "HDR10+"
        trimmedFormat.contains("hdr10", ignoreCase = true) -> "HDR10"
        trimmedFormat.equals("hdr", ignoreCase = true) -> "HDR"
        else -> ""
    }
}

private fun osdDescription(label: String): String {
    return if (label == "DV") "Dolby Vision" else label
}
