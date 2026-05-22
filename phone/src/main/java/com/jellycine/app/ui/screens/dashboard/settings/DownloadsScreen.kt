package com.jellycine.app.ui.screens.dashboard.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.data.model.DownloadStatus
import com.jellycine.data.model.DownloadStorageBehavior
import com.jellycine.data.model.TrackedDownload
import com.jellycine.data.preferences.DownloadPreferences
import com.jellycine.shared.R
import com.jellycine.app.ui.components.common.isPausedTrackedDownload
import com.jellycine.app.ui.screens.player.PlayerScreen
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.util.image.rememberImageUrl
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.launch
import java.util.Locale

private val DownloadAccentColor = Color(0xFF22D3EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackPressed: () -> Unit = {},
    embedded: Boolean = false,
    onPlayItem: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val resources = context.resources
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val downloadPreferences = remember { DownloadPreferences(context) }
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val downloads by downloadRepository.observeTrackedDownloads().collectAsState()
    val scope = rememberCoroutineScope()

    val visibleDownloads = remember(downloads) {
        downloads.filter {
            it.isOfflineAvailable ||
                it.state.status == DownloadStatus.DOWNLOADING ||
                it.state.status == DownloadStatus.QUEUED
        }
    }
    val movieEntries = remember(visibleDownloads) {
        visibleDownloads.filter { (it.item?.type ?: it.mediaType).equals("Movie", ignoreCase = true) }
            .sortedWith(
                compareByDescending<TrackedDownload> {
                    it.state.status == DownloadStatus.DOWNLOADING || it.state.status == DownloadStatus.QUEUED
                }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
            )
    }
    val seriesGroups = remember(visibleDownloads, resources) {
        buildSeriesGroups(
            visibleDownloads.filter { (it.item?.type ?: it.mediaType).equals("Episode", ignoreCase = true) },
            resources
        )
    }

    var selectedSeriesId by remember { mutableStateOf<String?>(null) }
    var playbackItemId by remember { mutableStateOf<String?>(null) }
    var showDownloadSettings by remember { mutableStateOf(false) }
    var storageBehavior by remember { mutableStateOf(downloadPreferences.getStorageBehavior()) }
    var deviceDownloadsTreeUri by remember { mutableStateOf(downloadPreferences.getDeviceDownloadsTreeUri()) }
    var maxConcurrentDownloads by remember { mutableStateOf(downloadPreferences.getMaxConcurrentDownloads()) }
    val appStoragePath = remember(context) {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.absolutePath
            ?: context.filesDir
                .resolve(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
    }
    val defaultDeviceDownloadsPath = remember {
        @Suppress("DEPRECATION")
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .resolve("JellyCine")
            .absolutePath
    }
    val deletingState = remember { mutableStateMapOf<String, Boolean>() }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) return@rememberLauncherForActivityResult
        storageBehavior = DownloadStorageBehavior.DEVICE_DOWNLOADS
        downloadPreferences.setStorageBehavior(DownloadStorageBehavior.DEVICE_DOWNLOADS)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val rawUri = uri.toString()
        deviceDownloadsTreeUri = rawUri
        storageBehavior = DownloadStorageBehavior.DEVICE_DOWNLOADS
        downloadPreferences.setDeviceDownloadsTreeUri(rawUri)
        downloadPreferences.setStorageBehavior(DownloadStorageBehavior.DEVICE_DOWNLOADS)
    }
    val selectDeviceDownloads: () -> Unit = {
        if (requiresLegacyDownloadsPermission() && !hasLegacyDownloadsPermission(context)) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            storageBehavior = DownloadStorageBehavior.DEVICE_DOWNLOADS
            downloadPreferences.setStorageBehavior(DownloadStorageBehavior.DEVICE_DOWNLOADS)
        }
    }

    val selectedSeries = remember(selectedSeriesId, seriesGroups) {
        seriesGroups.firstOrNull { it.id == selectedSeriesId }
    }

    val activePlaybackId = playbackItemId
    val closeCurrentLayer: () -> Unit = {
        when {
            showDownloadSettings -> showDownloadSettings = false
            !activePlaybackId.isNullOrBlank() -> playbackItemId = null
            selectedSeries != null -> selectedSeriesId = null
            !embedded -> onBackPressed()
        }
    }
    BackHandler(
        enabled = showDownloadSettings || !activePlaybackId.isNullOrBlank() || selectedSeries != null || !embedded,
        onBack = closeCurrentLayer
    )

    if (!activePlaybackId.isNullOrBlank()) {
        PlayerScreen(
            mediaId = activePlaybackId,
            onBackPressed = { playbackItemId = null }
        )
        return
    }

    val deleteDownload: (TrackedDownload) -> Unit = { entry ->
        val itemId = entry.itemId
        scope.launch {
            deletingState[itemId] = true
            downloadRepository.deleteDownloadedItem(itemId)
            deletingState.remove(itemId)
        }
    }
    val playDownload: (TrackedDownload) -> Unit = { entry ->
        val itemId = entry.item?.id ?: entry.itemId
        if (onPlayItem != null) {
            onPlayItem(itemId)
        } else {
            playbackItemId = itemId
        }
    }
    val toggleDownloadPause: (TrackedDownload) -> Unit = { entry ->
        if (isPausedState(entry, resources)) {
            downloadRepository.resumeDownload(entry.itemId)
        } else {
            downloadRepository.pauseDownload(entry.itemId)
        }
    }
    val cancelDownload: (TrackedDownload) -> Unit = { entry ->
        downloadRepository.cancelDownload(entry.itemId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when {
                            showDownloadSettings -> stringResource(R.string.downloads_settings_title)
                            selectedSeries != null -> selectedSeries.title
                            else -> stringResource(R.string.downloads)
                        }
                    )
                },
                actions = {
                    if (!showDownloadSettings) {
                        IconButton(
                            onClick = {
                                storageBehavior = downloadPreferences.getStorageBehavior()
                                deviceDownloadsTreeUri = downloadPreferences.getDeviceDownloadsTreeUri()
                                maxConcurrentDownloads = downloadPreferences.getMaxConcurrentDownloads()
                                showDownloadSettings = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.downloads_settings_title)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showDownloadSettings || selectedSeries != null || !embedded) {
                        IconButton(onClick = closeCurrentLayer) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back_button)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            showDownloadSettings -> {
                DownloadSettingsContent(
                    innerPadding = innerPadding,
                    storageBehavior = storageBehavior,
                    deviceDownloadsTreeUri = deviceDownloadsTreeUri,
                    maxConcurrentDownloads = maxConcurrentDownloads,
                    appStoragePath = appStoragePath,
                    defaultDeviceDownloadsPath = defaultDeviceDownloadsPath,
                    onMaxConcurrentDownloadsChange = { count ->
                        maxConcurrentDownloads = count
                        downloadPreferences.setMaxConcurrentDownloads(count)
                        downloadRepository.onDownloadPreferencesChanged()
                    },
                    onStorageBehaviorChange = { behavior ->
                        if (behavior == DownloadStorageBehavior.DEVICE_DOWNLOADS) {
                            selectDeviceDownloads()
                        } else {
                            storageBehavior = behavior
                            downloadPreferences.setStorageBehavior(behavior)
                        }
                    },
                    onChooseFolder = {
                        folderPicker.launch(deviceDownloadsTreeUri?.let { Uri.parse(it) })
                    },
                    onUseDefaultFolder = {
                        deviceDownloadsTreeUri = null
                        downloadPreferences.setDeviceDownloadsTreeUri(null)
                        selectDeviceDownloads()
                    }
                )
            }
            visibleDownloads.isEmpty() -> {
                EmptyDownloadsState(innerPadding = innerPadding)
            }
            selectedSeries != null -> {
                SeriesDetailContent(
                    innerPadding = innerPadding,
                    group = selectedSeries,
                    mediaRepository = mediaRepository,
                    deletingState = deletingState,
                    onDeleteEpisode = deleteDownload,
                    onPlayEpisode = playDownload,
                    onPause = toggleDownloadPause,
                    onCancel = cancelDownload
                )
            }
            else -> {
                DownloadsRootContent(
                    innerPadding = innerPadding,
                    seriesGroups = seriesGroups,
                    movieEntries = movieEntries,
                    mediaRepository = mediaRepository,
                    deletingState = deletingState,
                    onSeriesClick = { selectedSeriesId = it.id },
                    onDeleteMovie = deleteDownload,
                    onPlayMovie = playDownload,
                    onPause = toggleDownloadPause,
                    onCancel = cancelDownload
                )
            }
        }
    }
}

