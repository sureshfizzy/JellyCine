package com.vela.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.app.ui.components.common.DownloadActionMenu
import com.vela.app.ui.components.common.DownloadContent
import com.vela.app.ui.components.common.DownloadPanelState
import com.vela.app.ui.components.common.detailActionWidth
import com.vela.data.model.DownloadStatus
import com.vela.data.model.ItemDownloadState
import com.vela.shared.R
import com.vela.shared.ui.components.common.DetailDownloadActionButton
import com.vela.shared.ui.components.common.DetailDownloadActionState
import com.vela.shared.ui.components.common.DetailPlayActionButton
import com.vela.shared.ui.components.common.DetailTrailerButton
import com.vela.shared.ui.components.common.FavoriteActionButton

@Composable
internal fun ActionSection(
    screenWidthDp: Dp,
    useTabletLayout: Boolean,
    buttonHeight: Dp,
    playButtonText: String,
    isPartiallyWatched: Boolean,
    resumeProgress: Float,
    canDownloadItem: Boolean,
    itemDownloadState: ItemDownloadState,
    isPausedDownload: Boolean,
    hasActiveDownload: Boolean,
    downloadActionMenu: Boolean,
    downloadProgress: Float,
    onPlayClick: () -> Unit,
    onOverflowClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDownloadMenuChange: (Boolean) -> Unit,
    onPauseResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth(
                detailActionWidth(
                    screenWidthDp,
                    useTabletLayout = useTabletLayout
                )
            )
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                DetailPlayActionButton(
                    text = playButtonText,
                    isPartiallyWatched = isPartiallyWatched,
                    resumeProgress = resumeProgress,
                    onClick = onPlayClick,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (hasActiveDownload) {
                Box(modifier = Modifier.size(buttonHeight)) {
                    val downloadActionState = when {
                        itemDownloadState.status == DownloadStatus.DOWNLOADING ->
                            DetailDownloadActionState.Downloading
                        isPausedDownload -> DetailDownloadActionState.Paused
                        itemDownloadState.status == DownloadStatus.QUEUED ->
                            DetailDownloadActionState.Queued
                        else -> DetailDownloadActionState.Idle
                    }
                    DetailDownloadActionButton(
                        state = downloadActionState,
                        progress = downloadProgress,
                        onClick = {
                            if (hasActiveDownload) onDownloadMenuChange(true)
                            else onDownloadClick()
                        },
                        modifier = Modifier.fillMaxSize(),
                        iconOnly = true
                    )
                    DownloadActionMenu(
                        expanded = downloadActionMenu,
                        canResume = isPausedDownload,
                        hasActiveDownloads = hasActiveDownload,
                        onDismissRequest = { onDownloadMenuChange(false) },
                        onPauseResume = onPauseResumeDownload,
                        onCancel = onCancelDownload
                    )
                }
            }

            OutlinedButton(
                onClick = onOverflowClick,
                modifier = Modifier.size(buttonHeight),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF1F1F24),
                    contentColor = Color.White
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.detail_overflow),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
internal fun SeriesActionSection(
    screenWidthDp: Dp,
    useTabletLayout: Boolean,
    buttonHeight: Dp,
    seriesDownload: DownloadPanelState,
    seriesQueueInProgress: Boolean,
    seriesDownloadProgress: Float,
    seriesDownloadActionMenu: Boolean,
    canResumeSeriesDownloads: Boolean,
    hasActiveSeriesDownloads: Boolean,
    isFavorite: Boolean,
    hasTrailer: Boolean,
    onTrailerClick: () -> Unit,
    onSeriesDownloadClick: () -> Unit,
    onSeriesDownloadMenuChange: (Boolean) -> Unit,
    onPauseResumeSeriesDownloads: () -> Unit,
    onCancelSeriesDownloads: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(
                detailActionWidth(
                    screenWidthDp,
                    useTabletLayout = useTabletLayout
                )
            )
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight)
        ) {
            OutlinedButton(
                onClick = {
                    if (hasActiveSeriesDownloads) {
                        onSeriesDownloadMenuChange(true)
                    } else {
                        onSeriesDownloadClick()
                    }
                },
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.18f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF1F1F24),
                    contentColor = Color.White
                )
            ) {
                DownloadContent(
                    panelState = seriesDownload,
                    isQueueing = seriesQueueInProgress,
                    progress = seriesDownloadProgress,
                    idleLabelRes = R.string.downloads_action_download_series,
                    fontSize = 14.sp,
                    iconSize = 18.dp,
                    progressSize = 18.dp
                )
            }

            DownloadActionMenu(
                expanded = seriesDownloadActionMenu,
                canResume = canResumeSeriesDownloads,
                hasActiveDownloads = hasActiveSeriesDownloads,
                onDismissRequest = { onSeriesDownloadMenuChange(false) },
                onPauseResume = onPauseResumeSeriesDownloads,
                onCancel = onCancelSeriesDownloads
            )
        }

        if (hasTrailer) {
            DetailTrailerButton(
                onClick = onTrailerClick,
                size = buttonHeight
            )
        }

        FavoriteActionButton(
            isFavorite = isFavorite,
            onClick = onFavoriteClick,
            size = buttonHeight
        )
    }
}