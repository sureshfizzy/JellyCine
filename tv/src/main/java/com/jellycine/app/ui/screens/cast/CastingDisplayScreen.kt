package com.jellycine.app.ui.screens.cast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.jellycine.app.cast.CastPlaybackState
import com.jellycine.player.core.PlayerConstants.PROGRESS_BAR_HEIGHT_DP

data class CastTrackOption(
    val label: String,
    val streamIndex: Int?
)

@Composable
fun CastingDisplayScreen(
    castState: CastPlaybackState,
    onBackPressed: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStopCasting: () -> Unit,
    onDisconnect: () -> Unit,
    onSeekTo: (Long) -> Unit,
    fallbackArtworkUrl: String?,
    audioTrackOptions: List<CastTrackOption>,
    selectedAudioTrackIndex: Int?,
    onAudioTrackSelected: (Int?) -> Unit,
    subtitleTrackOptions: List<CastTrackOption>,
    selectedSubtitleTrackIndex: Int?,
    onSubtitleTrackSelected: (Int?) -> Unit,
    isTrackSelectionUpdating: Boolean
) {
    val durationMs = castState.durationMs.coerceAtLeast(0L)
    val positionMs = castState.positionMs.coerceIn(0L, if (durationMs > 0L) durationMs else Long.MAX_VALUE)
    val seekProgress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val targetDevice = castState.deviceName?.takeIf { it.isNotBlank() } ?: "your device"
    val mediaTitle = castState.mediaTitle?.takeIf { it.isNotBlank() } ?: "Now Playing"
    val mediaSubtitle = castState.mediaSubtitle?.takeIf { it.isNotBlank() }
    val artworkUrl = castState.artworkUrl ?: fallbackArtworkUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .zIndex(1f),
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.09f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close casting screen",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 360.dp,
                        max = 680.dp
                    ),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xD912171E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF1D2A38)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cast,
                                    contentDescription = null,
                                    tint = Color(0xFF7DFFCB),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Casting",
                                    color = Color(0xFFE7FFF6),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF1A2129)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Devices,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = targetDevice,
                                    color = Color.White.copy(alpha = 0.94f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(96.dp)
                                .height(132.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1A2430)
                        ) {
                            if (!artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = artworkUrl,
                                    contentDescription = mediaTitle,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cast,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.42f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cast,
                                contentDescription = null,
                                tint = Color(0xFF78F5C8),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = mediaTitle,
                                fontSize = 22.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            mediaSubtitle?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.72f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (audioTrackOptions.size > 1 || subtitleTrackOptions.size > 1) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (audioTrackOptions.size > 1) {
                                TrackOptionRow(
                                    label = "Audio",
                                    options = audioTrackOptions,
                                    selectedTrackIndex = selectedAudioTrackIndex,
                                    onTrackSelected = onAudioTrackSelected,
                                    enabled = !isTrackSelectionUpdating
                                )
                            }
                            if (subtitleTrackOptions.size > 1) {
                                TrackOptionRow(
                                    label = "Subtitles",
                                    options = subtitleTrackOptions,
                                    selectedTrackIndex = selectedSubtitleTrackIndex,
                                    onTrackSelected = onSubtitleTrackSelected,
                                    enabled = !isTrackSelectionUpdating
                                )
                            }
                            if (isTrackSelectionUpdating) {
                                Text(
                                    text = "Updating stream selection...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9FEBD4)
                                )
                            }
                        }
                    }

                    CastSeekBar(
                        progress = seekProgress,
                        enabled = durationMs > 0L,
                        onSeek = { progress ->
                            if (durationMs > 0L) {
                                onSeekTo((durationMs.toFloat() * progress).toLong())
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(positionMs),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (durationMs > 0L) formatDuration(durationMs) else "--:--",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onTogglePlayPause,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2BC396),
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = if (castState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                            Text(
                                text = if (castState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.padding(start = 6.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onStopCasting,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
                            Text(
                                text = "Stop",
                                modifier = Modifier.padding(start = 6.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFF3D4A57)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFE2EAF5)
                        )
                    ) {
                        Text(text = "Disconnect Device", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CastSeekBar(
    progress: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .height(PROGRESS_BAR_HEIGHT_DP.dp)
            .pointerInput(enabled) {
                detectTapGestures { offset ->
                    if (!enabled || size.width <= 0f) return@detectTapGestures
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
    ) {
        val yOffset = size.height / 2
        val trackInset = 2.dp.toPx()
        val trackStart = Offset(trackInset, yOffset)
        val trackEnd = Offset(size.width - trackInset, yOffset)
        val trackHeight = size.height * 0.55f
        val inactiveColor = if (enabled) {
            Color.White.copy(alpha = 0.35f)
        } else {
            Color.White.copy(alpha = 0.18f)
        }
        val activeColor = if (enabled) {
            Color.White.copy(alpha = 0.95f)
        } else {
            Color.White.copy(alpha = 0.55f)
        }

        drawLine(
            color = inactiveColor,
            start = trackStart,
            end = trackEnd,
            strokeWidth = trackHeight,
            cap = StrokeCap.Round
        )

        if (progress > 0f) {
            val progressX = trackStart.x + (trackEnd.x - trackStart.x) * progress.coerceIn(0f, 1f)
            drawLine(
                color = activeColor,
                start = trackStart,
                end = Offset(progressX, yOffset),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun TrackOptionRow(
    label: String,
    options: List<CastTrackOption>,
    selectedTrackIndex: Int?,
    onTrackSelected: (Int?) -> Unit,
    enabled: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.78f)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = options,
                key = { option -> "${option.streamIndex}_${option.label}" }
            ) { option ->
                val isSelected = option.streamIndex == selectedTrackIndex
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            onTrackSelected(option.streamIndex)
                        }
                    },
                    enabled = enabled,
                    label = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF234A3D),
                        selectedLabelColor = Color(0xFFE7FFF6),
                        containerColor = Color(0xFF1A2129),
                        labelColor = Color.White.copy(alpha = 0.88f),
                        disabledContainerColor = Color(0xFF131922),
                        disabledLabelColor = Color.White.copy(alpha = 0.45f)
                    )
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
