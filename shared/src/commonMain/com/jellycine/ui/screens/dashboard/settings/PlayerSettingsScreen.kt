package com.jellycine.ui.screens.dashboard.settings

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.resources.*
import com.jellycine.ui.components.common.SelectionOption
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerSettingsScreen(
    viewModel: PlayerSettingsViewModel,
    showPlaybackEngineSettings: Boolean = true,
    showAsyncMediaCodec: Boolean = true,
    hardwareDecodingOptions: List<SelectionOption> = AndroidHardwareDecodingOptions,
    audioOutputOptions: List<SelectionOption> = AndroidAudioOutputOptions,
    onBackPressed: () -> Unit = {},
    onNavigateToSubtitleSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val decodingColor = Color(0xFF3B82F6)
    val transcodingColor = Color(0xFF8B5CF6)
    val videoColor = Color(0xFFF97316)
    val gesturesColor = Color(0xFF14B8A6)
    val seekingColor = Color(0xFFEF4444)
    val performanceColor = Color(0xFF22C55E)
    val cacheColor = Color(0xFF06B6D4)
    val mpvActive = !showPlaybackEngineSettings || uiState.playerEngine == PlayerPreferences.PLAYER_ENGINE_MPV

    BackHandler { onBackPressed() }

    Scaffold(
        topBar = {
            topbar(
                title = stringResource(Res.string.player_settings_title),
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
            item { SectionLabel(stringResource(Res.string.player_settings_section_player)) }
            item {
                SettingsSection {
                    if (showPlaybackEngineSettings) SelectionDialogSettingsItem(
                        icon = Icons.Rounded.VideoSettings,
                        title = stringResource(Res.string.player_settings_player_engine),
                        subtitle = uiState.playerEngine,
                        selectedValue = uiState.playerEngine,
                        options = listOf(
                            SelectionOption("ExoPlayer", "ExoPlayer", "Android native, battery efficient", isDefault = true),
                            SelectionOption("MPV", "MPV", "Advanced rendering, custom shaders, better HDR")
                        ),
                        onOptionSelected = viewModel::setPlayerEngine,
                        accentColor = videoColor
                    )

                    if (mpvActive) {
                        if (showPlaybackEngineSettings) SettingsDivider()
                        SelectionDialogSettingsItem(
                            icon = Icons.Rounded.Speed,
                            title = stringResource(Res.string.player_settings_mpv_hardware_decoding),
                            subtitle = hardwareDecodingOptions.labelFor(uiState.mpvHardwareDecoding),
                            selectedValue = hardwareDecodingOptions.valueOrDefault(uiState.mpvHardwareDecoding),
                            options = hardwareDecodingOptions,
                            onOptionSelected = viewModel::setMpvHardwareDecoding,
                            accentColor = videoColor
                        )

                        SettingsDivider()
                        SelectionDialogSettingsItem(
                            icon = Icons.Rounded.VideoSettings,
                            title = stringResource(Res.string.player_settings_mpv_video_output),
                            subtitle = uiState.mpvVideoOutput,
                            selectedValue = uiState.mpvVideoOutput,
                            options = listOf(
                                SelectionOption("gpu-next", "GPU Next", "Modern libplacebo renderer, best quality", isDefault = true),
                                SelectionOption("gpu", "GPU", "Legacy GPU renderer, wider device support")
                            ),
                            onOptionSelected = viewModel::setMpvVideoOutput,
                            accentColor = videoColor
                        )

                        if (audioOutputOptions.size > 1) {
                        SettingsDivider()
                        SelectionDialogSettingsItem(
                            icon = Icons.Rounded.AudioFile,
                            title = stringResource(Res.string.player_settings_mpv_audio_output),
                            subtitle = audioOutputOptions.labelFor(uiState.mpvAudioOutput),
                            selectedValue = audioOutputOptions.valueOrDefault(uiState.mpvAudioOutput),
                            options = audioOutputOptions,
                            onOptionSelected = viewModel::setMpvAudioOutput,
                            accentColor = videoColor
                        )
                        }
                    }
                }
            }

            if (mpvActive) {
                val renderingColor = Color(0xFFF59E0B)
                item { SectionLabel("RENDERING") }
                item {
                    SettingsSection {
                        SelectionDialogSettingsItem(
                            icon = Icons.Rounded.Tune,
                            title = "Upscale Filter",
                            subtitle = uiState.mpvUpscaleFilter,
                            selectedValue = uiState.mpvUpscaleFilter,
                            options = listOf(
                                SelectionOption("bilinear", "Bilinear", "Fast, smooth, slightly soft"),
                                SelectionOption("spline36", "Spline36", "Balanced sharpness and smoothness"),
                                SelectionOption("lanczos", "Lanczos", "Sharp and detailed", isDefault = true),
                                SelectionOption("ewa_lanczos", "EWA Lanczos", "Sharpest quality, GPU intensive")
                            ),
                            onOptionSelected = viewModel::setMpvUpscaleFilter,
                            accentColor = renderingColor
                        )

                        SettingsDivider()
                        SelectionDialogSettingsItem(
                            icon = Icons.Rounded.Tune,
                            title = "Downscale Filter",
                            subtitle = uiState.mpvDownscaleFilter,
                            selectedValue = uiState.mpvDownscaleFilter,
                            options = listOf(
                                SelectionOption("hermite", "Hermite", "Smooth and soft, reduces aliasing", isDefault = true),
                                SelectionOption("mitchell", "Mitchell", "Balanced, slight sharpness"),
                                SelectionOption("catmull_rom", "Catmull-Rom", "Sharper, preserves edges"),
                                SelectionOption("lanczos", "Lanczos", "Sharpest downscale, may ring slightly")
                            ),
                            onOptionSelected = viewModel::setMpvDownscaleFilter,
                            accentColor = renderingColor
                        )

                        SettingsDivider()
                        SelectionDialogSettingsItem(
                            icon = Icons.Rounded.Contrast,
                            title = "Tone Mapping",
                            subtitle = uiState.mpvToneMapping,
                            selectedValue = uiState.mpvToneMapping,
                            options = listOf(
                                SelectionOption("auto", "Auto", "Dynamically picks best algorithm per scene", isDefault = true),
                                SelectionOption("bt.2390", "BT.2390", "Broadcast standard, natural highlights"),
                                SelectionOption("spline", "Spline", "Smooth curve, preserves mid-tones"),
                                SelectionOption("hable", "Filmic", "Cinematic roll-off, softer highlights"),
                                SelectionOption("mobius", "Mobius", "Soft roll-off, preserves dark detail"),
                                SelectionOption("reinhard", "Reinhard", "Simple, slightly desaturated highlights")
                            ),
                            onOptionSelected = viewModel::setMpvToneMapping,
                            enabled = uiState.mpvHdrToSdrTonemapping,
                            accentColor = renderingColor
                        )

                        SettingsDivider()
                        SwitchSettingsItem(
                            icon = Icons.Rounded.SlowMotionVideo,
                            title = "Smooth Motion",
                            subtitle = "Interpolate frames on high refresh displays",
                            checked = uiState.mpvSmoothMotion,
                            onCheckedChange = viewModel::setMpvSmoothMotion,
                            accentColor = renderingColor
                        )

                        SettingsDivider()
                        SwitchSettingsItem(
                            icon = Icons.Rounded.Gradient,
                            title = "Deband",
                            subtitle = "Reduce color banding in dark gradients",
                            checked = uiState.mpvDeband,
                            onCheckedChange = viewModel::setMpvDeband,
                            accentColor = renderingColor
                        )

                        SettingsDivider()
                        SwitchSettingsItem(
                            icon = Icons.Rounded.WbSunny,
                            title = "Dynamic Brightness",
                            subtitle = "Adapt tone mapping per scene",
                            checked = uiState.mpvDynamicPeak,
                            onCheckedChange = viewModel::setMpvDynamicPeak,
                            enabled = uiState.mpvHdrToSdrTonemapping,
                            accentColor = renderingColor
                        )
                        SettingsDivider()
                        SwitchSettingsItem(
                            icon = Icons.Rounded.HdrOn,
                            title = "HDR to SDR Tonemapping",
                            subtitle = "Tone map HDR content to SDR for accurate colors",
                            checked = uiState.mpvHdrToSdrTonemapping,
                            onCheckedChange = viewModel::setMpvHdrToSdrTonemapping,
                            accentColor = renderingColor
                        )
                    }
                }
            }

            if (showPlaybackEngineSettings) {
                item { SectionLabel(stringResource(Res.string.player_settings_section_decoding)) }
                item {
                    SettingsSection {
                        SwitchSettingsItem(
                            icon = Icons.Rounded.Speed,
                            title = stringResource(Res.string.player_settings_hardware_acceleration),
                            subtitle = stringResource(Res.string.player_settings_hardware_acceleration_summary),
                            checked = uiState.hardwareDecodingEnabled,
                            onCheckedChange = viewModel::setHardwareDecodingEnabled,
                            accentColor = decodingColor
                        )

                        if (showAsyncMediaCodec) {
                            SettingsDivider()
                            SwitchSettingsItem(
                                icon = Icons.Rounded.Tune,
                                title = stringResource(Res.string.player_settings_async_mediacodec),
                                subtitle = stringResource(Res.string.player_settings_async_mediacodec_summary),
                                checked = uiState.asyncMediaCodecEnabled,
                                onCheckedChange = viewModel::setAsyncMediaCodecEnabled,
                                enabled = uiState.hardwareDecodingEnabled,
                                accentColor = decodingColor
                            )
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(Res.string.player_settings_section_subtitles)) }
            item {
                SettingsSection {
                    ClickableSettingsItem(
                        icon = Icons.Rounded.VideoSettings,
                        title = stringResource(Res.string.player_settings_subtitles),
                        subtitle = stringResource(Res.string.player_settings_subtitles_summary),
                        onClick = onNavigateToSubtitleSettings,
                        accentColor = Color(0xFF6366F1)
                    )
                }
            }

            if (uiState.isVideoTranscodingAllowed || uiState.isAudioTranscodingAllowed) {
                item { SectionLabel(stringResource(Res.string.player_settings_section_transcoding)) }
                item {
                    SettingsSection {
                        if (uiState.isVideoTranscodingAllowed) {
                            SelectionDialogSettingsItem(
                                icon = Icons.Rounded.HighQuality,
                                title = stringResource(Res.string.player_settings_streaming_quality),
                                subtitle = uiState.streamingQuality,
                                selectedValue = uiState.streamingQuality,
                                options = PlayerPreferences.STREAMING_QUALITY_OPTIONS.map { quality ->
                                    SelectionOption(
                                        value = quality,
                                        label = quality,
                                        description = "",
                                        isDefault = quality == PlayerPreferences.DEFAULT_STREAMING_QUALITY
                                    )
                                },
                                onOptionSelected = viewModel::setStreamingQuality,
                                accentColor = transcodingColor
                            )
                        }

                        if (uiState.isVideoTranscodingAllowed && uiState.isAudioTranscodingAllowed) {
                            SettingsDivider()
                        }

                        if (uiState.isAudioTranscodingAllowed) {
                            SelectionDialogSettingsItem(
                                icon = Icons.Rounded.AudioFile,
                                title = stringResource(Res.string.player_settings_audio_quality),
                                subtitle = uiState.audioTranscodeMode,
                                selectedValue = uiState.audioTranscodeMode,
                                options = listOf(
                                    SelectionOption("Auto", "Auto", "Server decides based on client capability", isDefault = true),
                                    SelectionOption("Stereo", "Stereo", "2-channel audio, most compatible"),
                                    SelectionOption("5.1 Surround", "5.1 Surround", "6-channel surround sound"),
                                    SelectionOption("Passthrough", "Passthrough", "Original audio, no transcoding")
                                ),
                                onOptionSelected = viewModel::setAudioTranscodeMode,
                                accentColor = transcodingColor
                            )
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(Res.string.player_settings_section_video)) }
            item {
                SettingsSection {
                    if (showPlaybackEngineSettings) {
                        SelectionDialogSettingsItem(
                            icon = Icons.Rounded.VideoSettings,
                            title = stringResource(Res.string.player_settings_decoder_priority),
                            subtitle = decoderPriorityLabel(uiState.decoderPriority),
                            selectedValue = uiState.decoderPriority,
                            options = listOf(
                                SelectionOption(PlayerPreferences.DECODER_PRIORITY_AUTO, "Auto", "Let the player decide based on content", isDefault = true),
                                SelectionOption(PlayerPreferences.DECODER_PRIORITY_HARDWARE, "Hardware First", "Prefer GPU decoding, lower battery usage"),
                                SelectionOption(PlayerPreferences.DECODER_PRIORITY_SOFTWARE, "Software First", "Prefer CPU decoding, wider codec support")
                            ),
                            onOptionSelected = viewModel::setDecoderPriority,
                            accentColor = videoColor
                        )

                        SettingsDivider()
                    }
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Fullscreen,
                        title = stringResource(Res.string.player_settings_start_maximized),
                        subtitle = stringResource(Res.string.player_settings_start_maximized_summary),
                        checked = uiState.startMaximized,
                        onCheckedChange = viewModel::setStartMaximized,
                        accentColor = videoColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Devices,
                        title = stringResource(Res.string.player_settings_use_device_volume_in_player),
                        subtitle = stringResource(Res.string.player_settings_use_device_volume_in_player_summary),
                        checked = uiState.useDeviceVolumeInPlayer,
                        onCheckedChange = viewModel::setUseDeviceVolumeInPlayer,
                        accentColor = videoColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.DisplaySettings,
                        title = stringResource(Res.string.player_settings_use_device_brightness_in_player),
                        subtitle = stringResource(Res.string.player_settings_use_device_brightness_in_player_summary),
                        checked = uiState.useDeviceBrightnessInPlayer,
                        onCheckedChange = viewModel::setUseDeviceBrightnessInPlayer,
                        accentColor = videoColor
                    )
                }
            }

            item { SectionLabel(stringResource(Res.string.player_settings_section_gestures)) }
            item {
                SettingsSection {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(Res.string.player_settings_gestures),
                        subtitle = stringResource(Res.string.player_settings_gestures_summary),
                        checked = uiState.playerGesturesEnabled,
                        onCheckedChange = viewModel::setPlayerGesturesEnabled,
                        accentColor = gesturesColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Brush,
                        title = stringResource(Res.string.player_settings_volume_brightness_gestures),
                        subtitle = stringResource(Res.string.player_settings_volume_brightness_gestures_summary),
                        checked = uiState.volumeBrightnessGesturesEnabled,
                        onCheckedChange = viewModel::setVolumeBrightnessGesturesEnabled,
                        enabled = uiState.playerGesturesEnabled,
                        accentColor = gesturesColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.FastForward,
                        title = stringResource(Res.string.player_settings_progress_seek_gesture),
                        subtitle = stringResource(Res.string.player_settings_progress_seek_gesture_summary),
                        checked = uiState.progressSeekGestureEnabled,
                        onCheckedChange = viewModel::setProgressSeekGestureEnabled,
                        enabled = uiState.playerGesturesEnabled,
                        accentColor = gesturesColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Fullscreen,
                        title = stringResource(Res.string.player_settings_zoom_gesture),
                        subtitle = stringResource(Res.string.player_settings_zoom_gesture_summary),
                        checked = uiState.zoomGestureEnabled,
                        onCheckedChange = viewModel::setZoomGestureEnabled,
                        enabled = uiState.playerGesturesEnabled,
                        accentColor = gesturesColor
                    )

                }
            }

            item { SectionLabel(stringResource(Res.string.player_settings_section_seeking)) }
            item {
                SettingsSection {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.SkipNext,
                        title = stringResource(Res.string.player_settings_skip_intro),
                        subtitle = stringResource(Res.string.player_settings_skip_intro_summary),
                        checked = uiState.skipIntroEnabled,
                        onCheckedChange = viewModel::setSkipIntroEnabled,
                        accentColor = seekingColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(Res.string.player_settings_chapter_markers),
                        subtitle = stringResource(Res.string.player_settings_chapter_markers_summary),
                        checked = uiState.chapterMarkersEnabled,
                        onCheckedChange = viewModel::setChapterMarkersEnabled,
                        accentColor = seekingColor
                    )

                    SettingsDivider()
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.FastRewind,
                        title = stringResource(Res.string.player_settings_seek_backward),
                        subtitle = stringResource(
                            Res.string.player_settings_seek_interval_value,
                            uiState.seekBackwardIntervalSeconds
                        ),
                        selectedValue = uiState.seekBackwardIntervalSeconds.toString(),
                        options = (PlayerPreferences.MIN_SEEK_INTERVAL_SECONDS..PlayerPreferences.MAX_SEEK_INTERVAL_SECONDS step PlayerPreferences.SEEK_INTERVAL_STEP_SECONDS)
                            .map { seconds ->
                                SelectionOption(
                                    value = seconds.toString(),
                                    label = "${seconds}s",
                                    description = "",
                                    isDefault = seconds == PlayerPreferences.DEFAULT_SEEK_INTERVAL_SECONDS
                                )
                            },
                        onOptionSelected = { seconds ->
                            viewModel.setSeekBackwardIntervalSeconds(seconds.toInt())
                        },
                        accentColor = seekingColor
                    )

                    SettingsDivider()
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.FastForward,
                        title = stringResource(Res.string.player_settings_seek_forward),
                        subtitle = stringResource(
                            Res.string.player_settings_seek_interval_value,
                            uiState.seekForwardIntervalSeconds
                        ),
                        selectedValue = uiState.seekForwardIntervalSeconds.toString(),
                        options = (PlayerPreferences.MIN_SEEK_INTERVAL_SECONDS..PlayerPreferences.MAX_SEEK_INTERVAL_SECONDS step PlayerPreferences.SEEK_INTERVAL_STEP_SECONDS)
                            .map { seconds ->
                                SelectionOption(
                                    value = seconds.toString(),
                                    label = "${seconds}s",
                                    description = "",
                                    isDefault = seconds == PlayerPreferences.DEFAULT_SEEK_INTERVAL_SECONDS
                                )
                            },
                        onOptionSelected = { seconds ->
                            viewModel.setSeekForwardIntervalSeconds(seconds.toInt())
                        },
                        accentColor = seekingColor
                    )
                }
            }

            item { SectionLabel(stringResource(Res.string.player_settings_section_player_cache)) }
            item {
                SettingsSection {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.SkipNext,
                        title = stringResource(Res.string.cache_settings_cache_next_episode),
                        subtitle = stringResource(Res.string.cache_settings_cache_next_episode_summary),
                        checked = uiState.cacheNextEpisodeEnabled,
                        onCheckedChange = viewModel::setCacheNextEpisodeEnabled,
                        accentColor = cacheColor
                    )

                    SettingsDivider()
                    ValueSliderSettingsItem(
                        icon = Icons.Rounded.Storage,
                        title = stringResource(Res.string.player_settings_player_cache_size),
                        subtitle = stringResource(Res.string.player_settings_player_cache_size_summary),
                        value = uiState.playerCacheSizeMb,
                        defaultValue = PlayerPreferences.DEFAULT_PLAYER_CACHE_SIZE_MB,
                        minValue = PlayerPreferences.MIN_PLAYER_CACHE_SIZE_MB,
                        maxValue = PlayerPreferences.MAX_PLAYER_CACHE_SIZE_MB,
                        stepSize = PlayerPreferences.PLAYER_CACHE_SIZE_STEP_MB,
                        onValueChanged = viewModel::setPlayerCacheSizeMb,
                        valueLabel = { sizeMb ->
                            stringResource(Res.string.player_settings_player_cache_size_value, sizeMb)
                        },
                        defaultLabel = { sizeMb ->
                            stringResource(
                                Res.string.player_settings_default_value,
                                stringResource(Res.string.player_settings_player_cache_size_value, sizeMb)
                            )
                        },
                        accentColor = cacheColor
                    )

                    SettingsDivider()
                    ValueSliderSettingsItem(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(Res.string.player_settings_player_cache_time),
                        subtitle = stringResource(Res.string.player_settings_player_cache_time_summary),
                        value = uiState.playerCacheTimeSeconds,
                        defaultValue = PlayerPreferences.DEFAULT_PLAYER_CACHE_TIME_SECONDS,
                        minValue = PlayerPreferences.MIN_PLAYER_CACHE_TIME_SECONDS,
                        maxValue = PlayerPreferences.MAX_PLAYER_CACHE_TIME_SECONDS,
                        stepSize = PlayerPreferences.PLAYER_CACHE_TIME_STEP_SECONDS,
                        onValueChanged = viewModel::setPlayerCacheTimeSeconds,
                        valueLabel = { seconds ->
                            stringResource(Res.string.player_settings_player_cache_time_value, seconds)
                        },
                        defaultLabel = { seconds ->
                            stringResource(
                                Res.string.player_settings_default_value,
                                stringResource(Res.string.player_settings_player_cache_time_value, seconds)
                            )
                        },
                        accentColor = cacheColor
                    )
                }
            }

            if (showPlaybackEngineSettings) {
                item { SectionLabel(stringResource(Res.string.player_settings_section_performance)) }
                item {
                    SettingsSection {
                        SwitchSettingsItem(
                            icon = Icons.Rounded.BatteryStd,
                            title = stringResource(Res.string.player_settings_battery_optimization),
                            subtitle = stringResource(Res.string.player_settings_battery_optimization_summary),
                            checked = uiState.batteryOptimizationEnabled,
                            onCheckedChange = viewModel::setBatteryOptimizationEnabled,
                            accentColor = performanceColor
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SubtitleSettingsScreen(
    playerPreferences: PlayerPreferences,
    onBackPressed: () -> Unit = {}
) {
    val subtitleAccent = Color(0xFF6366F1)
    val positionAccent = Color(0xFF0EA5E9)

    var textSize by remember { mutableStateOf(playerPreferences.getSubtitleTextSize()) }
    var textColor by remember { mutableStateOf(playerPreferences.getSubtitleTextColor()) }
    var backgroundColor by remember { mutableStateOf(playerPreferences.getSubtitleBackgroundColor()) }
    var edgeType by remember { mutableStateOf(playerPreferences.getSubtitleEdgeType()) }
    var textOpacityPercent by remember { mutableStateOf(playerPreferences.getSubtitleTextOpacityPercent()) }
    var bottomEdgePercent by remember {
        mutableStateOf(playerPreferences.getSubtitlePosition())
    }
    var topEdgePercent by remember {
        mutableStateOf(playerPreferences.getSubtitleTopEdgePositionPercent())
    }

    BackHandler { onBackPressed() }

    Scaffold(
        topBar = {
            topbar(title = stringResource(Res.string.subtitle_settings_title), onBackPressed = onBackPressed)
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
            item { SectionLabel(stringResource(Res.string.subtitle_settings_section_style)) }
            item {
                SettingsSection {
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(Res.string.subtitle_settings_text_size),
                        subtitle = textSize,
                        selectedValue = textSize,
                        options = listOf(
                            SelectionOption("Small", "Small", "Compact text, more screen space"),
                            SelectionOption("Normal", "Normal", "Standard readable size", isDefault = true),
                            SelectionOption("Large", "Large", "Bigger text, easier to read"),
                            SelectionOption("Extra Large", "Extra Large", "Maximum readability")
                        ),
                        onOptionSelected = { selected ->
                            textSize = selected
                            playerPreferences.setSubtitleTextSize(selected)
                        },
                        accentColor = subtitleAccent
                    )

                    SettingsDivider()
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.SortByAlpha,
                        title = stringResource(Res.string.subtitle_settings_text_color),
                        subtitle = textColor,
                        selectedValue = textColor,
                        options = listOf(
                            SelectionOption("White", "White", "Standard, works on most backgrounds", isDefault = true),
                            SelectionOption("Yellow", "Yellow", "High contrast on dark scenes"),
                            SelectionOption("Green", "Green", "Distinct color for visibility"),
                            SelectionOption("Cyan", "Cyan", "Cool tone, easy on eyes")
                        ),
                        onOptionSelected = { selected ->
                            textColor = selected
                            playerPreferences.setSubtitleTextColor(selected)
                        },
                        accentColor = subtitleAccent
                    )

                    SettingsDivider()
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.Brush,
                        title = stringResource(Res.string.subtitle_settings_background_color),
                        subtitle = backgroundColor,
                        selectedValue = backgroundColor,
                        options = listOf(
                            SelectionOption("Transparent", "Transparent", "No background, clean look", isDefault = true),
                            SelectionOption("Black", "Black", "Dark box behind text for contrast"),
                            SelectionOption("White", "White", "Light box behind text")
                        ),
                        onOptionSelected = { selected ->
                            backgroundColor = selected
                            playerPreferences.setSubtitleBackgroundColor(selected)
                        },
                        accentColor = subtitleAccent
                    )

                    SettingsDivider()
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(Res.string.subtitle_settings_edge_type),
                        subtitle = edgeType,
                        selectedValue = edgeType,
                        options = listOf(
                            SelectionOption("None", "None", "No edge effect", isDefault = true),
                            SelectionOption("Outline", "Outline", "Border around text for readability"),
                            SelectionOption("Drop Shadow", "Drop Shadow", "Shadow below text for depth"),
                            SelectionOption("Raised", "Raised", "Embossed 3D effect"),
                            SelectionOption("Depressed", "Depressed", "Engraved 3D effect")
                        ),
                        onOptionSelected = { selected ->
                            edgeType = selected
                            playerPreferences.setSubtitleEdgeType(selected)
                        },
                        accentColor = subtitleAccent
                    )

                    SettingsDivider()
                    PercentageSliderSettingsItem(
                        icon = Icons.Rounded.SortByAlpha,
                        title = stringResource(Res.string.subtitle_settings_text_opacity),
                        subtitle = stringResource(Res.string.subtitle_settings_text_opacity_summary),
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

            item { SectionLabel(stringResource(Res.string.subtitle_settings_section_position)) }
            item {
                SettingsSection {
                    PercentageSliderSettingsItem(
                        icon = Icons.Rounded.Fullscreen,
                        title = stringResource(Res.string.subtitle_settings_bottom_edge_position),
                        subtitle = stringResource(Res.string.subtitle_settings_bottom_edge_position_summary),
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
                        title = stringResource(Res.string.subtitle_settings_top_edge_position),
                        subtitle = stringResource(Res.string.subtitle_settings_top_edge_position_summary),
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


@Composable
private fun decoderPriorityLabel(value: String): String {
    return when (value) {
        PlayerPreferences.DECODER_PRIORITY_HARDWARE -> stringResource(Res.string.player_settings_decoder_priority_hardware_first)
        PlayerPreferences.DECODER_PRIORITY_SOFTWARE -> stringResource(Res.string.player_settings_decoder_priority_software_first)
        else -> stringResource(Res.string.settings_auto)
    }
}

private fun List<SelectionOption>.valueOrDefault(current: String): String =
    firstOrNull { it.value == current }?.value
        ?: firstOrNull { it.isDefault }?.value
        ?: first().value

private fun List<SelectionOption>.labelFor(current: String): String {
    val value = valueOrDefault(current)
    return first { it.value == value }.label
}

val AndroidHardwareDecodingOptions = listOf(
    SelectionOption("mediacodec", "MediaCodec", "Direct hardware decode, best performance", isDefault = true),
    SelectionOption("mediacodec-copy", "MediaCodec (copy)", "Hardware decode with CPU copy, wider format support"),
    SelectionOption("no", "Software", "CPU decode, highest compatibility")
)

val AndroidAudioOutputOptions = listOf(
    SelectionOption("audiotrack", "AudioTrack", "Standard Android audio, most compatible", isDefault = true),
    SelectionOption("aaudio", "AAudio", "Low-latency audio, Android 8.1+"),
    SelectionOption("opensles", "OpenSL ES", "Legacy low-latency audio")
)

val IosHardwareDecodingOptions = listOf(
    SelectionOption("videotoolbox", "VideoToolbox", "Direct hardware decode, best performance", isDefault = true),
    SelectionOption("videotoolbox-copy", "VideoToolbox (copy)", "Hardware decode with CPU copy, wider format support"),
    SelectionOption("no", "Software", "CPU decode, highest compatibility")
)

val IosAudioOutputOptions = listOf(
    SelectionOption("audiounit", "AudioUnit", "Standard iOS audio output", isDefault = true)
)