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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.player.audio.ExternalAudioDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: PlayerSettingsViewModel = viewModel { PlayerSettingsViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    val decodingColor = Color(0xFF3B82F6)
    val audioColor = Color(0xFF14B8A6)
    val videoColor = Color(0xFFF97316)
    val performanceColor = Color(0xFF22C55E)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Player Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
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
            item { SectionLabel("Decoding") }
            item {
                SettingsSection {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Speed,
                        title = "Hardware Acceleration",
                        subtitle = "Use GPU for video acceleration (recommended)",
                        checked = uiState.hardwareDecodingEnabled,
                        onCheckedChange = viewModel::setHardwareDecodingEnabled,
                        accentColor = decodingColor
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SettingsDivider()
                        SwitchSettingsItem(
                            icon = Icons.Rounded.Tune,
                            title = "Asynchronous MediaCodec",
                            subtitle = "Enhanced performance for high-resolution content",
                            checked = uiState.asyncMediaCodecEnabled,
                            onCheckedChange = viewModel::setAsyncMediaCodecEnabled,
                            enabled = uiState.hardwareDecodingEnabled,
                            accentColor = decodingColor
                        )
                    }

                    SettingsDivider()
                    ClickableSettingsItem(
                        icon = Icons.Rounded.Info,
                        title = "Hardware Status",
                        subtitle = uiState.hardwareStatus ?: "Detecting...",
                        onClick = { viewModel.refreshHardwareStatus() },
                        accentColor = decodingColor
                    )
                }
            }

            item { SectionLabel("Audio") }
            item {
                SettingsSection {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.SurroundSound,
                        title = "Spatial Audio",
                        subtitle = "Enhanced audio experience with compatible content",
                        checked = uiState.spatialAudioEnabled,
                        onCheckedChange = viewModel::setSpatialAudioEnabled,
                        enabled = uiState.spatialAudioSupported,
                        accentColor = audioColor
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsDivider()
                        SwitchSettingsItem(
                            icon = Icons.Rounded.Headphones,
                            title = "Head Tracking",
                            subtitle = "Follow head movements for immersive audio",
                            checked = uiState.headTrackingEnabled,
                            onCheckedChange = viewModel::setHeadTrackingEnabled,
                            enabled = uiState.spatialAudioEnabled && uiState.headTrackingSupported,
                            accentColor = audioColor
                        )
                    }
                }
            }

            item { SectionLabel("Video") }
            item {
                SettingsSection {
                    DropdownSettingsItem(
                        icon = Icons.Rounded.VideoSettings,
                        title = "Video Decoder Priority",
                        subtitle = uiState.decoderPriority,
                        options = listOf("Hardware First", "Software First", "Auto"),
                        onOptionSelected = viewModel::setDecoderPriority,
                        accentColor = videoColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Fullscreen,
                        title = "Start Maximized",
                        subtitle = "Start video in fullscreen mode",
                        checked = uiState.startMaximized,
                        onCheckedChange = viewModel::setStartMaximized,
                        accentColor = videoColor
                    )
                }
            }

            item { SectionLabel("Performance") }
            item {
                SettingsSection {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Memory,
                        title = "Buffer Optimization",
                        subtitle = "Optimize buffering for better playback",
                        checked = uiState.bufferOptimizationEnabled,
                        onCheckedChange = viewModel::setBufferOptimizationEnabled,
                        accentColor = performanceColor
                    )

                    SettingsDivider()
                    SwitchSettingsItem(
                        icon = Icons.Rounded.BatteryStd,
                        title = "Battery Optimization",
                        subtitle = "Reduce power consumption during playback",
                        checked = uiState.batteryOptimizationEnabled,
                        onCheckedChange = viewModel::setBatteryOptimizationEnabled,
                        accentColor = performanceColor
                    )
                }
            }

            if (uiState.externalAudioDevices.isNotEmpty()) {
                item { SectionLabel("External Audio") }
                item {
                    SettingsSection {
                        uiState.externalAudioDevices.forEach { device ->
                            ExternalAudioDeviceItem(device = device)
                        }
                    }
                }
            }
        }
    }
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
                            text = { Text(option) },
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

@Composable
private fun ExternalAudioDeviceItem(
    device: ExternalAudioDevice
) {
    val deviceAccent = when (device.type) {
        "Bluetooth" -> Color(0xFF3B82F6)
        "USB" -> Color(0xFFF59E0B)
        "HDMI" -> Color(0xFFEF4444)
        "Wired" -> Color(0xFF14B8A6)
        else -> MaterialTheme.colorScheme.primary
    }

    Column {
        BaseSettingsItem(
            icon = when (device.type) {
                "Bluetooth" -> Icons.Rounded.Bluetooth
                "USB" -> Icons.Rounded.Usb
                "HDMI" -> Icons.Rounded.Tv
                "Wired" -> Icons.Rounded.Headphones
                else -> Icons.Rounded.AudioFile
            },
            title = device.name,
            subtitle = "${device.type} - ${device.maxChannels} channels${if (device.supportsHighRes) " - Hi-Res" else ""}",
            enabled = false,
            accentColor = deviceAccent
        )

        if (device.supportedCodecs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Codecs: ${device.supportedCodecs.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (device.sampleRates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Sample Rates: ${device.sampleRates.take(5).joinToString(", ")} Hz${if (device.sampleRates.size > 5) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun PlayerSettingsScreenPreview() {
    PlayerSettingsScreen()
}
