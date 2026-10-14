package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.RemoteSubtitleInfo
import com.jellycine.data.model.SubtitleLanguages
import com.jellycine.data.network.HttpStatusException
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.player.core.resolvedTitleOrNull
import com.jellycine.shared.R
import kotlinx.coroutines.launch

// Palette
private val DialogBg = Color(0xFF0E1117)
private val RowBg = Color(0xFF171A21)
private val Hairline = Color.White.copy(alpha = 0.06f)
private val Accent = Color(0xFF3A82F7)
private val Success = Color(0xFF4ADE80)
private val Danger = Color(0xFFFF6B6B)
private val MutedText = Color.White.copy(alpha = 0.55f)

private sealed interface SubtitleSearchUiState {
    data object Idle : SubtitleSearchUiState
    data object Loading : SubtitleSearchUiState
    data class Results(val items: List<RemoteSubtitleInfo>) : SubtitleSearchUiState
    data object Error : SubtitleSearchUiState
}

private data class StatusBanner(val message: String, val isError: Boolean)

/** Primary dialog: shows the item's current subtitles and an entry point to search online. */
@Composable
internal fun SubtitleSearchDialog(
    itemId: String,
    defaultLanguage: String?,
    canDelete: Boolean,
    onSubtitlesChanged: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedLanguage by remember { mutableStateOf(SubtitleLanguages.default(defaultLanguage)) }
    var currentSubs by remember { mutableStateOf<List<MediaStream>>(emptyList()) }
    var currentSubsLoading by remember { mutableStateOf(true) }
    var deletingIndex by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf<StatusBanner?>(null) }
    var showProviderSearch by remember { mutableStateOf(false) }

    val deletedText = stringResource(R.string.subtitle_search_deleted)
    val deleteErrorText = stringResource(R.string.subtitle_search_delete_error)
    val deleteDeniedText = stringResource(R.string.subtitle_search_delete_denied)

    suspend fun loadCurrentSubs() {
        currentSubsLoading = true
        currentSubs = mediaRepository.getSubtitleTracks(itemId).getOrNull().orEmpty()
        currentSubsLoading = false
    }

    LaunchedEffect(itemId) { loadCurrentSubs() }

    fun delete(stream: MediaStream) {
        val index = stream.index ?: return
        if (deletingIndex != null) return
        deletingIndex = index
        status = null
        coroutineScope.launch {
            val result = mediaRepository.deleteSubtitle(itemId, index)
            deletingIndex = null
            if (result.isSuccess) {
                status = StatusBanner(deletedText, isError = false)
                loadCurrentSubs()
                onSubtitlesChanged()
            } else {
                val denied = (result.exceptionOrNull() as? HttpStatusException)?.statusCode == 403
                status = StatusBanner(if (denied) deleteDeniedText else deleteErrorText, isError = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogSurface {
            DialogHeader(
                icon = Icons.Rounded.ClosedCaption,
                title = stringResource(R.string.subtitle_search_heading),
                onClose = onDismiss
            )
            Hairline()
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        OptionSelectorRow(
                            label = stringResource(R.string.subtitle_search_language),
                            selectedOption = selectedLanguage.name,
                            options = SubtitleLanguages.OPTIONS,
                            onOptionSelected = { name ->
                                SubtitleLanguages.byName(name)?.let { selectedLanguage = it }
                            }
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Accent)
                            .clickable { showProviderSearch = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.subtitle_search_action),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // On this item
                Spacer(Modifier.height(20.dp))
                SectionHeader(
                    Icons.Rounded.Subtitles,
                    stringResource(R.string.subtitle_search_current),
                    currentSubs.size.takeIf { !currentSubsLoading }
                )
                status?.let { banner ->
                    Spacer(Modifier.height(12.dp))
                    StatusRow(banner)
                }
                Spacer(Modifier.height(10.dp))
                when {
                    currentSubsLoading -> CenteredSpinner()
                    currentSubs.isEmpty() -> Text(
                        text = stringResource(R.string.subtitle_search_no_current),
                        color = MutedText,
                        fontSize = 13.sp
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        currentSubs.forEach { stream ->
                            CurrentSubtitleRow(
                                stream = stream,
                                canDelete = canDelete && stream.isExternal == true && stream.index != null,
                                isDeleting = deletingIndex == stream.index,
                                onDelete = { delete(stream) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProviderSearch) {
        SubtitleProviderSearchDialog(
            itemId = itemId,
            defaultLanguage = selectedLanguage.code,
            onDownloaded = {
                coroutineScope.launch { loadCurrentSubs() }
                onSubtitlesChanged()
            },
            onDismiss = { showProviderSearch = false }
        )
    }
}

/** Secondary dialog: language picker + online search + downloadable results. */
@Composable
private fun SubtitleProviderSearchDialog(
    itemId: String,
    defaultLanguage: String?,
    onDownloaded: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedLanguage by remember { mutableStateOf(SubtitleLanguages.default(defaultLanguage)) }
    var uiState by remember { mutableStateOf<SubtitleSearchUiState>(SubtitleSearchUiState.Idle) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var downloadedId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<StatusBanner?>(null) }

    val downloadedText = stringResource(R.string.subtitle_search_downloaded)
    val downloadErrorText = stringResource(R.string.subtitle_search_download_error)

    fun runSearch() {
        uiState = SubtitleSearchUiState.Loading
        status = null
        downloadedId = null
        coroutineScope.launch {
            val result = mediaRepository.searchRemoteSubtitles(itemId, selectedLanguage.code)
            uiState = result.fold(
                onSuccess = { SubtitleSearchUiState.Results(it) },
                onFailure = { SubtitleSearchUiState.Error }
            )
        }
    }

    fun download(info: RemoteSubtitleInfo) {
        val subtitleId = info.id ?: return
        if (downloadingId != null) return
        downloadingId = subtitleId
        status = null
        coroutineScope.launch {
            val result = mediaRepository.downloadRemoteSubtitle(itemId, subtitleId)
            downloadingId = null
            if (result.isSuccess) {
                downloadedId = subtitleId
                status = StatusBanner(downloadedText, isError = false)
                onDownloaded()
            } else {
                status = StatusBanner(downloadErrorText, isError = true)
            }
        }
    }

    LaunchedEffect(Unit) { runSearch() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogSurface {
            DialogHeader(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.subtitle_search_title),
                onClose = onDismiss
            )
            Hairline()
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                OptionSelectorRow(
                    label = stringResource(R.string.subtitle_search_language),
                    selectedOption = selectedLanguage.name,
                    options = SubtitleLanguages.OPTIONS,
                    onOptionSelected = { name ->
                        SubtitleLanguages.byName(name)?.let { selectedLanguage = it }
                    }
                )

                Spacer(Modifier.height(14.dp))

                val searchEnabled = uiState != SubtitleSearchUiState.Loading
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (searchEnabled) Accent else Accent.copy(alpha = 0.4f))
                        .clickable(enabled = searchEnabled) { runSearch() }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = stringResource(R.string.subtitle_search_action),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                status?.let { banner ->
                    Spacer(Modifier.height(12.dp))
                    StatusRow(banner)
                }

                Spacer(Modifier.height(14.dp))

                when (val state = uiState) {
                    SubtitleSearchUiState.Idle -> Unit
                    SubtitleSearchUiState.Loading -> CenteredSpinner(padding = 26.dp, size = 28.dp)
                    SubtitleSearchUiState.Error -> StateMessage(
                        icon = Icons.Rounded.ErrorOutline,
                        text = stringResource(R.string.subtitle_search_error),
                        tint = Danger
                    )
                    is SubtitleSearchUiState.Results -> {
                        if (state.items.isEmpty()) {
                            StateMessage(
                                icon = Icons.Rounded.Subtitles,
                                text = stringResource(R.string.subtitle_search_empty),
                                tint = MutedText
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.items.forEach { info ->
                                    SubtitleResultRow(
                                        info = info,
                                        isDownloading = downloadingId == info.id,
                                        isDownloaded = downloadedId == info.id,
                                        enabled = downloadingId == null,
                                        onClick = { download(info) }
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
private fun DialogSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, Hairline, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = DialogBg
    ) {
        Column(content = content)
    }
}

@Composable
private fun DialogHeader(icon: ImageVector, title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.subtitle_search_close),
                tint = MutedText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun Hairline() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
private fun SectionHeader(icon: ImageVector, label: String, count: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MutedText, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(label.uppercase(), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(count.toString(), color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Chip(text: String, tint: Color = MutedText, container: Color = Color.White.copy(alpha = 0.08f)) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CenteredSpinner(
    padding: androidx.compose.ui.unit.Dp = 12.dp,
    size: androidx.compose.ui.unit.Dp = 22.dp
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(size))
    }
}

@Composable
private fun StateMessage(icon: ImageVector, text: String, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = tint, fontSize = 13.sp)
    }
}

@Composable
private fun StatusRow(banner: StatusBanner) {
    val tint = if (banner.isError) Danger else Success
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (banner.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(text = banner.message, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
    }
}

@Composable
private fun CurrentSubtitleRow(
    stream: MediaStream,
    canDelete: Boolean,
    isDeleting: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RowBg)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stream.resolvedTitleOrNull() ?: "Subtitle ${stream.index ?: ""}".trim(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (stream.isExternal == true) {
                    Chip(stringResource(R.string.subtitle_search_external), tint = Accent, container = Accent.copy(alpha = 0.15f))
                } else {
                    Chip(stringResource(R.string.subtitle_search_embedded))
                }
                if (stream.isDefault == true) Chip(stringResource(R.string.subtitle_search_default))
                if (stream.isForced == true) Chip(stringResource(R.string.subtitle_search_forced))
                stream.codec?.takeIf { it.isNotBlank() }?.let { Chip(it.uppercase()) }
            }
        }

        if (canDelete) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Danger.copy(alpha = 0.10f))
                    .clickable(enabled = !isDeleting) { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(color = Danger, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.subtitle_search_delete),
                        tint = Danger,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleResultRow(
    info: RemoteSubtitleInfo,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isDownloaded) Success.copy(alpha = 0.4f) else Hairline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RowBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled && !isDownloaded) { onClick() }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.name?.takeIf { it.isNotBlank() } ?: info.providerName.orEmpty(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                info.providerName?.takeIf { it.isNotBlank() }?.let { Chip(it) }
                info.downloadCount?.takeIf { it > 0 }?.let {
                    Chip(stringResource(R.string.subtitle_search_downloads, it))
                }
                if (info.isHashMatch == true) {
                    Chip(stringResource(R.string.subtitle_search_hash_match), tint = Success, container = Success.copy(alpha = 0.14f))
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            when {
                isDownloading -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                isDownloaded -> Icon(Icons.Rounded.CheckCircle, null, tint = Success, modifier = Modifier.size(24.dp))
                else -> Icon(Icons.Rounded.CloudDownload, null, tint = Accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}