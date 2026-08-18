package com.jellycine.app.ui.screens.admin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.jellycine.data.model.ActivityLogEntry
import com.jellycine.data.model.AdminSessionInfo
import com.jellycine.data.model.SystemInfoFull

private val CardColor = Color(0xFF0B0E12)
private val BorderColor = Color.White.copy(alpha = 0.08f)
private val SecondaryText = Color.White.copy(alpha = 0.70f)
private val AccentBlue = Color(0xFF5AA9FA)
private val AccentGreen = Color(0xFF4FD06B)
private val AccentOrange = Color(0xFFF59E0B)
private val AccentCyan = Color(0xFF22D3EE)
private val AccentPurple = Color(0xFF8B5CF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerInfoScreen(onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: AdminPanelViewModel = viewModel { AdminPanelViewModel(context) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Admin Panel", fontWeight = FontWeight.SemiBold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Server", "Activity").forEachIndexed { index, label ->
                    val selected = selectedTab == index
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.clickable { selectedTab = index }
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> ServerTab(viewModel)
                1 -> ActivityLogTab(viewModel)
            }
        }
    }
}

@Composable
private fun ServerTab(viewModel: AdminPanelViewModel) {
    val serverState by viewModel.serverInfoState.collectAsState()
    val sessionsState by viewModel.sessionsState.collectAsState()

    if (serverState.isLoading && serverState.systemInfo == null) {
        LoadingState()
        return
    }
    if (serverState.error != null && serverState.systemInfo == null) {
        ErrorState(serverState.error!!, onRetry = { viewModel.retry() })
        return
    }

    val info = serverState.systemInfo
    val nowPlaying = sessionsState.sessions.filter { it.nowPlayingItem != null }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        item { ServerHeader(info) }

        item { SectionLabel("SYSTEM") }
        item {
            InfoSectionCard {
                InfoRow(
                    icon = Icons.Rounded.Computer,
                    label = "Operating System",
                    value = info?.operatingSystemDisplayName ?: info?.operatingSystem ?: "Unknown",
                    accentColor = AccentPurple
                )
                HorizontalDivider(color = BorderColor)
                InfoRow(
                    icon = Icons.Rounded.Info,
                    label = "Server ID",
                    value = info?.id ?: "Unknown",
                    accentColor = SecondaryText
                )
            }
        }

        if (nowPlaying.isNotEmpty()) {
            item { SectionLabel("NOW PLAYING") }
            items(nowPlaying) { session ->
                SessionCard(session, viewModel)
            }
        }
    }
}

@Composable
private fun ActivityLogTab(viewModel: AdminPanelViewModel) {
    val uiState by viewModel.activityState.collectAsState()

    if (uiState.isLoading && uiState.entries.isEmpty()) {
        LoadingState()
        return
    }
    if (uiState.error != null && uiState.entries.isEmpty()) {
        ErrorState(uiState.error!!, onRetry = { viewModel.retry() })
        return
    }
    if (uiState.entries.isEmpty()) {
        EmptyState("No activity logs")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        items(uiState.entries) { entry ->
            ActivityLogCard(entry)
        }
    }
}