@Composable
private fun EmptyDownloadsState(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.downloads_empty),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DownloadSettingsContent(
    innerPadding: PaddingValues,
    storageBehavior: DownloadStorageBehavior,
    deviceDownloadsTreeUri: String?,
    maxConcurrentDownloads: Int,
    appStoragePath: String,
    defaultDeviceDownloadsPath: String,
    onMaxConcurrentDownloadsChange: (Int) -> Unit,
    onStorageBehaviorChange: (DownloadStorageBehavior) -> Unit,
    onChooseFolder: () -> Unit,
    onUseDefaultFolder: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.downloads_settings_storage_location),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.downloads_settings_storage_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            DownloadStorageOption(
                title = stringResource(R.string.downloads_settings_in_app_storage),
                subtitle = stringResource(R.string.downloads_settings_in_app_storage_summary),
                selected = storageBehavior == DownloadStorageBehavior.APP_STORAGE,
                onClick = { onStorageBehaviorChange(DownloadStorageBehavior.APP_STORAGE) }
            )
        }

        item {
            DownloadPathSummary(
                label = stringResource(R.string.downloads_settings_in_app_path),
                path = appStoragePath
            )
        }

        item {
            DownloadStorageOption(
                title = stringResource(R.string.downloads_settings_device_downloads),
                subtitle = stringResource(R.string.downloads_settings_device_downloads_summary),
                selected = storageBehavior == DownloadStorageBehavior.DEVICE_DOWNLOADS,
                onClick = { onStorageBehaviorChange(DownloadStorageBehavior.DEVICE_DOWNLOADS) }
            )
        }

        item {
            DownloadPathSummary(
                label = if (deviceDownloadsTreeUri.isNullOrBlank()) {
                    stringResource(R.string.downloads_settings_default_path)
                } else {
                    stringResource(R.string.downloads_settings_selected_folder)
                },
                path = deviceDownloadLocationLabel(
                    treeUri = deviceDownloadsTreeUri,
                    defaultPath = defaultDeviceDownloadsPath
                )
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onChooseFolder,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = DownloadAccentColor)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.downloads_settings_change_folder))
                }
                TextButton(
                    onClick = onUseDefaultFolder,
                    modifier = Modifier.weight(1f),
                    enabled = deviceDownloadsTreeUri != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = DownloadAccentColor)
                ) {
                    Text(stringResource(R.string.downloads_settings_use_default))
                }
            }
        }

        if (requiresLegacyDownloadsPermission()) {
            item {
                Text(
                    text = stringResource(R.string.downloads_settings_legacy_permission_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            DownloadConcurrencySetting(
                selected = maxConcurrentDownloads,
                onSelected = onMaxConcurrentDownloadsChange
            )
        }
    }
}

@Composable
private fun DownloadConcurrencySetting(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val range = DownloadPreferences.MIN_CONCURRENT_DOWNLOADS..DownloadPreferences.MAX_CONCURRENT_DOWNLOADS

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.downloads_settings_concurrent_downloads),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.downloads_settings_concurrent_downloads_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = DownloadAccentColor.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.downloads_settings_concurrent_downloads_value,
                        selected,
                        selected
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = DownloadAccentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                range.forEach { count ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.downloads_settings_concurrent_downloads_value,
                                    count,
                                    count
                                ),
                                color = if (selected == count) {
                                    DownloadAccentColor
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        onClick = {
                            onSelected(count)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadPathSummary(
    label: String,
    path: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = DownloadAccentColor,
            modifier = Modifier.size(22.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DownloadStorageOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (selected) {
                    DownloadAccentColor.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = DownloadAccentColor)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun deviceDownloadLocationLabel(
    treeUri: String?,
    defaultPath: String
): String {
    val customPath = treeUri?.let(::folderLabelFromTreeUri)
    return if (customPath.isNullOrBlank()) defaultPath else customPath
}

private fun folderLabelFromTreeUri(treeUri: String): String {
    val decoded = Uri.decode(treeUri)
    val documentId = decoded.substringAfterLast("/tree/", decoded)
    if (documentId == "primary:") {
        return Environment.getExternalStorageDirectory().absolutePath
    }
    if (documentId.startsWith("primary:")) {
        return Environment.getExternalStorageDirectory()
            .resolve(documentId.removePrefix("primary:"))
            .absolutePath
    }
    return documentId.replace(':', '/').trimEnd('/')
}

private fun requiresLegacyDownloadsPermission(): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
}

private fun hasLegacyDownloadsPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun DownloadsRootContent(
    innerPadding: PaddingValues,
    seriesGroups: List<OfflineSeriesGroup>,
    movieEntries: List<TrackedDownload>,
    mediaRepository: MediaRepository,
    deletingState: Map<String, Boolean>,
    onSeriesClick: (OfflineSeriesGroup) -> Unit,
    onDeleteMovie: (TrackedDownload) -> Unit,
    onPlayMovie: (TrackedDownload) -> Unit,
    onPause: (TrackedDownload) -> Unit,
    onCancel: (TrackedDownload) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        if (seriesGroups.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.tv_shows)) }
            itemsIndexed(
                items = seriesGroups,
                key = { index, group -> "series_${group.id}_$index" }
            ) { _, group ->
                SeriesSummaryRow(
                    group = group,
                    mediaRepository = mediaRepository,
                    onClick = { onSeriesClick(group) }
                )
            }
        }

        if (movieEntries.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.movies)) }
            itemsIndexed(
                items = movieEntries,
                key = { index, entry -> "movie_${entry.itemId}_$index" }
            ) { _, entry ->
                MovieRow(
                    entry = entry,
                    mediaRepository = mediaRepository,
                    deleting = deletingState[entry.itemId] == true,
                    onDelete = { onDeleteMovie(entry) },
                    onPlay = { onPlayMovie(entry) },
                    onPauseAction = { onPause(entry) },
                    onCancelAction = { onCancel(entry) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun artwork(
    title: String,
    imageUrl: String?,
    logoUrl: String?,
    width: Dp,
    height: Dp
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        JellyfinPosterImage(
            imageUrl = imageUrl,
            contentDescription = title,
            context = context,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (!logoUrl.isNullOrBlank()) {
            JellyfinPosterImage(
                imageUrl = logoUrl,
                contentDescription = title,
                context = context,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(height * 0.28f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun downloadImageUrl(
    itemId: String?,
    imageType: String,
    mediaRepository: MediaRepository,
    width: Int = 1280,
    height: Int? = 720,
    quality: Int = 100
): String? {
    return rememberImageUrl(
        itemId = itemId,
        imageType = imageType,
        width = width,
        height = height,
        quality = quality,
        enableImageEnhancers = false,
        mediaRepository = mediaRepository
    )
}

@Composable
private fun SeriesSummaryRow(
    group: OfflineSeriesGroup,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val resources = LocalContext.current.resources
    val primaryImageUrl = downloadImageUrl(
        itemId = group.posterItemId,
        imageType = "Primary",
        mediaRepository = mediaRepository
    )
    val backdropImageUrl = downloadImageUrl(
        itemId = group.posterItemId,
        imageType = "Backdrop",
        mediaRepository = mediaRepository
    )
    val logoImageUrl = downloadImageUrl(
        itemId = group.posterItemId,
        imageType = "Logo",
        width = 1000,
        height = null,
        quality = 95,
        mediaRepository = mediaRepository
    )
    val imageUrl = backdropImageUrl ?: primaryImageUrl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        artwork(
            title = group.title,
            imageUrl = imageUrl,
            logoUrl = logoImageUrl,
            width = 148.dp,
            height = 84.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.downloads_series_meta,
                    resources.getQuantityString(
                        R.plurals.downloads_episodes_count,
                        group.totalEpisodes,
                        group.totalEpisodes
                    ),
                    formatBytes(group.totalSizeBytes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            val progressLabel = groupProgressLabel(group, resources)
            if (!progressLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MovieRow(
    entry: TrackedDownload,
    mediaRepository: MediaRepository,
    deleting: Boolean,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onPauseAction: () -> Unit,
    onCancelAction: () -> Unit
) {
    val resources = LocalContext.current.resources
    val posterId = entry.item?.id ?: entry.itemId
    val primaryImageUrl = downloadImageUrl(
        itemId = posterId,
        imageType = "Primary",
        mediaRepository = mediaRepository
    )
    val backdropImageUrl = downloadImageUrl(
        itemId = posterId,
        imageType = "Backdrop",
        mediaRepository = mediaRepository
    )
    val logoImageUrl = downloadImageUrl(
        itemId = posterId,
        imageType = "Logo",
        width = 1000,
        height = null,
        quality = 95,
        mediaRepository = mediaRepository
    )
    val imageUrl = backdropImageUrl ?: primaryImageUrl
    val canPlay = entry.isOfflineAvailable
    val statusLabel = downloadStatusLabel(entry, resources)
    val yearText = (entry.item?.productionYear ?: entry.year)?.toString().orEmpty()
    val sizeText = formatBytes(entry.displayBytes())
    val metaText = if (yearText.isBlank()) {
        sizeText
    } else {
        stringResource(R.string.downloads_series_meta, yearText, sizeText)
    }

    DownloadMediaRow(
        entry = entry,
        title = entry.title,
        metaText = metaText,
        statusLabel = statusLabel,
        titleMaxLines = 1,
        verticalPadding = 5.dp,
        canPlay = canPlay,
        deleting = deleting,
        onDelete = onDelete,
        onPlay = onPlay,
        onPause = onPauseAction,
        onCancel = onCancelAction,
        artwork = {
            artwork(
                title = entry.title,
                imageUrl = imageUrl,
                logoUrl = logoImageUrl,
                width = 148.dp,
                height = 84.dp
            )
        }
    )
}

@Composable
private fun DownloadMediaRow(
    entry: TrackedDownload,
    title: String,
    metaText: String,
    statusLabel: String?,
    titleMaxLines: Int,
    verticalPadding: Dp,
    canPlay: Boolean,
    deleting: Boolean,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    artwork: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = canPlay, onClick = onPlay)
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        artwork()
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            DownloadStatusText(entry = entry, statusLabel = statusLabel)
        }

        DownloadRowAction(
            entry = entry,
            deleting = deleting,
            onDelete = onDelete,
            onPause = onPause,
            onCancel = onCancel
        )
    }
}

@Composable
private fun SeriesDetailContent(
    innerPadding: PaddingValues,
    group: OfflineSeriesGroup,
    mediaRepository: MediaRepository,
    deletingState: Map<String, Boolean>,
    onDeleteEpisode: (TrackedDownload) -> Unit,
    onPlayEpisode: (TrackedDownload) -> Unit,
    onPause: (TrackedDownload) -> Unit,
    onCancel: (TrackedDownload) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        itemsIndexed(
            items = group.seasons,
            key = { index, season -> "${season.id}_$index" }
        ) { _, season ->
                Text(
                    text = season.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

            season.episodes.forEach { entry ->
                EpisodeRow(
                    entry = entry,
                    mediaRepository = mediaRepository,
                    deleting = deletingState[entry.itemId] == true,
                    onDelete = { onDeleteEpisode(entry) },
                    onPlay = { onPlayEpisode(entry) },
                    onPauseAction = { onPause(entry) },
                    onCancelAction = { onCancel(entry) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    entry: TrackedDownload,
    mediaRepository: MediaRepository,
    deleting: Boolean,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onPauseAction: () -> Unit,
    onCancelAction: () -> Unit
) {
    val resources = LocalContext.current.resources
    val item = entry.item
    val primaryImageUrl = downloadImageUrl(
        itemId = item?.id ?: entry.itemId,
        imageType = "Primary",
        mediaRepository = mediaRepository
    )
    val backdropImageUrl = downloadImageUrl(
        itemId = item?.id ?: entry.itemId,
        imageType = "Backdrop",
        mediaRepository = mediaRepository
    )
    val seriesPrimaryImageUrl = downloadImageUrl(
        itemId = item?.seriesId,
        imageType = "Primary",
        mediaRepository = mediaRepository
    )
    val imageUrl = primaryImageUrl ?: backdropImageUrl ?: seriesPrimaryImageUrl
    val episodeNumber = item?.indexNumber ?: 0
    val runtime = item?.runTimeTicks?.let { ticksToMinutes(it) } ?: "-"
    val size = formatBytes(entry.displayBytes())
    val canPlay = entry.isOfflineAvailable
    val statusLabel = downloadStatusLabel(entry, resources)

    DownloadMediaRow(
        entry = entry,
        title = stringResource(R.string.downloads_episode_title, episodeNumber, entry.title),
        metaText = stringResource(R.string.downloads_series_meta, runtime, size),
        statusLabel = statusLabel,
        titleMaxLines = 2,
        verticalPadding = 4.dp,
        canPlay = canPlay,
        deleting = deleting,
        onDelete = onDelete,
        onPlay = onPlay,
        onPause = onPauseAction,
        onCancel = onCancelAction,
        artwork = {
            Box(
                modifier = Modifier
                    .width(136.dp)
                    .height(74.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                JellyfinPosterImage(
                    imageUrl = imageUrl,
                    contentDescription = entry.title,
                    context = LocalContext.current,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    )
}

@Composable
private fun DownloadStatusText(
    entry: TrackedDownload,
    statusLabel: String?
) {
    if (statusLabel.isNullOrBlank()) return

    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = statusLabel,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = if (entry.state.status == DownloadStatus.DOWNLOADING) {
            Color.White
        } else {
            Color.White.copy(alpha = 0.82f)
        },
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DownloadActionMenuItem(
    text: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
            )
        },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        onClick = onClick
    )
}

@Composable
private fun DownloadActionIcon(
    entry: TrackedDownload,
    deleting: Boolean
) {
    when {
        deleting -> {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        }

        entry.state.status == DownloadStatus.DOWNLOADING -> {
            val progress = downloadProgress(entry).coerceIn(0.02f, 1f)
            val hasKnownTotal = entry.state.totalBytes > 0L
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasKnownTotal) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = stringResource(R.string.downloads_action_options),
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        entry.state.status == DownloadStatus.QUEUED -> {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(
                        width = 1.3.dp,
                        color = Color.White.copy(alpha = 0.85f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = stringResource(R.string.downloads_queue_options),
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        else -> {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.delete),
                tint = Color.White.copy(alpha = 0.88f)
            )
        }
    }
}

private fun isActiveDownload(entry: TrackedDownload): Boolean {
    return entry.state.status == DownloadStatus.DOWNLOADING ||
        entry.state.status == DownloadStatus.QUEUED
}

@Composable
private fun DownloadRowAction(
    entry: TrackedDownload,
    deleting: Boolean,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    val isActive = isActiveDownload(entry)
    val resources = LocalContext.current.resources
    val isPaused = isPausedState(entry, resources)
    var menuExpanded by remember(entry.itemId, entry.state.status, entry.state.message) { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = {
                if (isActive) {
                    menuExpanded = true
                } else {
                    onDelete()
                }
            },
            enabled = !deleting
        ) {
            DownloadActionIcon(entry = entry, deleting = deleting)
        }

        DropdownMenu(
            modifier = Modifier.widthIn(min = 108.dp, max = 136.dp),
            expanded = menuExpanded && isActive,
            onDismissRequest = { menuExpanded = false }
        ) {
            if (entry.state.status == DownloadStatus.DOWNLOADING) {
                DownloadActionMenuItem(
                    text = stringResource(R.string.downloads_action_pause),
                    onClick = {
                        menuExpanded = false
                        onPause()
                    }
                )
            } else if (isPaused) {
                DownloadActionMenuItem(
                    text = stringResource(R.string.resume),
                    onClick = {
                        menuExpanded = false
                        onPause()
                    }
                )
            }
            DownloadActionMenuItem(
                text = stringResource(R.string.downloads_action_cancel),
                onClick = {
                    menuExpanded = false
                    onCancel()
                }
            )
        }
    }
}

private data class OfflineSeriesGroup(
    val id: String,
    val title: String,
    val posterItemId: String,
    val seasons: List<OfflineSeasonGroup>,
    val totalEpisodes: Int,
    val totalSizeBytes: Long
)

private data class OfflineSeasonGroup(
    val id: String,
    val label: String,
    val episodes: List<TrackedDownload>
)

private fun buildSeriesGroups(entries: List<TrackedDownload>, resources: Resources): List<OfflineSeriesGroup> {
    return entries
        .groupBy { entry ->
            val item = entry.item
            item?.seriesId ?: item?.parentId ?: entry.itemId
        }
        .map { (seriesId, episodes) ->
            val firstItem = episodes.firstNotNullOfOrNull { it.item }
            val seriesTitle = firstItem?.seriesName?.takeIf { it.isNotBlank() }
                ?: episodes.firstOrNull()?.title
                ?: resources.getString(R.string.downloads_series_fallback)
            val posterItemId = firstItem?.seriesId ?: firstItem?.id ?: seriesId

            val seasons = episodes
                .groupBy { ep ->
                    val item = ep.item
                    item?.seasonId ?: item?.parentIndexNumber?.toString() ?: "unknown"
                }
                .map { (_, seasonEpisodes) ->
                    val first = seasonEpisodes.firstOrNull()?.item
                    val seasonId = first?.seasonId ?: first?.parentIndexNumber?.toString() ?: "unknown"
                    val label = when {
                        !first?.seasonName.isNullOrBlank() -> first.seasonName.orEmpty()
                        first?.parentIndexNumber != null -> resources.getString(
                            R.string.downloads_season_number,
                            first.parentIndexNumber
                        )
                        else -> resources.getString(R.string.downloads_season_fallback)
                    }
                    OfflineSeasonGroup(
                        id = seasonId,
                        label = label,
                        episodes = seasonEpisodes.sortedBy { it.item?.indexNumber ?: Int.MAX_VALUE }
                    )
                }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }

            OfflineSeriesGroup(
                id = seriesId,
                title = seriesTitle,
                posterItemId = posterItemId,
                seasons = seasons,
                totalEpisodes = episodes.size,
                totalSizeBytes = episodes.sumOf { it.displayBytes() ?: 0L }
            )
        }
        .sortedWith(
            compareByDescending<OfflineSeriesGroup> { group ->
                group.seasons.any { season ->
                    season.episodes.any { episode ->
                        episode.state.status == DownloadStatus.DOWNLOADING ||
                            episode.state.status == DownloadStatus.QUEUED
                    }
                }
            }
                .thenBy { it.title.lowercase(Locale.getDefault()) }
        )
}

private fun formatBytes(bytes: Long?): String {
    val safe = bytes ?: return "-"
    if (safe <= 0L) return "-"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        safe >= gb -> String.format(Locale.US, "%.1f GB", safe / gb)
        safe >= mb -> String.format(Locale.US, "%.1f MB", safe / mb)
        safe >= kb -> String.format(Locale.US, "%.0f KB", safe / kb)
        else -> "$safe B"
    }
}

private fun ticksToMinutes(ticks: Long): String {
    val minutes = ticks / 10_000_000L / 60L
    val hours = minutes / 60
    val remMin = minutes % 60
    return when {
        hours > 0L && remMin > 0L -> "${hours}h ${remMin}m"
        hours > 0L -> "${hours}h"
        minutes > 0L -> "${minutes}m"
        else -> "-"
    }
}

private fun TrackedDownload.displayBytes(): Long? {
    return fileSizeBytes
        ?: state.totalBytes.takeIf { it > 0L }
        ?: state.downloadedBytes.takeIf { it > 0L }
}

private fun isPausedState(entry: TrackedDownload, resources: Resources): Boolean {
    return isPausedTrackedDownload(entry, resources.getString(R.string.downloads_status_paused))
}

private fun downloadStatusLabel(entry: TrackedDownload, resources: Resources): String? {
    return when (entry.state.status) {
        DownloadStatus.DOWNLOADING -> {
            val size = if (entry.state.totalBytes > 0L) {
                resources.getString(
                    R.string.downloads_progress_with_total,
                    formatProgressBytes(entry.state.downloadedBytes),
                    formatProgressBytes(entry.state.totalBytes)
                )
            } else if (entry.state.downloadedBytes > 0L) {
                resources.getString(
                    R.string.downloads_progress_downloaded_only,
                    formatProgressBytes(entry.state.downloadedBytes)
                )
            } else {
                ""
            }
            if (entry.state.totalBytes > 0L) {
                val percent = (downloadProgress(entry) * 100f).toInt()
                resources.getString(R.string.downloads_status_downloading_percent, percent, size)
            } else {
                resources.getString(R.string.downloads_status_downloading, size)
            }
        }
        DownloadStatus.QUEUED -> {
            if (isPausedState(entry, resources)) {
                resources.getString(R.string.downloads_status_paused)
            } else {
                resources.getString(R.string.downloads_status_queued)
            }
        }
        else -> null
    }
}

private fun downloadProgress(entry: TrackedDownload): Float {
    val downloadedBytes = entry.state.downloadedBytes
    val totalBytes = entry.state.totalBytes
    if (downloadedBytes > 0L && totalBytes > 0L) {
        return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }
    return 0f
}

private fun formatProgressBytes(bytes: Long): String {
    return if (bytes <= 0L) "0 B" else formatBytes(bytes)
}

private fun groupProgressLabel(group: OfflineSeriesGroup, resources: Resources): String? {
    val episodes = group.seasons.flatMap { it.episodes }
    val totalEpisodes = episodes.size
    val completedEpisodes = episodes.count { it.isOfflineAvailable }
    val downloadingEpisodes = episodes.count { it.state.status == DownloadStatus.DOWNLOADING }
    val pausedEpisodes = episodes.count { isPausedState(it, resources) }
    val queuedEpisodes = episodes.count { it.state.status == DownloadStatus.QUEUED } - pausedEpisodes

    return when {
        downloadingEpisodes > 0 ->
            resources.getString(
                R.string.downloads_group_downloading,
                completedEpisodes + downloadingEpisodes,
                totalEpisodes
            )
        queuedEpisodes > 0 ->
            resources.getQuantityString(R.plurals.downloads_group_queued, queuedEpisodes, queuedEpisodes)
        pausedEpisodes > 0 ->
            resources.getQuantityString(R.plurals.downloads_group_paused, pausedEpisodes, pausedEpisodes)
        else -> null
    }
}