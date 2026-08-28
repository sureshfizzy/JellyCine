package com.vela.app.ui.screens.dashboard.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.app.download.DownloadRepositoryProvider
import com.vela.data.model.BaseItemDto
import com.vela.data.repository.MediaRepository
import com.vela.player.preferences.TranscodeProfile
import com.vela.shared.R
import com.vela.shared.util.image.JellyfinPosterImage
import com.vela.shared.util.image.imageTagFor
import com.vela.shared.util.image.rememberImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class OverflowPicker { None, Playlist, Collection }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemOverflowSheet(
    item: BaseItemDto,
    isAdministrator: Boolean,
    mediaRepository: MediaRepository,
    onDismiss: () -> Unit,
    onPlayFromBeginning: (String) -> Unit,
    onItemMutated: (BaseItemDto) -> Unit,
    onItemDeleted: (String) -> Unit,
    onMessage: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picker by remember { mutableStateOf(OverflowPicker.None) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemoveIdentify by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val isFavorite = item.userData?.isFavorite == true
    val isPlayed = item.userData?.played == true
    val isLocked = item.lockData == true
    val canDownload = item.canDownload != false &&
        (item.type.equals("Movie", true) || item.type.equals("Episode", true))
    val canPlay = item.type.equals("Movie", true) ||
        item.type.equals("Episode", true) ||
        item.type.equals("Series", true) ||
        item.type.equals("Trailer", true)
    val posterUrl = rememberImageUrl(
        itemId = item.id,
        imageType = "Primary",
        width = 240,
        height = null,
        quality = 85,
        imageTag = item.imageTagFor("Primary"),
        mediaRepository = mediaRepository
    )
    var posterAspect by remember(posterUrl) { mutableStateOf(2f / 3f) }

    fun runAction(block: suspend () -> Result<*>) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { block() }
            busy = false
            onMessage(result.isSuccess)
            if (result.isSuccess) {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161616),
        scrimColor = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val previewAspect = posterAspect.coerceIn(0.45f, 2.4f)
                Box(
                    modifier = Modifier
                        .then(
                            if (previewAspect < 1f) {
                                Modifier
                                    .height(78.dp)
                                    .aspectRatio(previewAspect, matchHeightConstraintsFirst = true)
                            } else {
                                Modifier
                                    .widthIn(max = 120.dp)
                                    .height(48.dp)
                                    .aspectRatio(previewAspect, matchHeightConstraintsFirst = true)
                            }
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    JellyfinPosterImage(
                        imageUrl = posterUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        context = context,
                        onIntrinsicSizeChange = { width, height ->
                            if (width > 0f && height > 0f) {
                                posterAspect = width / height
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name ?: "",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            if (canPlay) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        runAction {
                            mediaRepository.resolvePlayableItemId(item).map { playableId ->
                                withContext(Dispatchers.Main) { onPlayFromBeginning(playableId) }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2A2A),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.item_action_play_from_beginning),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium
                    )
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OverflowAction(
                icon = Icons.Outlined.Folder,
                label = stringResource(R.string.item_action_add_collection),
                enabled = !busy,
                onClick = { picker = OverflowPicker.Collection }
            )
            OverflowAction(
                icon = Icons.Outlined.PlaylistAdd,
                label = stringResource(R.string.item_action_add_playlist),
                enabled = !busy,
                onClick = { picker = OverflowPicker.Playlist }
            )
            OverflowAction(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                label = stringResource(
                    if (isFavorite) R.string.item_action_remove_favorite else R.string.item_action_add_favorite
                ),
                enabled = !busy,
                onClick = {
                    val itemId = item.id ?: return@OverflowAction
                    runAction {
                        mediaRepository.setFavoriteStatus(itemId, !isFavorite).onSuccess {
                            onItemMutated(
                                item.copy(
                                    userData = item.userData?.copy(isFavorite = !isFavorite)
                                        ?: com.vela.data.model.UserItemDataDto(isFavorite = !isFavorite)
                                )
                            )
                        }
                    }
                }
            )
            OverflowAction(
                icon = if (isPlayed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                label = stringResource(
                    if (isPlayed) R.string.item_action_mark_unplayed else R.string.item_action_mark_played
                ),
                enabled = !busy,
                onClick = {
                    val itemId = item.id ?: return@OverflowAction
                    runAction {
                        mediaRepository.setPlayedStatus(itemId, !isPlayed).onSuccess {
                            onItemMutated(
                                item.copy(
                                    userData = item.userData?.copy(
                                        played = !isPlayed,
                                        playbackPositionTicks = if (isPlayed) item.userData?.playbackPositionTicks else 0
                                    ) ?: com.vela.data.model.UserItemDataDto(played = !isPlayed)
                                )
                            )
                        }
                    }
                }
            )

            if (canDownload) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.08f)
                )
                OverflowAction(
                    icon = Icons.Outlined.Download,
                    label = stringResource(R.string.item_action_download),
                    enabled = !busy,
                    onClick = {
                        runAction {
                            DownloadRepositoryProvider.getInstance(context)
                                .enqueueItemDownload(item, TranscodeProfile("Original", null, null), null)
                        }
                    }
                )
            }

            if (isAdministrator) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.08f)
                )
                OverflowAction(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(R.string.item_action_delete),
                    enabled = !busy && item.canDelete != false,
                    onClick = { confirmDelete = true }
                )
                OverflowAction(
                    icon = Icons.Outlined.Search,
                    label = stringResource(R.string.item_action_identify),
                    enabled = !busy,
                    onClick = {
                        val itemId = item.id ?: return@OverflowAction
                        runAction {
                            mediaRepository.refreshItemMetadata(
                                itemId = itemId,
                                metadataRefreshMode = "FullRefresh",
                                imageRefreshMode = "FullRefresh",
                                replaceAllMetadata = true
                            )
                        }
                    }
                )
                OverflowAction(
                    icon = Icons.Outlined.Replay,
                    label = stringResource(R.string.item_action_remove_identify),
                    enabled = !busy,
                    onClick = { confirmRemoveIdentify = true }
                )
                OverflowAction(
                    icon = Icons.Outlined.Refresh,
                    label = stringResource(R.string.item_action_refresh_metadata),
                    enabled = !busy,
                    onClick = {
                        val itemId = item.id ?: return@OverflowAction
                        runAction {
                            mediaRepository.refreshItemMetadata(
                                itemId = itemId,
                                metadataRefreshMode = "FullRefresh",
                                imageRefreshMode = "Default",
                                replaceAllMetadata = false
                            )
                        }
                    }
                )
                OverflowAction(
                    icon = Icons.Outlined.Refresh,
                    label = stringResource(R.string.item_action_scan_library),
                    enabled = !busy,
                    onClick = {
                        val itemId = item.id ?: return@OverflowAction
                        runAction {
                            mediaRepository.refreshItemMetadata(
                                itemId = itemId,
                                metadataRefreshMode = "None",
                                imageRefreshMode = "None",
                                replaceAllMetadata = false
                            )
                        }
                    }
                )
                OverflowAction(
                    icon = if (isLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    label = stringResource(
                        if (isLocked) R.string.item_action_unlock_metadata else R.string.item_action_lock_metadata
                    ),
                    enabled = !busy,
                    onClick = {
                        val itemId = item.id ?: return@OverflowAction
                        runAction {
                            mediaRepository.setItemLocked(itemId, !isLocked).onSuccess {
                                onItemMutated(item.copy(lockData = !isLocked))
                            }
                        }
                    }
                )
            }
        }
    }

    if (picker != OverflowPicker.None) {
        LibraryTargetPicker(
            title = stringResource(
                if (picker == OverflowPicker.Playlist) {
                    R.string.item_action_add_playlist
                } else {
                    R.string.item_action_add_collection
                }
            ),
            createLabel = stringResource(
                if (picker == OverflowPicker.Playlist) {
                    R.string.item_action_new_playlist
                } else {
                    R.string.item_action_new_collection
                }
            ),
            loadTargets = {
                if (picker == OverflowPicker.Playlist) {
                    mediaRepository.getPlaylists()
                } else {
                    mediaRepository.getUserItems(
                        includeItemTypes = "BoxSet",
                        recursive = true,
                        sortBy = "SortName",
                        sortOrder = "Ascending",
                        fields = "ChildCount,RecursiveItemCount"
                    ).map { it.items.orEmpty().filter { item -> item.id != null } }
                }
            },
            onCreate = { name ->
                val ids = listOfNotNull(item.id)
                if (picker == OverflowPicker.Playlist) {
                    mediaRepository.createPlaylistWithItems(name, ids)
                } else {
                    mediaRepository.createCollectionWithItems(name, ids)
                }
            },
            onSelect = { targetId ->
                val ids = listOfNotNull(item.id)
                if (picker == OverflowPicker.Playlist) {
                    mediaRepository.addItemsToPlaylist(targetId, ids)
                } else {
                    mediaRepository.addItemsToCollection(targetId, ids)
                }
            },
            onDismiss = { picker = OverflowPicker.None },
            onFinished = { success ->
                picker = OverflowPicker.None
                onMessage(success)
                if (success) onDismiss()
            }
        )
    }

    if (confirmDelete) {
        ConfirmActionDialog(
            text = stringResource(R.string.item_action_confirm_delete),
            onConfirm = {
                confirmDelete = false
                val itemId = item.id ?: return@ConfirmActionDialog
                runAction {
                    mediaRepository.deleteLibraryItem(itemId).onSuccess { onItemDeleted(itemId) }
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }

    if (confirmRemoveIdentify) {
        ConfirmActionDialog(
            text = stringResource(R.string.item_action_confirm_remove_identify),
            onConfirm = {
                confirmRemoveIdentify = false
                val itemId = item.id ?: return@ConfirmActionDialog
                runAction { mediaRepository.clearItemIdentification(itemId) }
            },
            onDismiss = { confirmRemoveIdentify = false }
        )
    }
}

@Composable
private fun OverflowAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 0.92f else 0.4f),
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 0.78f else 0.3f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTargetPicker(
    title: String,
    createLabel: String,
    loadTargets: suspend () -> Result<List<BaseItemDto>>,
    onCreate: suspend (String) -> Result<String?>,
    onSelect: suspend (String) -> Result<Unit>,
    onDismiss: () -> Unit,
    onFinished: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var targets by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var newName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        targets = withContext(Dispatchers.IO) { loadTargets().getOrDefault(emptyList()) }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.item_action_name_hint)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White.copy(alpha = 0.4f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.18f)
                )
            )
            TextButton(
                onClick = {
                    val name = newName.trim()
                    if (name.isEmpty()) return@TextButton
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { onCreate(name) }
                        onFinished(result.isSuccess)
                    }
                }
            ) {
                Text(createLabel, color = Color(0xFF3AA0FF))
            }
            if (!loading && targets.isEmpty()) {
                Text(
                    text = stringResource(R.string.item_picker_empty),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            targets.forEach { target ->
                Text(
                    text = target.name.orEmpty(),
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val id = target.id ?: return@clickable
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { onSelect(id) }
                                onFinished(result.isSuccess)
                            }
                        }
                        .padding(vertical = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text, color = Color.White) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = Color(0xFFFF6B6B))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        },
        containerColor = Color(0xFF1C1C1C)
    )
}
