package com.jellycine.app.ui.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.ui.theme.JellyBlue

@Composable
fun ActionButtonsSection(
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        // Play Button
        Button(
            onClick = onPlayClick,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = JellyBlue,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Play",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Download Button with Progress
        DownloadButton(
            onClick = onDownloadClick,
            isDownloading = isDownloading,
            progress = downloadProgress,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
        )
    }
}

@Composable
private fun DownloadButton(
    onClick: () -> Unit,
    isDownloading: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = if (!isDownloading) onClick else { {} },
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isDownloading) Color.Black.copy(alpha = 0.7f) else Color.Black,
                containerColor = Color.Transparent
            ),
            border = BorderStroke(
                2.dp, 
                if (isDownloading) JellyBlue.copy(alpha = 0.7f) else JellyBlue.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(16.dp),
            enabled = !isDownloading
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when {
                    isDownloading && progress < 1f -> {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF0080FF),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${(progress * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    isDownloading && progress >= 1f -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Downloaded",
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Downloaded",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Download",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}