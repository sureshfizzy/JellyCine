package com.vela.shared.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vela.data.update.AppFlavor
import com.vela.data.update.AppUpdateAsset
import com.vela.data.update.CUSTOM_DOWNLOAD_MIRROR_ID
import com.vela.shared.R
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateDialogs(viewModel: AppUpdateViewModel, uiState: AppUpdateUiState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshInstallPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.showMirrorDialog && !uiState.showCustomMirrorDialog) {
        MirrorPickerDialog(viewModel = viewModel, uiState = uiState)
    }
    if (uiState.showCustomMirrorDialog) {
        CustomMirrorDialog(viewModel = viewModel, uiState = uiState)
    }
    if (uiState.showReleaseDialog && uiState.release != null) {
        ReleaseDialog(viewModel = viewModel, uiState = uiState)
    }
}

@Composable
private fun MirrorPickerDialog(viewModel: AppUpdateViewModel, uiState: AppUpdateUiState) {
    AlertDialog(
        onDismissRequest = viewModel::dismissMirrorPicker,
        title = { Text(stringResource(R.string.about_update_download_mirror)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                viewModel.mirrors().forEach { mirror ->
                    val selected = uiState.mirror.id == mirror.id
                    val label = if (mirror.id == "direct") {
                        stringResource(R.string.about_update_mirror_direct)
                    } else {
                        mirror.label
                    }
                    TextButton(onClick = { viewModel.selectMirror(mirror.id) }) {
                        Text(
                            text = if (selected) "✓ $label" else label,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                val customSelected = uiState.mirror.id == CUSTOM_DOWNLOAD_MIRROR_ID
                TextButton(onClick = { viewModel.selectMirror(CUSTOM_DOWNLOAD_MIRROR_ID) }) {
                    val customLabel = buildString {
                        append(stringResource(R.string.about_update_mirror_custom))
                        if (uiState.customPrefix.isNotBlank()) {
                            append(" (")
                            append(uiState.customPrefix.trimEnd('/'))
                            append(")")
                        }
                    }
                    Text(
                        text = if (customSelected) "✓ $customLabel" else customLabel,
                        color = if (customSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::dismissMirrorPicker) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun CustomMirrorDialog(viewModel: AppUpdateViewModel, uiState: AppUpdateUiState) {
    AlertDialog(
        onDismissRequest = viewModel::dismissMirrorPicker,
        title = { Text(stringResource(R.string.about_update_mirror_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.customPrefixDraft,
                    onValueChange = viewModel::updateCustomPrefixDraft,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.customPrefixError,
                    placeholder = { Text(stringResource(R.string.about_update_mirror_custom_hint)) }
                )
                if (uiState.customPrefixError) {
                    Text(
                        text = stringResource(R.string.about_update_mirror_custom_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::saveCustomPrefix) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissMirrorPicker) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ReleaseDialog(viewModel: AppUpdateViewModel, uiState: AppUpdateUiState) {
    val release = uiState.release ?: return
    val flavorAssets = viewModel.assetsForSelectedFlavor()
    val recommended = flavorAssets.firstOrNull { asset ->
        uiState.deviceAbis.any { abi -> abi.equals(asset.abi, ignoreCase = true) }
    } ?: flavorAssets.firstOrNull { it.abi == null }

    AlertDialog(
        onDismissRequest = viewModel::dismissReleaseDialog,
        title = {
            Text(
                text = if (uiState.isNewer) {
                    stringResource(R.string.about_update_available, release.versionName)
                } else {
                    stringResource(R.string.about_update_latest, release.versionName)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.about_update_current, uiState.currentVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.about_update_flavor),
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.selectedFlavor == AppFlavor.Phone,
                        onClick = { viewModel.selectFlavor(AppFlavor.Phone) },
                        enabled = !uiState.downloading,
                        label = { Text(stringResource(R.string.about_update_flavor_phone)) }
                    )
                    FilterChip(
                        selected = uiState.selectedFlavor == AppFlavor.Tv,
                        onClick = { viewModel.selectFlavor(AppFlavor.Tv) },
                        enabled = !uiState.downloading,
                        label = { Text(stringResource(R.string.about_update_flavor_tv)) }
                    )
                }

                Text(
                    text = stringResource(R.string.about_update_abi),
                    style = MaterialTheme.typography.titleSmall
                )
                if (flavorAssets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.about_update_no_apk),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        flavorAssets.forEach { asset ->
                            val selected = uiState.selectedAsset?.name == asset.name
                            val isRecommended = recommended?.name == asset.name
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectAsset(asset) },
                                enabled = !uiState.downloading,
                                label = { Text(assetChipLabel(asset, isRecommended)) }
                            )
                        }
                    }
                }

                uiState.selectedAsset?.let { asset ->
                    Text(
                        text = "${asset.name} · ${formatBytes(asset.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (release.notes.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.about_update_notes),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.downloading) {
                    val progress = downloadProgress(uiState.receivedBytes, uiState.totalBytes)
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = formatDownloadProgress(uiState.receivedBytes, uiState.totalBytes),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                uiState.downloadError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (uiState.needsInstallPermission) {
                    Text(
                        text = stringResource(R.string.about_update_permission),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                uiState.installError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            when {
                uiState.downloading -> {
                    TextButton(onClick = viewModel::cancelDownload) {
                        Text(stringResource(R.string.about_update_cancel_download))
                    }
                }
                uiState.needsInstallPermission -> {
                    TextButton(onClick = viewModel::openInstallPermissionSettings) {
                        Text(stringResource(R.string.about_update_permission_action))
                    }
                }
                uiState.downloadedFile != null -> {
                    TextButton(onClick = viewModel::installDownloaded) {
                        Text(stringResource(R.string.about_update_install))
                    }
                }
                else -> {
                    TextButton(
                        onClick = viewModel::downloadSelected,
                        enabled = uiState.selectedAsset != null
                    ) {
                        Text(stringResource(R.string.about_update_download))
                    }
                }
            }
        },
        dismissButton = {
            if (!uiState.downloading) {
                TextButton(onClick = viewModel::dismissReleaseDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun assetChipLabel(asset: AppUpdateAsset, recommended: Boolean): String {
    val abi = asset.abi ?: stringResource(R.string.about_update_abi_universal)
    return if (recommended) {
        "$abi · ${stringResource(R.string.about_update_abi_recommended)}"
    } else {
        abi
    }
}

private fun downloadProgress(received: Long, total: Long?): Float? {
    if (total == null || total <= 0L) return null
    return (received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun formatDownloadProgress(received: Long, total: Long?): String {
    return if (total != null && total > 0L) {
        "${formatBytes(received)} / ${formatBytes(total)}"
    } else {
        formatBytes(received)
    }
}

internal fun formatBytes(bytes: Long): String {
    val value = max(0L, bytes)
    if (value < 1024) return "$value B"
    val kb = value / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}
