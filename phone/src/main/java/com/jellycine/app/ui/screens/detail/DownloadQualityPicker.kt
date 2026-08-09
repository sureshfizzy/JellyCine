package com.jellycine.app.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jellycine.player.preferences.TranscodeProfile
import com.jellycine.player.preferences.TranscodeProfiles
import java.util.Locale

data class ResolutionGroup(
    val label: String,
    val maxHeight: Int?,
    val profiles: List<TranscodeProfile>
)

@Composable
internal fun DownloadQualityPicker(
    runtimeTicks: Long?,
    sourceHeight: Int?,
    sourceBitrate: Int?,
    sourceFileSize: Long?,
    onDismiss: () -> Unit,
    onSelected: (TranscodeProfile) -> Unit
) {
    val resolutionGroups = remember(sourceHeight, sourceBitrate) {
        buildResolutionGroups(sourceHeight, sourceBitrate)
    }

    val sizeEstimator = remember(runtimeTicks, sourceBitrate, sourceFileSize) {
        SizeEstimator(runtimeTicks, sourceBitrate, sourceFileSize)
    }

    var selectedResolution by remember { mutableStateOf<ResolutionGroup?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF10131A)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                AnimatedContent(
                    targetState = selectedResolution,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally { it } + fadeIn())
                                .togetherWith(slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn())
                                .togetherWith(slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "quality_step"
                ) { resolution ->
                    if (resolution == null) {
                        ResolutionStep(
                            groups = resolutionGroups,
                            sizeEstimator = sizeEstimator,
                            onSelectOriginal = {
                                onSelected(TranscodeProfiles.PRESETS.first())
                            },
                            onSelectResolution = { group ->
                                if (group.profiles.size == 1) {
                                    onSelected(group.profiles.first())
                                } else {
                                    selectedResolution = group
                                }
                            },
                            onDismiss = onDismiss
                        )
                    } else {
                        BitrateStep(
                            group = resolution,
                            sizeEstimator = sizeEstimator,
                            onBack = { selectedResolution = null },
                            onSelected = onSelected,
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolutionStep(
    groups: List<ResolutionGroup>,
    sizeEstimator: SizeEstimator,
    onSelectOriginal: () -> Unit,
    onSelectResolution: (ResolutionGroup) -> Unit,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PickerHeader(
            title = "Download Quality",
            subtitle = "Select resolution"
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "original") {
                val originalSize = sizeEstimator.originalSizeLabel()
                QualityRow(
                    title = "Original",
                    subtitle = "Full quality" + if (originalSize != null) " · $originalSize" else "",
                    trailingText = null,
                    showArrow = false,
                    onClick = onSelectOriginal
                )
            }

            items(groups, key = { it.label }) { group ->
                val midBitrate = group.profiles[group.profiles.size / 2].maxBitrate
                val estimatedSize = midBitrate?.let { sizeEstimator.estimate(it) }

                QualityRow(
                    title = group.label,
                    subtitle = "${group.profiles.size} bitrate options",
                    trailingText = estimatedSize?.let { "~$it" },
                    showArrow = group.profiles.size > 1,
                    onClick = { onSelectResolution(group) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun BitrateStep(
    group: ResolutionGroup,
    sizeEstimator: SizeEstimator,
    onBack: () -> Unit,
    onSelected: (TranscodeProfile) -> Unit,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            PickerHeader(
                title = group.label,
                subtitle = "Select bitrate"
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(group.profiles, key = { it.label }) { profile ->
                val bitrateLabel = formatBitrate(profile.maxBitrate)
                val bitrate = profile.maxBitrate
                val estimatedSize = bitrate?.let { sizeEstimator.estimate(it) }

                QualityRow(
                    title = bitrateLabel,
                    subtitle = profile.label,
                    trailingText = estimatedSize?.let { "~$it" },
                    showArrow = false,
                    onClick = { onSelected(profile) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun PickerHeader(
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF22D3EE),
                            Color(0xFF0284C7)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.HighQuality,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun QualityRow(
    title: String,
    subtitle: String?,
    trailingText: String?,
    showArrow: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                color = Color(0xFF22D3EE),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (showArrow) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp)
            )
        }
    }
}

private fun buildResolutionGroups(sourceHeight: Int?, sourceBitrate: Int?): List<ResolutionGroup> {
    val nonOriginal = TranscodeProfiles.PRESETS.filter { it.maxHeight != null }
    val grouped = nonOriginal.groupBy { it.maxHeight!! }
        .toSortedMap(compareByDescending { it })

    return grouped
        .filter { (height, _) -> sourceHeight == null || height <= sourceHeight }
        .map { (height, profiles) ->
            val filtered = if (sourceBitrate != null && sourceBitrate > 0) {
                profiles.filter { val br = it.maxBitrate; br == null || br <= sourceBitrate }
            } else {
                profiles
            }
            val label = when (height) {
                2160 -> "4K"
                1080 -> "1080p"
                720 -> "720p"
                480 -> "480p"
                360 -> "360p"
                else -> "${height}p"
            }
            ResolutionGroup(
                label = label,
                maxHeight = height,
                profiles = filtered.sortedByDescending { it.maxBitrate }
            )
        }
        .filter { it.profiles.isNotEmpty() }
}

private fun formatBitrate(bitrate: Int?): String {
    if (bitrate == null) return "Auto"
    val mbps = bitrate / 1_000_000.0
    return if (mbps >= 1.0) {
        if (mbps == mbps.toLong().toDouble()) {
            "${mbps.toLong()} Mbps"
        } else {
            String.format(Locale.US, "%.1f Mbps", mbps)
        }
    } else {
        "${(bitrate / 1000)} Kbps"
    }
}

private class SizeEstimator(
    private val runtimeTicks: Long?,
    private val sourceBitrate: Int?,
    private val sourceFileSize: Long?
) {
    private val runtimeSeconds: Double? = runtimeTicks?.let { it / 10_000_000.0 }

    fun originalSizeLabel(): String? = sourceFileSize?.let { formatBytes(it) }

    fun estimate(targetBitrate: Int): String? {
        val seconds = runtimeSeconds ?: return null
        if (seconds <= 0.0) return null

        val effectiveVideoBitrate = if (sourceBitrate != null && sourceBitrate > 0) {
            minOf(targetBitrate, sourceBitrate)
        } else {
            targetBitrate
        }

        val totalBitrate = effectiveVideoBitrate + AUDIO_OVERHEAD_BPS
        val estimatedBytes = (totalBitrate.toDouble() / 8.0) * seconds

        val cappedBytes = if (sourceFileSize != null && sourceFileSize > 0) {
            minOf(estimatedBytes.toLong(), sourceFileSize)
        } else {
            estimatedBytes.toLong()
        }

        return formatBytes(cappedBytes)
    }

    companion object {
        private const val AUDIO_OVERHEAD_BPS = 192_000
    }
}

private fun formatBytes(bytes: Long): String {
    val value = bytes.toDouble()
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        value >= gb -> String.format(Locale.US, "%.1f GB", value / gb)
        value >= mb -> String.format(Locale.US, "%.0f MB", value / mb)
        value >= kb -> String.format(Locale.US, "%.0f KB", value / kb)
        else -> "$bytes B"
    }
}