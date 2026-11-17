package com.jellycine.ui.screens.dashboard.settings

import com.jellycine.ui.screens.dashboard.settings.*
import androidx.compose.ui.*
import androidx.compose.ui.backhandler.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.jellycine.resources.*
import org.jetbrains.compose.resources.stringResource
import com.jellycine.data.preferences.NetworkPreferences

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CacheSettingsScreen(
    viewModel: SettingsViewModel,
    onBackPressed: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCacheSizeDialog by remember { mutableStateOf(false) }

    BackHandler { onBackPressed() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.settings_cache),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back_button),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Text(
                    text = stringResource(Res.string.settings_cache_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
                )
            }

            item {
                CacheSection {
                    CacheSwitchItem(
                        icon = Icons.Rounded.Image,
                        title = stringResource(Res.string.cache_settings_cache_images),
                        subtitle = stringResource(Res.string.cache_settings_cache_images_summary),
                        checked = uiState.imageCachingEnabled,
                        onCheckedChange = viewModel::setImageCachingEnabled,
                        accentColor = Color(0xFF22D3EE)
                    )
                    CacheDivider()
                    CacheActionItem(
                        icon = Icons.Rounded.SdCard,
                        title = stringResource(Res.string.cache_settings_cache_size),
                        subtitle = if (uiState.imageMemoryCacheMb == NetworkPreferences.AUTO_IMAGE_MEMORY_CACHE_MB) {
                            stringResource(Res.string.settings_auto)
                        } else {
                            stringResource(Res.string.cache_settings_cache_size_value_mb, uiState.imageMemoryCacheMb)
                        },
                        enabled = uiState.imageCachingEnabled,
                        onClick = { showCacheSizeDialog = true },
                        accentColor = Color(0xFF60A5FA)
                    )
                }
            }
        }
    }

    if (showCacheSizeDialog) {
        CacheSizeValueDialog(
            initialValue = uiState.imageMemoryCacheMb,
            onDismiss = { showCacheSizeDialog = false },
            onSave = { value ->
                viewModel.setImageMemoryCacheMb(value)
                showCacheSizeDialog = false
            }
        )
    }
}

@Composable
private fun CacheSection(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050505)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.14f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun CacheDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.10f)
    )
}

@Composable
private fun CacheSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) accentColor else Color.White.copy(alpha = 0.32f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (enabled) 0.65f else 0.35f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun CacheActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) accentColor else Color.White.copy(alpha = 0.32f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (enabled) 0.65f else 0.35f)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 0.6f else 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CacheSizeValueDialog(
    initialValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var textValue by remember(initialValue) {
        mutableStateOf(
            if (initialValue == NetworkPreferences.AUTO_IMAGE_MEMORY_CACHE_MB) {
                ""
            } else {
                initialValue.toString()
            }
        )
    }
    val parsedValue = textValue.toIntOrNull()
    val valueToPersist = parsedValue ?: NetworkPreferences.AUTO_IMAGE_MEMORY_CACHE_MB
    val isValid = parsedValue == null || (
        parsedValue == NetworkPreferences.AUTO_IMAGE_MEMORY_CACHE_MB ||
            parsedValue in NetworkPreferences.MIN_IMAGE_MEMORY_CACHE_MB..NetworkPreferences.MAX_IMAGE_MEMORY_CACHE_MB
    )
    val hasValidationError = textValue.isNotBlank() && !isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(stringResource(Res.string.cache_settings_cache_size_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        textValue = input.filter { it.isDigit() }.take(4)
                    },
                    label = { Text(stringResource(Res.string.cache_settings_cache_size_input_label)) },
                    singleLine = true,
                    isError = hasValidationError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White.copy(alpha = 0.9f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                        focusedBorderColor = Color(0xFF22D3EE),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                        cursorColor = Color(0xFF22D3EE),
                        errorBorderColor = Color(0xFFFF6B6B),
                        errorLabelColor = Color(0xFFFF6B6B),
                        errorCursorColor = Color(0xFFFF6B6B)
                    )
                )
                Text(
                    text = stringResource(
                        Res.string.cache_settings_allowed_range_mb,
                        NetworkPreferences.MIN_IMAGE_MEMORY_CACHE_MB,
                        NetworkPreferences.MAX_IMAGE_MEMORY_CACHE_MB
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = stringResource(Res.string.cache_settings_auto_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { onSave(valueToPersist) }) {
                Text(stringResource(Res.string.settings_apply), color = Color(0xFF22D3EE))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}
