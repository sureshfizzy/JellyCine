package com.vela.app.ui.screens.player

import android.content.res.Configuration
import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.vela.shared.R
import com.vela.detail.SpatializationResult
import com.vela.player.core.ChapterMarker
import com.vela.player.core.PlayerConstants.PROGRESS_BAR_HEIGHT_DP
import com.vela.player.core.PlayerConstants.PROGRESS_BAR_HIT_HEIGHT_DP
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ScrubPreviewWidth = 160.dp
private val ScrubPreviewHeight = 90.dp

@Composable
fun ControlsOverlay(
    title: String,
    mediaLogoUrl: String? = null,
    seasonEpisodeLabel: String? = null,
    chapterMarkers: List<ChapterMarker> = emptyList(),
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long = 0L,
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
    onAddLocalSubtitle: () -> Unit = {},
    onShowSubtitleStyle: () -> Unit = {},
    onShowSubtitleDelay: () -> Unit = {},
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
    scrubPreviewFrame: Bitmap? = null,
    onScrubPreviewPositionChange: (Long?) -> Unit = {},
    onLiveSeek: (Float) -> Unit = {},
    onEnterPip: () -> Unit = {},
    onToggleHardwareDecoding: () -> Unit = {},
    onShowChapters: () -> Unit = {},
    onNudgeSpeed: (Float) -> Unit = {},
    playbackSpeed: Float = 1f,
    hardwareDecodingEnabled: Boolean = true,
    onUserInteraction: () -> Unit = {},
    onBackgroundClick: () -> Unit = {},
    skipActionLabel: String? = null,
    onSkipAction: () -> Unit = {}
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
            bufferedPosition = bufferedPosition,
            displayedPosition = displayedPosition,
            scrubPreviewFrame = scrubPreviewFrame,
            chapterMarkers = chapterMarkers,
            isLocked = isLocked,
            showPlaybackSettingsButton = showPlaybackSettingsButton,
            onBackClick = onBackClick,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            onLiveSeek = onLiveSeek,
            onScrubProgressChange = { progress ->
                scrubPreviewProgress = progress
                onScrubStateChange(progress != null)
            },
            onScrubPreviewProgressChange = { progress ->
                onScrubPreviewPositionChange(
                    progress
                        ?.takeIf { duration > 0L }
                        ?.let { (duration * it).toLong() }
                )
            },
            onToggleLock = onToggleLock,
            onShowMediaInfo = onShowMediaInfo,
            onShowPlaybackSettings = onShowPlaybackSettings,
            onShowAudioTrackSelection = onShowAudioTrackSelection,
            onShowSubtitleTrackSelection = onShowSubtitleTrackSelection,
            onAddLocalSubtitle = onAddLocalSubtitle,
            onShowSubtitleStyle = onShowSubtitleStyle,
            onShowSubtitleDelay = onShowSubtitleDelay,
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
            isScrubbing = scrubPreviewProgress != null,
            landscape = !isPortrait,
            skipActionLabel = skipActionLabel,
            onSkipAction = onSkipAction,
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
    bufferedPosition: Long = 0L,
    displayedPosition: Long,
    scrubPreviewFrame: Bitmap?,
    chapterMarkers: List<ChapterMarker>,
    isLocked: Boolean,
    showPlaybackSettingsButton: Boolean,
    onBackClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onLiveSeek: (Float) -> Unit,
    onScrubProgressChange: (Float?) -> Unit,
    onScrubPreviewProgressChange: (Float?) -> Unit,
    onToggleLock: () -> Unit,
    onShowMediaInfo: () -> Unit,
    onShowPlaybackSettings: () -> Unit,
    onShowAudioTrackSelection: () -> Unit,
    onShowSubtitleTrackSelection: () -> Unit,
    onAddLocalSubtitle: () -> Unit,
    onShowSubtitleStyle: () -> Unit,
    onShowSubtitleDelay: () -> Unit,
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
    isScrubbing: Boolean = false,
    landscape: Boolean = false,
    skipActionLabel: String? = null,
    onSkipAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showOverflow by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    val playerHud = rememberPlayerHudStats()
    val progress = if (duration > 0 && currentPosition >= 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedProgress = if (duration > 0 && bufferedPosition > 0) {
        (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val chromeInteraction = remember { MutableInteractionSource() }
    val headline = seasonEpisodeLabel?.takeIf { it.isNotBlank() } ?: title
    val iconTint = Color.White
    val disabledTint = Color.White.copy(alpha = 0.35f)
    val chromeEdgeInset = if (landscape) 8.dp else PlayerChromeInset


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
                        start = chromeEdgeInset,
                        end = PlayerChromeEndInset,
                        top = PlayerChromeTopGap
                    )
                    .align(Alignment.TopCenter)
                    .clickable(
                        interactionSource = chromeInteraction,
                        indication = null,
                        onClick = onUserInteraction
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PlayerGlassIconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back_button),
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
                PlayerGlass(
                    modifier = Modifier.height(PlayerGlassButtonSize),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerChromeIconButton(
                            onClick = onToggleHardwareDecoding,
                            modifier = Modifier
                                .height(PlayerChromeIconSize)
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
                        PlayerChromeIconButton(onClick = onToggleOrientation) {
                            Icon(
                                imageVector = Icons.Outlined.ScreenRotation,
                                contentDescription = stringResource(R.string.player_cd_toggle_orientation),
                                tint = iconTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        PlayerChromeIconButton(onClick = onEnterPip) {
                            Icon(
                                imageVector = Icons.Outlined.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.player_pip),
                                tint = iconTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        PlayerChromeIconButton(onClick = onCycleAspectRatio) {
                            Icon(
                                imageVector = Icons.Outlined.AspectRatio,
                                contentDescription = stringResource(R.string.player_settings_start_maximized),
                                tint = iconTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        PlayerChromeIconButton(onClick = { showOverflow = !showOverflow }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.player_more),
                                tint = iconTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
            if (!showOverflow) {
                OverlayPlaybackStats(
                    batteryPercent = playerHud.batteryPercent,
                    clock = playerHud.clock,
                    speedLabel = playerHud.speedLabel,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(
                            WindowInsets.displayCutout.only(
                                WindowInsetsSides.Top + WindowInsetsSides.End
                            )
                        )
                        .padding(
                            top = PlayerGlassButtonSize + PlayerChromeTopGap + 6.dp,
                            end = PlayerChromeEndInset
                        )
                )
            }
            if (showOverflow) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showOverflow = false }
                        )
                )
                PlayerOverflowMenu(
                    onChapters = onShowChapters,
                    onMediaInfo = onShowMediaInfo,
                    onDismiss = { showOverflow = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(
                            WindowInsets.displayCutout.only(
                                WindowInsetsSides.Top + WindowInsetsSides.End
                            )
                        )
                        .padding(
                            top = PlayerGlassButtonSize + PlayerChromeTopGap + 4.dp,
                            end = PlayerChromeEndInset
                        )
                )
            }
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
                                WindowInsets.displayCutout.only(WindowInsetsSides.Start)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = chromeEdgeInset)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.player_unlock),
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        if (!isLocked) {
            if (landscape) {
                PlayerGlassIconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                        .padding(start = chromeEdgeInset)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LockOpen,
                        contentDescription = stringResource(R.string.player_lock),
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
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
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                        .padding(end = chromeEdgeInset)
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
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    OverlayTransportButtons(
                        isPlaying = isPlaying,
                        seekBackwardSeconds = seekBackwardSeconds,
                        seekForwardSeconds = seekForwardSeconds,
                        iconTint = iconTint,
                        networkSpeed = playerHud.speedLabel,
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
                            Modifier
                                .windowInsetsPadding(
                                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                                )
                                .windowInsetsPadding(
                                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
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
                        start = chromeEdgeInset,
                        end = chromeEdgeInset,
                        bottom = PlayerChromeBottomGap
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlayerGlassIconButton(
                        onClick = onPlayPreviousEpisode,
                        enabled = canPlayPreviousEpisode,
                        size = 48.dp
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.player_previous_episode),
                            tint = if (canPlayPreviousEpisode) iconTint else disabledTint,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    PlayerGlassIconButton(
                        onClick = onPlayNextEpisode,
                        enabled = canPlayNextEpisode,
                        size = 48.dp
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.player_next_episode),
                            tint = if (canPlayNextEpisode) iconTint else disabledTint,
                            modifier = Modifier.size(26.dp)
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
                    val skipLabel = skipActionLabel
                    if (!skipLabel.isNullOrBlank()) {
                        PlayerGlass(
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = skipLabel,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .clickable(onClick = onSkipAction)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
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
                            bufferedProgress = bufferedProgress,
                            duration = duration,
                            chapterMarkers = chapterMarkers,
                            previewFrame = scrubPreviewFrame,
                            onSeek = onSeek,
                            onLiveSeek = onLiveSeek,
                            onScrubProgressChange = onScrubProgressChange,
                            onScrubPreviewProgressChange = onScrubPreviewProgressChange,
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
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                bufferedProgress = bufferedProgress,
                                duration = duration,
                                chapterMarkers = chapterMarkers,
                                previewFrame = scrubPreviewFrame,
                                onSeek = onSeek,
                                onLiveSeek = onLiveSeek,
                                onScrubProgressChange = onScrubProgressChange,
                                onScrubPreviewProgressChange = onScrubPreviewProgressChange,
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
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        PlayerChromeIconButton(onClick = onShowAudioTrackSelection) {
                            Icon(
                                imageVector = Icons.Outlined.MusicNote,
                                contentDescription = stringResource(R.string.player_dialog_audio_title),
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Box {
                            PlayerChromeIconButton(
                                onClick = onShowSubtitleTrackSelection,
                                onLongClick = { showSubtitleMenu = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ClosedCaption,
                                    contentDescription = stringResource(R.string.player_dialog_subtitles_title),
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showSubtitleMenu,
                                onDismissRequest = { showSubtitleMenu = false },
                                containerColor = Color(0xE61C1C1E),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.player_subtitle_add_local),
                                            color = Color.White
                                        )
                                    },
                                    onClick = {
                                        showSubtitleMenu = false
                                        onAddLocalSubtitle()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.player_subtitle_scale_position),
                                            color = Color.White
                                        )
                                    },
                                    onClick = {
                                        showSubtitleMenu = false
                                        onShowSubtitleStyle()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.OpenWith,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.player_subtitle_time_offset),
                                            color = Color.White
                                        )
                                    },
                                    onClick = {
                                        showSubtitleMenu = false
                                        onShowSubtitleDelay()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                )
                            }
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
    previewFrame: Bitmap?,
    onSeek: (Float) -> Unit,
    onLiveSeek: (Float) -> Unit = {},
    onScrubProgressChange: (Float?) -> Unit,
    onScrubPreviewProgressChange: (Float?) -> Unit,
    bufferedProgress: Float = 0f,
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
    val previewWidthPx = with(density) { ScrubPreviewWidth.roundToPx() }
    val previewYOffsetPx = with(density) {
        (if (previewFrame != null) (-128).dp else (-42).dp).roundToPx()
    }

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
                        // 向前 live seek 会落到更早的关键帧，点选目标位置会被带回去。
                        // 只在向后或已缓冲范围内预览；最终位置一律在 UP 时 exact seek。
                        if (canLiveSeek(newProgress, progress, bufferedProgress)) {
                            onLiveSeek(scrubProgress)
                        }
                        onScrubPreviewProgressChange(scrubProgress)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        scrubProgress = newProgress
                        onScrubProgressChange(scrubProgress)
                        val now = SystemClock.uptimeMillis()
                        if (
                            canLiveSeek(newProgress, progress, bufferedProgress) &&
                            now - lastLiveSeekAt >= 80L
                        ) {
                            lastLiveSeekAt = now
                            onLiveSeek(scrubProgress)
                        }
                        onScrubPreviewProgressChange(scrubProgress)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        scrubProgress = newProgress
                        dragActive = false
                        onSeek(scrubProgress)
                        onScrubProgressChange(null)
                        onScrubPreviewProgressChange(null)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        dragActive = false
                        scrubProgress = progress.coerceIn(0f, 1f)
                        onScrubProgressChange(null)
                        onScrubPreviewProgressChange(null)
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
                color = Color.White.copy(alpha = 0.22f),
                start = trackStart,
                end = trackEnd,
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            val renderedBuffered = bufferedProgress.coerceIn(0f, 1f)
            if (renderedBuffered > renderedProgress) {
                val bufferedX = trackStart.x + (trackEnd.x - trackStart.x) * renderedBuffered
                drawLine(
                    color = Color.White.copy(alpha = 0.52f),
                    start = trackStart,
                    end = Offset(bufferedX, yOffset),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

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
                    val maxPreviewX = (widthPx - previewWidthPx).coerceAtLeast(0)
                    IntOffset(
                        x = (thumbCenterX - previewWidthPx / 2).coerceIn(0, maxPreviewX),
                        y = previewYOffsetPx
                    )
                },
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 4.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier.width(ScrubPreviewWidth)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    previewFrame?.let { frame ->
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ScrubPreviewHeight)
                        )
                    }
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
}

private fun canLiveSeek(
    targetProgress: Float,
    currentProgress: Float,
    bufferedProgress: Float
): Boolean {
    return targetProgress <= currentProgress + 0.0005f ||
        targetProgress <= bufferedProgress + 0.0005f
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
private fun PlayerOverflowMenu(
    onChapters: () -> Unit,
    onMediaInfo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerGlass(
        modifier = modifier
            .widthIn(min = 176.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            PlayerOverflowMenuItem(
                text = stringResource(R.string.player_chapters),
                onClick = {
                    onDismiss()
                    onChapters()
                }
            )
            PlayerOverflowMenuItem(
                text = stringResource(R.string.player_media_info),
                onClick = {
                    onDismiss()
                    onMediaInfo()
                }
            )
        }
    }
}

@Composable
private fun PlayerOverflowMenuItem(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    )
}

@Composable
private fun OverlayPlaybackStats(
    batteryPercent: Int,
    clock: String,
    speedLabel: String,
    modifier: Modifier = Modifier
) {
    val batteryText = if (batteryPercent >= 0) "${batteryPercent}%" else "--%"
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.BatteryStd,
            contentDescription = null,
            tint = Color(0xFF7CFF6B),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = batteryText,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = clock,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = speedLabel,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun rememberPlayerHudStats(): PlayerHudStats {
    val context = LocalContext.current
    var batteryPercent by remember {
        mutableIntStateOf(readBatteryPercent(context))
    }
    var clock by remember {
        mutableStateOf(currentClockText())
    }
    var speedLabel by remember { mutableStateOf("0.00 MB/s") }
    LaunchedEffect(Unit) {
        var lastRx = android.net.TrafficStats.getTotalRxBytes()
        var lastTime = android.os.SystemClock.elapsedRealtime()
        while (true) {
            kotlinx.coroutines.delay(1_000)
            clock = currentClockText()
            batteryPercent = readBatteryPercent(context)
            val now = android.os.SystemClock.elapsedRealtime()
            val rx = android.net.TrafficStats.getTotalRxBytes()
            val elapsed = (now - lastTime).coerceAtLeast(1L)
            speedLabel = if (rx >= 0L && lastRx >= 0L) {
                val bytesPerSec = (rx - lastRx).coerceAtLeast(0L) * 1000.0 / elapsed
                String.format(java.util.Locale.US, "%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
            } else {
                "-- MB/s"
            }
            lastRx = rx
            lastTime = now
        }
    }
    return PlayerHudStats(
        batteryPercent = batteryPercent,
        clock = clock,
        speedLabel = speedLabel
    )
}

private data class PlayerHudStats(
    val batteryPercent: Int,
    val clock: String,
    val speedLabel: String
)

private fun currentClockText(): String {
    return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date())
}

private fun readBatteryPercent(context: android.content.Context): Int {
    val manager = context.getSystemService(android.content.Context.BATTERY_SERVICE)
        as? android.os.BatteryManager
    return manager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}

@Composable
private fun OverlayTransportButtons(
    isPlaying: Boolean,
    seekBackwardSeconds: Int,
    seekForwardSeconds: Int,
    iconTint: Color,
    networkSpeed: String = "",
    onSeekBackward: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp)
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
                modifier = Modifier.size(30.dp)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    modifier = Modifier.size(if (isPlaying) 36.dp else 44.dp)
                )
            }
            if (networkSpeed.isNotBlank()) {
                Text(
                    text = networkSpeed,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
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
                    .size(30.dp)
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
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.player_speed_decrease),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    val increaseButton = @Composable {
        PlayerChromeIconButton(
            onClick = { onNudgeSpeed(0.25f) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.player_speed_increase),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
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
        modifier = modifier.then(if (vertical) Modifier.width(56.dp) else Modifier),
        shape = RoundedCornerShape(999.dp)
    ) {
        if (vertical) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
        previewFrame = null,
        onSeek = { },
        onScrubProgressChange = { },
        onScrubPreviewProgressChange = { },
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
