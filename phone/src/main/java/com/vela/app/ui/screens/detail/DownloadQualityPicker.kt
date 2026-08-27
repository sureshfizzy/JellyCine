package com.vela.app.ui.screens.detail

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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HighQuality
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vela.data.model.MediaStream
import com.vela.player.preferences.TranscodeProfile
import com.vela.player.preferences.TranscodeProfiles
import java.util.Locale
import com.vela.shared.R

data class ResolutionGroup(
    val label: String,
    val maxHeight: Int?,
    val profiles: List<TranscodeProfile>
)

data class DownloadQualitySelection(
    val quality: TranscodeProfile,
    val audioStreamIndex: Int? = null
)

@Composable
internal fun DownloadQualityPicker(
    runtimeTicks: Long?,
    sourceHeight: Int?,
    sourceBitrate: Int?,
    sourceFileSize: Long?,
    audioStreams: List<MediaStream>,
    onDismiss: () -> Unit,
    onSelected: (DownloadQualitySelection) -> Unit
) {
    val resolutionGroups = remember(sourceHeight, sourceBitrate) {
        buildResolutionGroups(sourceHeight, sourceBitrate)
    }
    val sizeEstimator = remember(runtimeTicks, sourceBitrate, sourceFileSize) {
        SizeEstimator(runtimeTicks, sourceBitrate, sourceFileSize)
    }

    var selectedResolution by remember { mutableStateOf<ResolutionGroup?>(null) }
    var selectedProfile by remember { mutableStateOf<TranscodeProfile?>(null) }
    val showAudioStep = audioStreams.size > 1

    fun completeWith(profile: TranscodeProfile) {
        if (showAudioStep) selectedProfile = profile
        else onSelected(DownloadQualitySelection(profile))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF10131A)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                val step = when {
                    selectedProfile != null -> 2
                    selectedResolution != null -> 1
                    else -> 0
                }
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it } + fadeIn())
                                .togetherWith(slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn())
                                .togetherWith(slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "quality_step"
                ) { currentStep ->
                    when (currentStep) {
                        0 -> StepContent(
                            title = "Download Quality",
                            subtitle = "Select resolution",
                            onDismiss = onDismiss
                        ) {
                            item(key = "original") {
                                QualityRow(
                                    title = "Original",
                                    subtitle = "Full quality" + (sizeEstimator.originalSizeLabel()?.let { " · $it" } ?: ""),
                                    onClick = { onSelected(DownloadQualitySelection(TranscodeProfiles.PRESETS.first())) }
                                )
                            }
                            items(resolutionGroups, key = { it.label }) { group ->
                                val midBitrate = group.profiles[group.profiles.size / 2].maxBitrate
                                QualityRow(
                                    title = group.label,
                                    subtitle = "${group.profiles.size} bitrate options",
                                    trailingText = midBitrate?.let { sizeEstimator.estimate(it) }?.let { "~$it" },
                                    showArrow = group.profiles.size > 1,
                                    onClick = {
                                        if (group.profiles.size == 1) completeWith(group.profiles.first())
                                        else selectedResolution = group
                                    }
                                )
                            }
                        }
                        1 -> {
                            val resolution = selectedResolution ?: return@AnimatedContent
                            StepContent(
                                title = resolution.label,
                                subtitle = "Select bitrate",
                                onBack = { selectedResolution = null },
                                onDismiss = onDismiss
                            ) {
                                items(resolution.profiles, key = { it.label }) { profile ->
                                    QualityRow(
                                        title = formatBitrate(profile.maxBitrate),
                                        subtitle = profile.label,
                                        trailingText = profile.maxBitrate?.let { sizeEstimator.estimate(it) }?.let { "~$it" },
                                        onClick = { completeWith(profile) }
                                    )
                                }
                            }
                        }
                        2 -> {
                            val profile = selectedProfile ?: return@AnimatedContent
                            StepContent(
                                title = "Audio Track",
                                subtitle = "Select audio to include",
                                onBack = { selectedProfile = null },
                                onDismiss = onDismiss
                            ) {
                                items(audioStreams, key = { it.index ?: it.hashCode() }) { stream ->
                                    QualityRow(
                                        title = stream.displayTitle ?: buildAudioLabel(stream),
                                        subtitle = listOfNotNull(
                                            stream.codec?.uppercase(Locale.US),
                                            stream.channelLayout ?: stream.channels?.let { "${it}ch" }
                                        ).joinToString(" · "),
                                        trailingText = stream.bitRate?.let { formatBitrate(it) },
                                        onClick = { onSelected(DownloadQualitySelection(profile, stream.index)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepContent(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            PickerHeader(title = title, subtitle = subtitle)
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun PickerHeader(title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF0284C7)))),
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
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = subtitle, color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun QualityRow(
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    showArrow: Boolean = false,
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title, color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle, color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingText != null) {
            Text(text = trailingText, color = Color(0xFF22D3EE), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (showArrow) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight, contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp).size(18.dp)
            )
        }
    }
}

private fun buildResolutionGroups(sourceHeight: Int?, sourceBitrate: Int?): List<ResolutionGroup> {
    val grouped = TranscodeProfiles.PRESETS
        .filter { it.maxHeight != null }
        .groupBy { it.maxHeight!! }
        .toSortedMap(compareByDescending { it })

    return grouped
        .filter { (height, _) -> sourceHeight == null || height <= sourceHeight }
        .map { (height, profiles) ->
            val filtered = if (sourceBitrate != null && sourceBitrate > 0) {
                profiles.filter { val br = it.maxBitrate; br == null || br <= sourceBitrate }
            } else {
                profiles
            }
            ResolutionGroup(
                label = when (height) { 2160 -> "4K"; 1080 -> "1080p"; 720 -> "720p"; 480 -> "480p"; 360 -> "360p"; else -> "${height}p" },
                maxHeight = height,
                profiles = filtered.sortedByDescending { it.maxBitrate }
            )
        }
        .filter { it.profiles.isNotEmpty() }
}

private fun buildAudioLabel(stream: MediaStream): String {
    val lang = stream.language?.let { Locale(it).displayLanguage } ?: "Unknown"
    return if (stream.isDefault == true) "$lang (Default)" else lang
}

private fun formatBitrate(bitrate: Int?): String {
    if (bitrate == null) return "Auto"
    val mbps = bitrate / 1_000_000.0
    return if (mbps >= 1.0) {
        if (mbps == mbps.toLong().toDouble()) "${mbps.toLong()} Mbps"
        else String.format(Locale.US, "%.1f Mbps", mbps)
    } else {
        "${bitrate / 1000} Kbps"
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
        val effectiveVideoBitrate = if (sourceBitrate != null && sourceBitrate > 0) minOf(targetBitrate, sourceBitrate) else targetBitrate
        val estimatedBytes = ((effectiveVideoBitrate + 192_000).toDouble() / 8.0) * seconds
        val capped = if (sourceFileSize != null && sourceFileSize > 0) minOf(estimatedBytes.toLong(), sourceFileSize) else estimatedBytes.toLong()
        return formatBytes(capped)
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = 1024.0 * 1024.0 * 1024.0
    val mb = 1024.0 * 1024.0
    val kb = 1024.0
    val value = bytes.toDouble()
    return when {
        value >= gb -> String.format(Locale.US, "%.1f GB", value / gb)
        value >= mb -> String.format(Locale.US, "%.0f MB", value / mb)
        value >= kb -> String.format(Locale.US, "%.0f KB", value / kb)
        else -> "$bytes B"
    }
}