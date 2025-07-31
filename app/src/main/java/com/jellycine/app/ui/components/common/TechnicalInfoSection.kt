package com.jellycine.app.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSourceInfo
import com.jellycine.data.model.MediaStream
import com.jellycine.detail.CodecUtils

@Composable
fun TechnicalInfoSection(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
    title: String = "Technical Information"
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // File Size
                item.mediaSources?.firstOrNull()?.size?.let { size ->
                    CodecUtils.getFileSize(size)?.let { formattedSize ->
                        ModernFileInfoRow(
                            label = "File Size",
                            value = formattedSize,
                            icon = Icons.Rounded.Storage
                        )
                    }
                }

                // Video Quality
                item.mediaStreams?.firstOrNull { it.type == "Video" }?.let { videoStream ->
                    videoStream.displayTitle?.takeIf { it.isNotBlank() }?.let { videoInfo ->
                        ModernFileInfoRow(
                            label = "Video Quality",
                            value = videoInfo,
                            icon = Icons.Rounded.VideoFile
                        )
                    }
                }

                // Available Audio
                val audioInfo = item.mediaStreams?.filter { it.type == "Audio" }
                    ?.mapNotNull { it.displayTitle?.takeIf { title -> title.isNotBlank() } }
                    ?.joinToString(", ")

                if (!audioInfo.isNullOrEmpty()) {
                    ModernFileInfoRow(
                        label = "Available Audio",
                        value = audioInfo,
                        icon = Icons.AutoMirrored.Rounded.VolumeUp
                    )
                }

                // Subtitles
                val subtitleInfo = item.mediaStreams?.filter { it.type == "Subtitle" }
                    ?.mapNotNull { it.displayTitle?.takeIf { title -> title.isNotBlank() } }
                    ?.joinToString(", ")

                if (!subtitleInfo.isNullOrEmpty()) {
                    ModernFileInfoRow(
                        label = "Subtitles",
                        value = subtitleInfo,
                        icon = Icons.Rounded.Subtitles
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TechnicalInfoSectionPreview() {
    // Create mock data for preview
    val mockItem = BaseItemDto(
        id = "preview-id",
        name = "Waterfalls",
        type = "Movie",
        mediaSources = listOf(
            MediaSourceInfo(
                id = "source-1",
                size = 7079215104L // 6.6 GB
            )
        ),
        mediaStreams = listOf(
            // Video stream
            MediaStream(
                index = 0,
                type = "Video",
                codec = "hevc",
                width = 3840,
                height = 1920,
                videoRange = "HDR",
                videoRangeType = "DOVI"
            ),
            // Audio stream 1
            MediaStream(
                index = 1,
                type = "Audio",
                codec = "eac3",
                channels = 6,
                language = "eng",
                isDefault = true,
                title = "DD+ 5.1"
            ),
            // Audio stream 2
            MediaStream(
                index = 2,
                type = "Audio",
                codec = "ac3",
                channels = 2,
                language = "spa",
                isDefault = false,
                title = "DD 2.0"
            )
        )
    )

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TechnicalInfoSection(item = mockItem)
        }
    }
}