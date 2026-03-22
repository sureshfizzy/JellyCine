package com.jellycine.app.ui.screens.dashboard.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.app.R
import com.jellycine.player.preferences.PlayerPreferences
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onBackPressed: () -> Unit = {},
    onNavigateToSubtitleSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: PlayerSettingsViewModel = viewModel { PlayerSettingsViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    val decodingColor = Color(0xFF3B82F6)
    val transcodingColor = Color(0xFF8B5CF6)
    val videoColor = Color(0xFFF97316)
    val gesturesColor = Color(0xFF14B8A6)
    val seekingColor = Color(0xFFEF4444)
    val performanceColor = Color(0xFF22C55E)
    val cacheColor = Color(0xFF06B6D4)

    Scaffold(
        topBar = {
            topbar(
                title = stringResource(R.string.player_settings_title),
                onBackPressed = onBackPressed
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { SectionLabel(stringResource(R.string.player_settings_section_decoding)) }
            item {
                SettingsSection {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Speed,
                        title = stringResource(R.string.player_settings_hardware_acceleration),
                        subtitle = stringResource(R.string.player_settings_hardware_acceleration_summary),
                        checked = uiState.hardwareDecodingEnabled,
                        onCheckedChange = viewModel::setHardwareDecodingEnabled,
                        accentColor = decodingColor
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SettingsDivider()
                        SwitchSettingsItem(
                            icon = Icons.Rounded.Tune,
                            title = stringResource(R.string.player_settings_async_mediacodec),
                            subtitle = stringResource(R.string.player_settings_async_mediacodec_summary),
                            checked = uiState.asyncMediaCodecEnabled,
                            onCheckedChange = viewModel::setAsyncMediaCodecEnabled,
                            enabled = uiState.hardwareDecodingEnabled,
                            accentColor = decodingColor
                        )
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.player_settings_section_subtitles)) }
            item {
                SettingsSection {
                    ClickableSettingsItem(
                        icon = Icons.Rounded.VideoSettings,
                        title = stringResource(R.string.player_settings_subtitles),
                        subtitle = stringResource(R.string.player_settings_subtitles_summary),
                        onClick = onNavigateToSubtitleSettings,
                        accentColor = Color(0xFF6366F1)
                    )
                }
            }

            if (uiState.isVideoTranscodingAllowed || uiState.isAudioTranscodingAllowed) {
                item { SectionLabel(stringResource(R.string.player_settings_section_transcoding)) }
                item {
                    SettingsSection {
                        if (uiState.isVideoTranscodingAllowed) {
                            DropdownSettingsItem(
                                icon = Icons.Rounded.HighQuality,
                                title = stringResource(R.string.player_settings_streaming_quality),
                                subtitle = uiState.streamingQuality,
                                options = PlayerPreferences.STREAMING_QUALITY_OPTIONS,
                                onOptionSelected = viewModel::setStreamingQuality,
                                accentColor = transcodingColor
                            )
                        }

                        if (uiState.isVideoTranscodingAllowed && uiState.isAudioTranscodingAllowed) {
                            SettingsDivider()
                        }

                        if (uiState.isAudioTranscodingAllowed) {
                            DropdownSettingsItem(
                                icon = Icons.Rounded.AudioFile,
                                title = stringResource(R.string.player_settings_audio_quality),
                                subtitle = uiState.audioTranscodeMode,
                                options = PlayerPreferences.AUDIO_TRANSCODE_MODE_OPTIONS,
                                onOptionSelected = viewModel::setAudioTranscodeMode,
                                accentColor = transcodingColor
                            )
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.player_settings_section_video)) }
            item {
                SettingsSection {
                    DropdownSettingsItem(
                        icon = Icons.Rounded.VideoSettings,
                        title = stringResource(R.string.player_settings_decoder_priority),
                        subtitle = decoderPriorityLabel(uiState.decoderPriority),
                        options = listOf("Hardware First", "Software First", "Auto"),
                        optionLabel = { decoderPriorityLabel(it) },
                        onOptionSelected = viewModel::setDecoderPriority,
                        accentColor = videoColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Fullscreen,
                        title = stringResource(R.string.player_settings_start_maximized),
                        subtitle = stringResource(R.string.player_settings_start_maximized_summary),
                        checked = uiState.startMaximized,
                        onCheckedChange = viewModel::setStartMaximized,
                        accentColor = videoColor
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.player_settings_section_gestures)) }
            item {
                SettingsSection {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.player_settings_gestures),
                        subtitle = stringResource(R.string.player_settings_gestures_summary),
                        checked = uiState.playerGesturesEnabled,
                        onCheckedChange = viewModel::setPlayerGesturesEnabled,
                        accentColor = gesturesColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Brush,
                        title = stringResource(R.string.player_settings_volume_brightness_gestures),
                        subtitle = stringResource(R.string.player_settings_volume_brightness_gestures_summary),
                        checked = uiState.volumeBrightnessGesturesEnabled,
                        onCheckedChange = viewModel::setVolumeBrightnessGesturesEnabled,
                        enabled = uiState.playerGesturesEnabled,
                        accentColor = gesturesColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.FastForward,
                        title = stringResource(R.string.player_settings_progress_seek_gesture),
                        subtitle = stringResource(R.string.player_settings_progress_seek_gesture_summary),
                        checked = uiState.progressSeekGestureEnabled,
                        onCheckedChange = viewModel::setProgressSeekGestureEnabled,
                        enabled = uiState.playerGesturesEnabled,
                        accentColor = gesturesColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Fullscreen,
                        title = stringResource(R.string.player_settings_zoom_gesture),
                        subtitle = stringResource(R.string.player_settings_zoom_gesture_summary),
                        checked = uiState.zoomGestureEnabled,
                        onCheckedChange = viewModel::setZoomGestureEnabled,
                        enabled = uiState.playerGesturesEnabled,
                        accentColor = gesturesColor
                    )

                }
            }

            item { SectionLabel(stringResource(R.string.player_settings_section_seeking)) }
            item {
                SettingsSection {
                    DropdownSettingsItem(
                        icon = Icons.Rounded.FastRewind,
                        title = stringResource(R.string.player_settings_seek_backward),
                        subtitle = stringResource(
                            R.string.player_settings_seek_interval_value,
                            uiState.seekBackwardIntervalSeconds
                        ),
                        options = (PlayerPreferences.MIN_SEEK_INTERVAL_SECONDS..PlayerPreferences.MAX_SEEK_INTERVAL_SECONDS step PlayerPreferences.SEEK_INTERVAL_STEP_SECONDS)
                            .map(Int::toString),
                        optionLabel = { seconds ->
                            stringResource(R.string.player_settings_seek_interval_value, seconds.toInt())
                        },
                        onOptionSelected = { seconds ->
                            viewModel.setSeekBackwardIntervalSeconds(seconds.toInt())
                        },
                        accentColor = seekingColor
                    )

                    SettingsDivider()
                    DropdownSettingsItem(
                        icon = Icons.Rounded.FastForward,
                        title = stringResource(R.string.player_settings_seek_forward),
                        subtitle = stringResource(
                            R.string.player_settings_seek_interval_value,
                            uiState.seekForwardIntervalSeconds
                        ),
                        options = (PlayerPreferences.MIN_SEEK_INTERVAL_SECONDS..PlayerPreferences.MAX_SEEK_INTERVAL_SECONDS step PlayerPreferences.SEEK_INTERVAL_STEP_SECONDS)
                            .map(Int::toString),
                        optionLabel = { seconds ->
                            stringResource(R.string.player_settings_seek_interval_value, seconds.toInt())
                        },
                        onOptionSelected = { seconds ->
                            viewModel.setSeekForwardIntervalSeconds(seconds.toInt())
                        },
                        accentColor = seekingColor
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.player_settings_section_player_cache)) }
            item {
                SettingsSection {
                    ValueSliderSettingsItem(
                        icon = Icons.Rounded.Storage,
                        title = stringResource(R.string.player_settings_player_cache_size),
                        subtitle = stringResource(R.string.player_settings_player_cache_size_summary),
                        value = uiState.playerCacheSizeMb,
                        defaultValue = PlayerPreferences.DEFAULT_PLAYER_CACHE_SIZE_MB,
                        minValue = PlayerPreferences.MIN_PLAYER_CACHE_SIZE_MB,
                        maxValue = PlayerPreferences.MAX_PLAYER_CACHE_SIZE_MB,
                        stepSize = PlayerPreferences.PLAYER_CACHE_SIZE_STEP_MB,
                        onValueChanged = viewModel::setPlayerCacheSizeMb,
                        valueLabel = { sizeMb ->
                            stringResource(R.string.player_settings_player_cache_size_value, sizeMb)
                        },
                        defaultLabel = { sizeMb ->
                            stringResource(
                                R.string.player_settings_default_value,
                                stringResource(R.string.player_settings_player_cache_size_value, sizeMb)
                            )
                        },
                        accentColor = cacheColor
                    )

                    SettingsDivider()
                    ValueSliderSettingsItem(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(R.string.player_settings_player_cache_time),
                        subtitle = stringResource(R.string.player_settings_player_cache_time_summary),
                        value = uiState.playerCacheTimeSeconds,
                        defaultValue = PlayerPreferences.DEFAULT_PLAYER_CACHE_TIME_SECONDS,
                        minValue = PlayerPreferences.MIN_PLAYER_CACHE_TIME_SECONDS,
                        maxValue = PlayerPreferences.MAX_PLAYER_CACHE_TIME_SECONDS,
                        stepSize = PlayerPreferences.PLAYER_CACHE_TIME_STEP_SECONDS,
                        onValueChanged = viewModel::setPlayerCacheTimeSeconds,
                        valueLabel = { seconds ->
                            stringResource(R.string.player_settings_player_cache_time_value, seconds)
                        },
                        defaultLabel = { seconds ->
                            stringResource(
                                R.string.player_settings_default_value,
                                stringResource(R.string.player_settings_player_cache_time_value, seconds)
                            )
                        },
                        accentColor = cacheColor
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.player_settings_section_performance)) }
            item {
                SettingsSection {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.BatteryStd,
                        title = stringResource(R.string.player_settings_battery_optimization),
                        subtitle = stringResource(R.string.player_settings_battery_optimization_summary),
                        checked = uiState.batteryOptimizationEnabled,
                        onCheckedChange = viewModel::setBatteryOptimizationEnabled,
                        accentColor = performanceColor
                    )
                }
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsScreen(
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val subtitleAccent = Color(0xFF6366F1)
    val positionAccent = Color(0xFF0EA5E9)

    var textSize by remember { mutableStateOf(playerPreferences.getSubtitleTextSize()) }
    var textColor by remember { mutableStateOf(playerPreferences.getSubtitleTextColor()) }
    var backgroundColor by remember { mutableStateOf(playerPreferences.getSubtitleBackgroundColor()) }
    var edgeType by remember { mutableStateOf(playerPreferences.getSubtitleEdgeType()) }
    var textOpacityPercent by remember { mutableStateOf(playerPreferences.getSubtitleTextOpacityPercent()) }
    var bottomEdgePercent by remember {
        mutableStateOf(playerPreferences.getSubtitleBottomEdgePositionPercent())
    }
    var topEdgePercent by remember {
        mutableStateOf(playerPreferences.getSubtitleTopEdgePositionPercent())
    }

    Scaffold(
        topBar = {
            topbar(title = stringResource(R.string.subtitle_settings_title), onBackPressed = onBackPressed)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { SectionLabel(stringResource(R.string.subtitle_settings_section_style)) }
            item {
                SettingsSection {
                    DropdownSettingsItem(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.subtitle_settings_text_size),
                        subtitle = textSize,
                        options = PlayerPreferences.SUBTITLE_TEXT_SIZE_OPTIONS,
                        onOptionSelected = { selected ->
                            textSize = selected
                            playerPreferences.setSubtitleTextSize(selected)
                        },
                        accentColor = subtitleAccent
                    )

                    SettingsDivider()
                    DropdownSettingsItem(
                        icon = Icons.Rounded.SortByAlpha,
                        title = stringResource(R.string.subtitle_settings_text_color),
                        subtitle = textColor,
                        options = PlayerPreferences.SUBTITLE_TEXT_COLOR_OPTIONS,
                        onOptionSelected = { selected ->
                            textColor = selected
                            playerPreferences.setSubtitleTextColor(selected)
                        },
                        accentColor = subtitleAccent
                    )

                    SettingsDivider()
                    DropdownSettingsItem(
                        icon = Icons.Rounded.Brush,
                        title = stringResource(R.string.subtitle_settings_background_color),
                        subtitle = backgroundColor,
                        options = PlayerPreferences.SUBTITLE_BACKGROUND_OPTIONS,
                        onOptionSelected = { selected ->
                            backgroundColor = selected
                            playerPreferences.setSubtitleBackgroundColor(selected)
                        },
                        accentColor = subtitleAccent
                    )

                    SettingsDivider()
                    DropdownSettingsItem(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.subtitle_settings_edge_type),
                        subtitle = edgeType,
                        options = PlayerPreferences.SUBTITLE_EDGE_TYPE_OPTIONS,
                        onOptionSelected = { selected ->
                            edgeType = selected
                            playerPreferences.setSubtitleEdgeType(selected)
                        },
                        accentColor = subtitleAccent
                    )

                    SettingsDivider()
                    PercentageSliderSettingsItem(
                        icon = Icons.Rounded.SortByAlpha,
                        title = stringResource(R.string.subtitle_settings_text_opacity),
                        subtitle = stringResource(R.string.subtitle_settings_text_opacity_summary),
                        value = textOpacityPercent,
                        defaultValue = PlayerPreferences.DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT,
                        minValue = 0,
                        maxValue = 100,
                        onValueChanged = { updated ->
                            textOpacityPercent = updated
                            playerPreferences.setSubtitleTextOpacityPercent(updated)
                        },
                        accentColor = subtitleAccent
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.subtitle_settings_section_position)) }
            item {
                SettingsSection {
                    PercentageSliderSettingsItem(
                        icon = Icons.Rounded.Fullscreen,
                        title = stringResource(R.string.subtitle_settings_bottom_edge_position),
                        subtitle = stringResource(R.string.subtitle_settings_bottom_edge_position_summary),
                        value = bottomEdgePercent,
                        defaultValue = PlayerPreferences.DEFAULT_SUBTITLE_BOTTOM_EDGE_PERCENT,
                        onValueChanged = { updated ->
                            bottomEdgePercent = updated
                            playerPreferences.setSubtitleBottomEdgePositionPercent(updated)
                        },
                        accentColor = positionAccent
                    )

                    SettingsDivider()
                    PercentageSliderSettingsItem(
                        icon = Icons.Rounded.Fullscreen,
                        title = stringResource(R.string.subtitle_settings_top_edge_position),
                        subtitle = stringResource(R.string.subtitle_settings_top_edge_position_summary),
                        value = topEdgePercent,
                        defaultValue = PlayerPreferences.DEFAULT_SUBTITLE_TOP_EDGE_PERCENT,
                        onValueChanged = { updated ->
                            topEdgePercent = updated
                            playerPreferences.setSubtitleTopEdgePositionPercent(updated)
                        },
                        accentColor = positionAccent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun topbar(
    title: String,
    onBackPressed: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back_button)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SettingsSection(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        accentColor = accentColor,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor,
                    checkedBorderColor = accentColor,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.7f),
                    disabledCheckedTrackColor = accentColor.copy(alpha = 0.45f),
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            )
        }
    )
}

@Composable
private fun ClickableSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        isDestructive = isDestructive,
        accentColor = accentColor,
        onClick = if (enabled) onClick else null,
        trailing = {
            if (enabled) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}

@Composable
private fun DropdownSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<String>,
    optionLabel: @Composable (String) -> String = { it },
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    var expanded by remember { mutableStateOf(false) }

    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        accentColor = accentColor,
        onClick = if (enabled) ({ expanded = true }) else null,
        trailing = {
            Box {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(optionLabel(option)) },
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun BaseSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null && enabled) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = when {
                        isDestructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        else -> accentColor.copy(alpha = 0.16f)
                    },
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isDestructive -> MaterialTheme.colorScheme.error
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else -> accentColor
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    isDestructive -> MaterialTheme.colorScheme.error
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValueSliderSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Int,
    defaultValue: Int,
    minValue: Int,
    maxValue: Int,
    stepSize: Int = 1,
    onValueChanged: (Int) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    valueLabel: @Composable (Int) -> String,
    defaultLabel: @Composable (Int) -> String
) {
    val safeMin = minValue
    val safeMax = maxValue.coerceAtLeast(minValue + 1)
    val safeStepSize = stepSize.coerceAtLeast(1)
    val valueRange = safeMin..safeMax
    val sliderSteps = (((safeMax - safeMin) / safeStepSize) - 1).coerceAtLeast(0)
    val safeValue = value.coerceIn(valueRange.first, valueRange.last)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Text(
                text = valueLabel(safeValue),
                style = MaterialTheme.typography.titleSmall,
                color = accentColor
            )
        }

        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .graphicsLayer(scaleY = 0.75f),
            value = safeValue.toFloat(),
            onValueChange = { changed ->
                val steppedValue = (
                    ((changed - safeMin) / safeStepSize).roundToInt() * safeStepSize + safeMin
                ).coerceIn(valueRange.first, valueRange.last)
                onValueChanged(steppedValue)
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = sliderSteps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.25f),
                activeTickColor = accentColor.copy(alpha = 0.4f),
                inactiveTickColor = accentColor.copy(alpha = 0.4f)
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(18.dp)
                        .background(
                            color = accentColor,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = valueLabel(valueRange.first),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = defaultLabel(defaultValue),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                modifier = Modifier.clickable {
                    onValueChanged(defaultValue.coerceIn(valueRange.first, valueRange.last))
                }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = valueLabel(valueRange.last),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PercentageSliderSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Int,
    defaultValue: Int,
    minValue: Int = 0,
    maxValue: Int = 50,
    stepSize: Int = 5,
    onValueChanged: (Int) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    ValueSliderSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        value = value,
        defaultValue = defaultValue,
        minValue = minValue,
        maxValue = maxValue,
        stepSize = stepSize,
        onValueChanged = onValueChanged,
        accentColor = accentColor,
        valueLabel = { currentValue ->
            stringResource(R.string.player_settings_percent_value, currentValue)
        },
        defaultLabel = { currentValue ->
            stringResource(R.string.player_settings_default_percent, currentValue)
        }
    )
}


@Preview(showBackground = true)
@Composable
fun PlayerSettingsScreenPreview() {
    PlayerSettingsScreen()
}

@Composable
private fun decoderPriorityLabel(value: String): String {
    return when (value) {
        "Hardware First" -> stringResource(R.string.player_settings_decoder_priority_hardware_first)
        "Software First" -> stringResource(R.string.player_settings_decoder_priority_software_first)
        else -> stringResource(R.string.settings_auto)
    }
}
