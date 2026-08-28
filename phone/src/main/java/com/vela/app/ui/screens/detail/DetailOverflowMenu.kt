package com.vela.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.vela.data.model.BaseItemDto
import com.vela.data.repository.MediaRepository
import com.vela.shared.R
import com.vela.shared.util.image.JellyfinPosterImage
import com.vela.shared.util.image.imageTagFor
import com.vela.shared.util.image.rememberImageUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailOverflowSheet(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    canDownload: Boolean,
    hasTrailer: Boolean,
    onDismiss: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onCast: () -> Unit,
    onDownload: () -> Unit,
    onTrailer: () -> Unit,
    onItemDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
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
    val title = if (item.type.equals("Episode", true)) {
        item.seriesName?.takeIf { it.isNotBlank() } ?: item.name.orEmpty()
    } else {
        item.name.orEmpty()
    }
    val subtitle = episodeHeaderText(item)
        ?: item.productionYear?.toString()

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
                        context = context,
                        imageUrl = posterUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onIntrinsicSizeChange = { width, height ->
                            if (width > 0f && height > 0f) {
                                posterAspect = width / height
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            DetailOverflowAction(
                icon = Icons.Rounded.PlayArrow,
                label = stringResource(R.string.item_action_play_from_beginning),
                enabled = !busy,
                onClick = {
                    onDismiss()
                    onPlayFromBeginning()
                }
            )
            DetailOverflowAction(
                icon = Icons.Outlined.Cast,
                label = stringResource(R.string.detail_action_cast),
                enabled = !busy,
                onClick = {
                    onDismiss()
                    onCast()
                }
            )
            if (hasTrailer) {
                DetailOverflowAction(
                    icon = Icons.Outlined.Theaters,
                    label = stringResource(R.string.detail_play_trailer),
                    enabled = !busy,
                    onClick = {
                        onDismiss()
                        onTrailer()
                    }
                )
            }
            if (canDownload) {
                DetailOverflowAction(
                    icon = Icons.Outlined.Download,
                    label = stringResource(R.string.item_action_download),
                    enabled = !busy,
                    onClick = {
                        onDismiss()
                        onDownload()
                    }
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.08f)
            )
            DetailOverflowAction(
                icon = Icons.Outlined.Refresh,
                label = stringResource(R.string.item_action_refresh_metadata),
                enabled = !busy,
                onClick = {
                    val itemId = item.id ?: return@DetailOverflowAction
                    if (busy) return@DetailOverflowAction
                    busy = true
                    scope.launch {
                        mediaRepository.refreshItemMetadata(itemId)
                        busy = false
                        onDismiss()
                    }
                }
            )
            if (item.canDelete != false) {
                DetailOverflowAction(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(R.string.item_action_delete),
                    enabled = !busy,
                    onClick = { confirmDelete = true }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color(0xFF1A1C22),
            title = {
                Text(
                    text = stringResource(R.string.item_action_delete),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.item_action_confirm_delete),
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        val itemId = item.id ?: return@TextButton
                        busy = true
                        scope.launch {
                            mediaRepository.deleteLibraryItem(itemId).onSuccess {
                                onItemDeleted()
                            }
                            busy = false
                            onDismiss()
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel), color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun DetailOverflowAction(
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 0.86f else 0.3f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 0.92f else 0.4f),
            fontSize = 16.sp
        )
    }
}
