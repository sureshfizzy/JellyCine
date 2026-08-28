package com.vela.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vela.data.model.BaseItemDto
import com.vela.data.model.ChapterInfo
import com.vela.data.model.ExternalUrl
import com.vela.data.model.MediaSourceInfo
import com.vela.data.model.MediaStream
import com.vela.data.repository.MediaRepository
import com.vela.shared.R
import com.vela.shared.util.image.JellyfinPosterImage
import com.vela.shared.util.image.rememberImageUrl
import java.util.Locale

private val DetailCardColor = Color(0xFF1C1C1F)
private val DetailMuted = Color.White.copy(alpha = 0.62f)

@Composable
internal fun SourceTrackSection(
    videoTitle: String,
    videoSubtitle: String?,
    videoOptions: List<String>,
    selectedVideo: String,
    audioLabel: String,
    audioOptions: List<String>,
    subtitleLabel: String,
    subtitleOptions: List<String>,
    onVideoOptionSelected: (String) -> Unit,
    onAudioOptionSelected: (String) -> Unit,
    onSubtitleOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val showVideo = videoTitle.isNotBlank() || videoOptions.isNotEmpty()
    val showAudio = audioLabel.isNotBlank() || audioOptions.isNotEmpty()
    val showSubtitle = subtitleOptions.size > 1
    if (!showVideo && !showAudio && !showSubtitle) return

    var picker by remember { mutableStateOf<TrackPicker?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DetailCardColor)
    ) {
        if (showVideo) {
            SourceTrackRow(
                icon = Icons.Rounded.VideoLibrary,
                title = videoTitle.ifBlank { selectedVideo },
                subtitle = videoSubtitle,
                showChevron = videoOptions.size > 1,
                onClick = {
                    if (videoOptions.size > 1) picker = TrackPicker.Video
                }
            )
        }
        if (showAudio) {
            SourceTrackRow(
                icon = Icons.Rounded.MusicNote,
                title = audioLabel.ifBlank { selectedVideo },
                subtitle = null,
                showChevron = audioOptions.size > 1,
                onClick = {
                    if (audioOptions.size > 1) picker = TrackPicker.Audio
                }
            )
        }
        if (showSubtitle) {
            SourceTrackRow(
                icon = Icons.Rounded.Subtitles,
                title = subtitleLabel,
                subtitle = null,
                showChevron = true,
                onClick = { picker = TrackPicker.Subtitle }
            )
        }
    }

    when (picker) {
        TrackPicker.Video -> TrackOptionDialog(
            title = stringResource(R.string.detail_video_fallback),
            options = videoOptions,
            selected = selectedVideo,
            onSelected = onVideoOptionSelected,
            onDismiss = { picker = null }
        )
        TrackPicker.Audio -> TrackOptionDialog(
            title = stringResource(R.string.detail_audio_fallback),
            options = audioOptions,
            selected = audioLabel,
            onSelected = onAudioOptionSelected,
            onDismiss = { picker = null }
        )
        TrackPicker.Subtitle -> TrackOptionDialog(
            title = stringResource(R.string.detail_subtitle_fallback),
            options = subtitleOptions,
            selected = subtitleLabel,
            onSelected = onSubtitleOptionSelected,
            onDismiss = { picker = null }
        )
        null -> Unit
    }
}

