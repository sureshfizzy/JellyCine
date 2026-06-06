package com.jellycine.shared.ui.components.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.SeerrRequestState
import com.jellycine.shared.R

enum class DetailDownloadActionState {
    Idle,
    Downloading,
    Queued,
    Completed,
    Paused,
    Unavailable
}

@Composable
fun DetailPlayActionButton(
    text: String,
    isPartiallyWatched: Boolean,
    resumeProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPartiallyWatched) Color(0xFF1F1F24) else Color.White,
            contentColor = Color.Black
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val progressFraction = resumeProgress.coerceIn(0f, 1f)

            if (isPartiallyWatched) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(Color.White)
                )
            }

            DetailPlayActionButtonContent(
                text = text,
                isPartiallyWatched = isPartiallyWatched,
                iconTint = if (isPartiallyWatched) Color.White else Color.Black,
                textColor = if (isPartiallyWatched) Color.White else Color.Black,
                modifier = Modifier.fillMaxSize()
            )

            if (isPartiallyWatched) {
                DetailPlayActionButtonContent(
                    text = text,
                    isPartiallyWatched = true,
                    iconTint = Color.Black,
                    textColor = Color.Black,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(right = size.width * progressFraction) {
                                this@drawWithContent.drawContent()
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun DetailPlayActionButtonContent(
    text: String,
    isPartiallyWatched: Boolean,
    iconTint: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPartiallyWatched) {
            Icon(
                painter = painterResource(R.drawable.ic_resume_playback),
                contentDescription = stringResource(R.string.detail_continue_playback),
                modifier = Modifier.size(22.dp),
                tint = iconTint
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.size(22.dp),
                tint = iconTint
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textColor
        )
    }
}

@Composable
fun DetailDownloadActionButton(
    state: DetailDownloadActionState,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF1F1F24),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith
                    fadeOut(animationSpec = tween(180))
            },
            label = "download_button_state"
        ) { targetState ->
            when (targetState) {
                DetailDownloadActionState.Downloading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 0.99f) },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF03A9F4)
                        )
                        if (!iconOnly) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                DetailDownloadActionState.Queued -> {
                    DetailDownloadLabelContent(
                        icon = Icons.Rounded.Download,
                        label = stringResource(R.string.downloads_status_queued),
                        iconOnly = iconOnly
                    )
                }

                DetailDownloadActionState.Completed -> {
                    DetailDownloadLabelContent(
                        icon = Icons.Rounded.CheckCircle,
                        label = stringResource(R.string.downloads_status_downloaded),
                        fontSize = 12.sp,
                        tint = Color(0xFF4CAF50),
                        textColor = Color(0xFF4CAF50),
                        iconOnly = iconOnly
                    )
                }

                DetailDownloadActionState.Paused -> {
                    DetailDownloadLabelContent(
                        icon = Icons.Rounded.PauseCircle,
                        label = stringResource(R.string.downloads_status_paused),
                        tint = Color(0xFFFFC107),
                        iconOnly = iconOnly
                    )
                }

                DetailDownloadActionState.Unavailable -> {
                    DetailDownloadLabelContent(
                        icon = Icons.Rounded.Download,
                        label = stringResource(R.string.settings_unavailable),
                        iconOnly = iconOnly
                    )
                }

                DetailDownloadActionState.Idle -> {
                    DetailDownloadLabelContent(
                        icon = Icons.Rounded.Download,
                        label = stringResource(R.string.downloads_action_download),
                        iconOnly = iconOnly
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailDownloadLabelContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    tint: Color = Color.White,
    textColor: Color = Color.White,
    iconOnly: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
            tint = tint
        )
        if (!iconOnly) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SeerrRequestActionButton(
    requestState: SeerrRequestState,
    isBusy: Boolean,
    busyLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRequested = requestState == SeerrRequestState.REQUESTED
    Button(
        onClick = onClick,
        enabled = !isRequested && !isBusy,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRequested) Color(0xFF1F1F24) else Color.White,
            contentColor = if (isRequested) Color.White else Color.Black,
            disabledContainerColor = Color(0xFF1F1F24),
            disabledContentColor = Color.White.copy(alpha = 0.86f)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    ) {
        when {
            isBusy -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF03A9F4)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = busyLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            isRequested -> {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = stringResource(R.string.detail_seerr_requested),
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF9CDCFE)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_seerr_requested),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Rounded.AddCircle,
                    contentDescription = stringResource(R.string.detail_seerr_request),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_seerr_request),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FavoriteActionButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF1F1F24),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) {
                stringResource(R.string.detail_unfavorite)
            } else {
                stringResource(R.string.favorite)
            },
            modifier = Modifier.size(20.dp),
            tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White
        )
    }
}

@Composable
fun DetailTrailerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF1F1F24),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.LocalMovies,
            contentDescription = stringResource(R.string.detail_play_trailer),
            modifier = Modifier.size(22.dp),
            tint = Color.White
        )
    }
}