@Composable
private fun SessionCard(session: AdminSessionInfo, viewModel: AdminPanelViewModel) {
    val nowPlaying = session.nowPlayingItem
    val isPlaying = nowPlaying != null

    var posterUrl by remember(session.nowPlayingItem?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(session.nowPlayingItem?.id) {
        val itemId = session.nowPlayingItem?.id
        if (itemId != null) posterUrl = viewModel.getItemImageUrl(itemId)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (nowPlaying != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = nowPlaying.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 70.dp, height = 100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = buildString {
                                nowPlaying.seriesName?.let { append("$it — ") }
                                append(nowPlaying.name ?: "Unknown")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        nowPlaying.productionYear?.let {
                            Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        }
                        val positionTicks = session.playState?.positionTicks
                        val runtimeTicks = nowPlaying.runTimeTicks
                        if (positionTicks != null && runtimeTicks != null && runtimeTicks > 0) {
                            Text(
                                "${formatTicks(positionTicks)} / ${formatTicks(runtimeTicks)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText
                            )
                        }
                    }
                }

                val positionTicks = session.playState?.positionTicks
                val runtimeTicks = nowPlaying.runTimeTicks
                if (positionTicks != null && runtimeTicks != null && runtimeTicks > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = (positionTicks.toFloat() / runtimeTicks).coerceIn(0f, 1f)
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                        Surface(Modifier.fillMaxSize(), color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(2.dp)) {}
                        Surface(Modifier.fillMaxWidth(progress).height(4.dp), color = AccentBlue, shape = RoundedCornerShape(2.dp)) {}
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    color = (if (isPlaying) AccentGreen else SecondaryText).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Devices, null, tint = if (isPlaying) AccentGreen else SecondaryText, modifier = Modifier.size(16.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        buildString {
                            append(session.client ?: "Unknown")
                            session.applicationVersion?.let { append(" $it") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(session.deviceName ?: "")
                            session.remoteEndPoint?.let { if (isNotEmpty()) append("  •  "); append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (nowPlaying != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderColor)

                val playMethod = session.playState?.playMethod
                val method = when {
                    playMethod.equals("DirectPlay", ignoreCase = true) -> "Direct Play"
                    playMethod.equals("DirectStream", ignoreCase = true) -> "Direct Stream"
                    playMethod.equals("Transcode", ignoreCase = true) -> "Transcode"
                    else -> playMethod
                }

                val videoStream = nowPlaying.mediaStreams?.firstOrNull { it.type.equals("Video", ignoreCase = true) }
                val audioStream = nowPlaying.mediaStreams?.firstOrNull {
                    it.type.equals("Audio", ignoreCase = true) && it.isDefault == true
                } ?: nowPlaying.mediaStreams?.firstOrNull { it.type.equals("Audio", ignoreCase = true) }

                val bitrate = nowPlaying.bitrate
                val streamLabel = buildString {
                    append(nowPlaying.container?.uppercase() ?: "—")
                    if (bitrate != null) append(" (${bitrate / 1_000_000} mbps)")
                }
                StreamDetailRow("Stream", streamLabel, method)
                HorizontalDivider(color = BorderColor)

                if (videoStream != null) {
                    StreamDetailRow("Video", videoStream.displayTitle ?: videoStream.codec?.uppercase() ?: "—", method)
                    HorizontalDivider(color = BorderColor)
                }
                if (audioStream != null) {
                    StreamDetailRow("Audio", audioStream.displayTitle ?: audioStream.codec?.uppercase() ?: "—", method)
                    HorizontalDivider(color = BorderColor)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                session.userName ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun StreamDetailRow(label: String, value: String, method: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = AccentCyan, modifier = Modifier.width(56.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            if (method != null) {
                Text("→  $method", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            }
        }
    }
}

@Composable
private fun ActivityLogCard(entry: ActivityLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                color = AccentBlue.copy(alpha = 0.12f),
                shape = RoundedCornerShape(9.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.History, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    entry.name ?: "Unknown Event",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val overview = entry.shortOverview
                if (!overview.isNullOrBlank()) {
                    Text(overview, style = MaterialTheme.typography.bodySmall, color = SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val date = entry.date
                if (date != null) {
                    Text(formatActivityDate(date), style = MaterialTheme.typography.labelSmall, color = SecondaryText.copy(alpha = 0.7f))
                }
            }
        }
    }
}

private fun formatTicks(ticks: Long): String {
    val totalSeconds = (ticks / 10_000_000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun formatActivityDate(isoDate: String): String {
    return try {
        isoDate.substringBefore(".").substringBefore("Z").replace("T", " ").take(16)
    } catch (_: Exception) { isoDate }
}

@Composable
private fun ServerHeader(info: SystemInfoFull?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(60.dp),
            color = AccentBlue.copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.24f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Dns, null, tint = AccentBlue, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(info?.serverName ?: "Server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            color = AccentGreen.copy(alpha = 0.14f),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.28f))
        ) {
            Text("v${info?.version ?: "—"}", style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
        }
        if (info?.hasUpdateAvailable == true) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = AccentOrange.copy(alpha = 0.14f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.28f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Rounded.Update, null, tint = AccentOrange, modifier = Modifier.size(13.dp))
                    Text("Update available", style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
}

@Composable
private fun InfoSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { Column(content = content) }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(38.dp), color = accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = SecondaryText)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

@Composable
private fun LoadingState() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "offset"
    )
    val brush = Brush.linearGradient(
        listOf(Color(0xFF111111), Color(0xFF1A1A1A), Color(0xFF222222), Color(0xFF1A1A1A), Color(0xFF111111)),
        start = androidx.compose.ui.geometry.Offset(offset * 1000f, 0f),
        end = androidx.compose.ui.geometry.Offset(offset * 1000f + 600f, 0f)
    )

    @Composable fun bar(h: Dp, w: Modifier = Modifier.fillMaxWidth(), r: Dp = 16.dp) =
        Box(modifier = w.height(h).background(brush, RoundedCornerShape(r)))

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        bar(80.dp)
        bar(12.dp, Modifier.width(80.dp), 6.dp)
        bar(120.dp)
        bar(12.dp, Modifier.width(100.dp), 6.dp)
        bar(90.dp)
        bar(90.dp)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.WifiOff, null, tint = AccentOrange, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    color = AccentBlue
                ) {
                    Text(
                        "Try Again",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
    }
}