@Composable
private fun SourceTrackRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    showChevron: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = showChevron, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = DetailMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TrackOptionDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .width(420.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF111113)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color.White.copy(alpha = 0.10f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    onSelected(option)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option,
                                color = Color.White.copy(alpha = if (isSelected) 1f else 0.78f),
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChaptersSection(
    itemId: String?,
    chapters: List<ChapterInfo>,
    mediaRepository: MediaRepository,
    onChapterClick: (ChapterInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chapters.isEmpty() || itemId.isNullOrBlank()) return
    Column(modifier = modifier.padding(top = 22.dp)) {
        Text(
            text = stringResource(R.string.detail_chapters),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            itemsIndexed(chapters) { index, chapter ->
                ChapterCard(
                    itemId = itemId,
                    index = index,
                    chapter = chapter,
                    mediaRepository = mediaRepository,
                    onClick = { onChapterClick(chapter) }
                )
            }
        }
    }
}

@Composable
private fun ChapterCard(
    itemId: String,
    index: Int,
    chapter: ChapterInfo,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = rememberImageUrl(
        itemId = itemId,
        imageType = "Chapter/$index",
        width = 400,
        height = 225,
        quality = 80,
        imageTag = chapter.imageTag,
        mediaRepository = mediaRepository
    )
    val title = chapter.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.detail_chapter_fallback, index + 1)
    val time = formatChapterClock(chapter.startPositionTicks)

    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2A2A2A))
        ) {
            if (!imageUrl.isNullOrBlank()) {
                JellyfinPosterImage(
                    context = context,
                    imageUrl = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (time != null) {
            Text(
                text = time,
                color = DetailMuted,
                fontSize = 11.sp
            )
        }
    }
}

private data class DetailAlbumImage(
    val imageType: String,
    val imageTag: String?
)

@Composable
internal fun PhotosSection(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier
) {
    val itemId = item.id?.takeIf { it.isNotBlank() } ?: return
    val images = remember(item.id, item.backdropImageTags, item.screenshotImageTags) {
        albumImages(item)
    }
    if (images.isEmpty()) return

    var previewIndex by remember(item.id) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.padding(top = 24.dp)) {
        Text(
            text = stringResource(R.string.detail_photos),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            itemsIndexed(
                items = images,
                key = { index, image -> "${image.imageType}_${image.imageTag}_$index" }
            ) { index, image ->
                PhotoCard(
                    itemId = itemId,
                    image = image,
                    mediaRepository = mediaRepository,
                    onClick = { previewIndex = index }
                )
            }
        }
    }

    previewIndex?.let { index ->
        PhotoPreviewDialog(
            itemId = itemId,
            images = images,
            initialIndex = index,
            mediaRepository = mediaRepository,
            onDismiss = { previewIndex = null }
        )
    }
}

