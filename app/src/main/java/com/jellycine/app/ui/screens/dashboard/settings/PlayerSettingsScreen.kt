package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.player.core.PlayerUtils
import com.jellycine.player.audio.SpatializerHelper
import com.jellycine.player.audio.ExternalAudioDevice
import com.jellycine.player.video.HdrCapabilityManager
import com.jellycine.detail.CodecCapabilityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: PlayerSettingsViewModel = viewModel { PlayerSettingsViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Player Settings",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Hardware Acceleration Section
            item {
                SettingsSection(title = "Hardware Acceleration") {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Speed,
                        title = "Hardware Acceleration",
                        subtitle = "Use GPU for video acceleration (recommended)",
                        checked = uiState.hardwareDecodingEnabled,
                        onCheckedChange = viewModel::setHardwareDecodingEnabled
                    )
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SwitchSettingsItem(
                            icon = Icons.Rounded.Tune,
                            title = "Asynchronous MediaCodec",
                            subtitle = "Enhanced performance for high-resolution content",
                            checked = uiState.asyncMediaCodecEnabled,
                            onCheckedChange = viewModel::setAsyncMediaCodecEnabled,
                            enabled = uiState.hardwareDecodingEnabled
                        )
                    }

                    ClickableSettingsItem(
                        icon = Icons.Rounded.Info,
                        title = "Hardware Status",
                        subtitle = uiState.hardwareStatus ?: "Detecting...",
                        onClick = { viewModel.refreshHardwareStatus() }
                    )
                }
            }

            // Audio Settings Section
            item {
                SettingsSection(title = "Audio") {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.SurroundSound,
                        title = "Spatial Audio",
                        subtitle = "Enhanced audio experience with compatible content",
                        checked = uiState.spatialAudioEnabled,
                        onCheckedChange = viewModel::setSpatialAudioEnabled,
                        enabled = uiState.spatialAudioSupported
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchSettingsItem(
                            icon = Icons.Rounded.Headphones,
                            title = "Head Tracking",
                            subtitle = "Follow head movements for immersive audio",
                            checked = uiState.headTrackingEnabled,
                            onCheckedChange = viewModel::setHeadTrackingEnabled,
                            enabled = uiState.spatialAudioEnabled && uiState.headTrackingSupported
                        )
                    }


                }
            }

            // Video Settings Section
            item {
                SettingsSection(title = "Video") {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.HighQuality,
                        title = "HDR Support",
                        subtitle = "Enable HDR and Dolby Vision content",
                        checked = uiState.hdrEnabled,
                        onCheckedChange = viewModel::setHdrEnabled,
                        enabled = uiState.hdrSupported
                    )

                    DropdownSettingsItem(
                        icon = Icons.Rounded.VideoSettings,
                        title = "Video Decoder Priority",
                        subtitle = uiState.decoderPriority,
                        options = listOf("Hardware First", "Software First", "Auto"),
                        selectedOption = uiState.decoderPriority,
                        onOptionSelected = viewModel::setDecoderPriority
                    )

                    SwitchSettingsItem(
                        icon = Icons.Rounded.Fullscreen,
                        title = "Start Maximized",
                        subtitle = "Start video in fullscreen mode",
                        checked = uiState.startMaximized,
                        onCheckedChange = viewModel::setStartMaximized
                    )
                }
            }

            // Performance Section
            item {
                SettingsSection(title = "Performance") {
                    SwitchSettingsItem(
                        icon = Icons.Rounded.Memory,
                        title = "Buffer Optimization",
                        subtitle = "Optimize buffering for better playback",
                        checked = uiState.bufferOptimizationEnabled,
                        onCheckedChange = viewModel::setBufferOptimizationEnabled
                    )

                    SwitchSettingsItem(
                        icon = Icons.Rounded.BatteryStd,
                        title = "Battery Optimization",
                        subtitle = "Reduce power consumption during playback",
                        checked = uiState.batteryOptimizationEnabled,
                        onCheckedChange = viewModel::setBatteryOptimizationEnabled
                    )
                }
            }

            // Device Information Section
            item {
                SettingsSection(title = "Device Information") {
                    InfoSettingsItem(
                        icon = Icons.Rounded.Smartphone,
                        title = "Device Model",
                        value = "${Build.MANUFACTURER} ${Build.MODEL}"
                    )

                    InfoSettingsItem(
                        icon = Icons.Rounded.Android,
                        title = "Android Version",
                        value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    )

                    InfoSettingsItem(
                        icon = Icons.Rounded.VideoLibrary,
                        title = "Video Codecs",
                        value = uiState.supportedCodecs
                    )
                }
            }
            
            // External Audio Devices Section (only show if devices are connected)
            if (uiState.externalAudioDevices.isNotEmpty()) {
                item {
                    SettingsSection(title = "External Audio Devices") {
                        uiState.externalAudioDevices.forEach { device ->
                            ExternalAudioDeviceItem(
                                device = device
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
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
    isDestructive: Boolean = false
) {
    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        isDestructive = isDestructive,
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
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    
    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = if (enabled) { { expanded = true } } else null,
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
private fun InfoSettingsItem(
    icon: ImageVector,
    title: String,
    value: String
) {
    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = value,
        enabled = false
    )
}

@Composable
private fun BaseSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null && enabled) {
        Modifier.clickable { onClick() }
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with background
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = when {
                        isDestructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
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
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    isDestructive -> MaterialTheme.colorScheme.error
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
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
    Column {
        // Device Name and Type
        BaseSettingsItem(
            icon = when (device.type) {
                "Bluetooth" -> Icons.Rounded.Bluetooth
                "USB" -> Icons.Rounded.Usb
                "HDMI" -> Icons.Rounded.Tv
                "Wired" -> Icons.Rounded.Headphones
                else -> Icons.Rounded.AudioFile
            },
            title = device.name,
            subtitle = "${device.type} • ${device.maxChannels} channels${if (device.supportsHighRes) " • Hi-Res" else ""}",
            enabled = false
        )
        
        // Supported Codecs
        if (device.supportedCodecs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
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
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Sample Rates
        if (device.sampleRates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
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
                    fontSize = 12.sp,
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