@Composable
private fun PhotoCard(
    itemId: String,
    image: DetailAlbumImage,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = rememberImageUrl(
        itemId = itemId,
        imageType = image.imageType,
        width = 560,
        height = 315,
        quality = 80,
        imageTag = image.imageTag,
        mediaRepository = mediaRepository
    )
    Box(
        modifier = Modifier
            .width(268.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            JellyfinPosterImage(
                context = context,
                imageUrl = imageUrl,
                contentDescription = stringResource(R.string.detail_photos),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun PhotoPreviewDialog(
    itemId: String,
    images: List<DetailAlbumImage>,
    initialIndex: Int,
    mediaRepository: MediaRepository,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size }
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val image = images[page]
                val imageUrl = rememberImageUrl(
                    itemId = itemId,
                    imageType = image.imageType,
                    width = 1920,
                    height = null,
                    quality = 95,
                    imageTag = image.imageTag,
                    mediaRepository = mediaRepository
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!imageUrl.isNullOrBlank()) {
                        JellyfinPosterImage(
                            context = context,
                            imageUrl = imageUrl,
                            contentDescription = stringResource(R.string.detail_photos),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(images.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 7.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.35f)
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun albumImages(item: BaseItemDto): List<DetailAlbumImage> {
    val backdrops = item.backdropImageTags.orEmpty().mapIndexedNotNull { index, tag ->
        tag.takeIf { it.isNotBlank() }?.let {
            DetailAlbumImage(
                imageType = "Backdrop/$index",
                imageTag = it
            )
        }
    }
    val screenshots = item.screenshotImageTags.orEmpty().mapIndexedNotNull { index, tag ->
        tag.takeIf { it.isNotBlank() }?.let {
            DetailAlbumImage(
                imageType = "Screenshot/$index",
                imageTag = it
            )
        }
    }
    return backdrops + screenshots
}

@Composable
internal fun ExternalLinksSection(
    urls: List<ExternalUrl>,
    modifier: Modifier = Modifier
) {
    val links = urls.filter { !it.url.isNullOrBlank() }
    if (links.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier.padding(top = 22.dp)) {
        Text(
            text = stringResource(R.string.detail_external_links),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(links) { _, link ->
                Surface(
                    onClick = { uriHandler.openUri(link.url.orEmpty()) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.28f)
                    )
                ) {
                    Text(
                        text = link.name?.takeIf { it.isNotBlank() } ?: link.url.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun MediaInfoSection(
    item: BaseItemDto,
    mediaSources: List<MediaSourceInfo>,
    streams: List<MediaStream>,
    smallFileSizeLabel: String,
    modifier: Modifier = Modifier
) {
    val source = mediaSources.firstOrNull()
    val videoStreams = streams.filter { it.type.equals("Video", true) }
    val audioStreams = streams.filter { it.type.equals("Audio", true) }
    if (source == null && videoStreams.isEmpty() && audioStreams.isEmpty()) return

    val sourceTitle = source?.name?.takeIf { it.isNotBlank() }
        ?: item.name?.takeIf { it.isNotBlank() }
    val dateLabel = formatDetailPremiereDate(item.premiereDate)
    val container = source?.container?.uppercase(Locale.US)
    val sizeLabel = formatDetailFileSize(source?.size, smallFileSizeLabel)
    val runtimeLabel = formatDetailRuntimeClock(item.runTimeTicks ?: source?.runTimeTicks)

    Column(modifier = modifier.padding(top = 22.dp)) {
        Text(
            text = stringResource(R.string.detail_media_info),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        if (!sourceTitle.isNullOrBlank()) {
            Text(
                text = sourceTitle,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        if (!dateLabel.isNullOrBlank()) {
            Text(
                text = dateLabel,
                color = DetailMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        val summary = listOfNotNull(container, sizeLabel, runtimeLabel)
            .filter { it.isNotBlank() }
            .joinToString("  ")
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                color = DetailMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        LazyRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(videoStreams) { _, stream ->
                MediaStreamCard(
                    icon = Icons.Rounded.Movie,
                    heading = stringResource(R.string.detail_media_video),
                    rows = videoStreamRows(stream)
                )
            }
            itemsIndexed(audioStreams) { _, stream ->
                MediaStreamCard(
                    icon = Icons.Rounded.AudioFile,
                    heading = stringResource(R.string.detail_media_audio),
                    rows = audioStreamRows(stream)
                )
            }
        }
    }
}

@Composable
private fun MediaStreamCard(
    icon: ImageVector,
    heading: String,
    rows: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .heightIn(min = 220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DetailCardColor)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = heading,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        rows.forEach { (label, value) ->
            Text(
                text = "$label: $value",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun videoStreamRows(stream: MediaStream): List<Pair<String, String>> {
    return listOfNotNull(
        stringResource(R.string.detail_media_type) to (stream.type ?: "Video"),
        stream.index?.let { stringResource(R.string.detail_media_index) to it.toString() },
        stream.displayTitle?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.detail_media_display_title) to it
        },
        stream.language?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.detail_media_language) to it
        },
        stream.codec?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.detail_media_codec) to it
        },
        formatStreamBitrate(stream.bitRate)?.let {
            stringResource(R.string.detail_media_bitrate) to it
        },
        stream.videoRange?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.detail_media_range) to it
        },
        stream.bitDepth?.let { stringResource(R.string.detail_media_bit_depth) to it.toString() },
        stream.pixelFormat?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.detail_media_pixel_format) to it
        },
        stream.width?.let { stringResource(R.string.detail_media_width) to it.toString() },
        stream.height?.let { stringResource(R.string.detail_media_height) to it.toString() },
        stream.aspectRatio?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.detail_media_aspect) to it
        },
        stream.averageFrameRate?.let {
            stringResource(R.string.detail_media_framerate) to
                String.format(Locale.US, "%s", it)
        },
        stringResource(R.string.detail_media_default) to (stream.isDefault == true).toString(),
        stringResource(R.string.detail_media_external) to (stream.isExternal == true).toString()
    )
}

@Composable
private fun audioStreamRows(stream: MediaStream): List<Pair<String, String>> {
    return listOfNotNull(
        stringResource(R.string.detail_media_type) to (stream.type ?: "Audio"),
        stream.index?.let { stringResource(R.string.detail_media_index) to it.toString() },
        stream.displayTitle?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.detail_media_display_title) to it
        },
        stream.language?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.detail_media_language) to it
        },
        stream.codec?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.detail_media_codec) to it
        },
        formatStreamBitrate(stream.bitRate)?.let {
            stringResource(R.string.detail_media_bitrate) to it
        },
        stringResource(R.string.detail_media_default) to (stream.isDefault == true).toString(),
        stringResource(R.string.detail_media_external) to (stream.isExternal == true).toString()
    )
}

internal fun formatDetailPremiereDate(premiereDate: String?): String? {
    val raw = premiereDate?.takeIf { it.isNotBlank() } ?: return null
    val datePart = raw.take(10)
    if (datePart.length == 10 && datePart[4] == '-' && datePart[7] == '-') {
        return "${datePart.substring(0, 4)}/${datePart.substring(5, 7)}/${datePart.substring(8, 10)}"
    }
    return raw
}


internal fun formatResumeClock(ticks: Long): String {
    val totalSeconds = (ticks / 10_000_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun formatDetailFileSize(sizeBytes: Long?, smallFileSizeLabel: String): String? {
    if (sizeBytes == null || sizeBytes <= 0) return null
    val gb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
    val mb = sizeBytes / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> smallFileSizeLabel
    }
}

internal fun formatDetailBitrate(bitsPerSecond: Long?): String? {
    val value = bitsPerSecond?.takeIf { it > 0L } ?: return null
    return if (value >= 1_000_000L) {
        "${String.format(Locale.US, "%.1f", value / 1_000_000.0)} Mbps"
    } else {
        "${value / 1000L} kbps"
    }
}

internal fun formatDetailRuntimeClock(ticks: Long?): String? {
    val totalSeconds = ticksToClockSeconds(ticks) ?: return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m ${seconds}s"
    }
}

@Composable
internal fun detailRuntimeLabel(ticks: Long?): String? {
    val totalSeconds = ticksToClockSeconds(ticks) ?: return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        stringResource(R.string.detail_runtime_hms, hours, minutes, seconds)
    } else {
        stringResource(R.string.detail_runtime_ms, minutes, seconds)
    }
}

internal fun mediaSourceTitle(item: BaseItemDto, sources: List<MediaSourceInfo>): String {
    sources.firstOrNull()?.name?.takeIf { it.isNotBlank() }?.let { return it }
    val episode = item.indexNumber
    val season = item.parentIndexNumber
    val name = item.name?.takeIf { it.isNotBlank() }
    return when {
        season != null && episode != null && name != null ->
            "S${"%02d".format(season)}E${"%02d".format(episode)} - $name"
        name != null -> name
        else -> ""
    }
}

internal fun chapterStartMs(chapter: ChapterInfo): Long? {
    val ticks = chapter.startPositionTicks ?: return null
    if (ticks <= 0L) return 0L
    return ticks / 10_000L
}

private fun formatChapterClock(ticks: Long?): String? {
    val totalSeconds = ticksToClockSeconds(ticks) ?: return "00:00"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatStreamBitrate(bitRate: Int?): String? {
    return formatDetailBitrate(bitRate?.toLong())
}

private fun ticksToClockSeconds(ticks: Long?): Long? {
    if (ticks == null || ticks <= 0L) return null
    return ticks / 10_000_000L
}

private enum class TrackPicker { Video, Audio, Subtitle }
