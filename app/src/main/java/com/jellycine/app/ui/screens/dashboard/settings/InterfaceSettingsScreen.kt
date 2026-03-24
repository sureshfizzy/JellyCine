package com.jellycine.app.ui.screens.dashboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.app.R
import com.jellycine.app.preferences.Preferences
import com.jellycine.data.repository.AuthRepositoryProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettingsScreen(
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = remember { Preferences(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val currentServerType by authRepository.getServerType().collectAsStateWithLifecycle(initialValue = null)
    val isEmbyServer = currentServerType.equals("EMBY", ignoreCase = true)
    val featureCarouselEnabled by preferences.FeatureCarouselEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isFeatureCarouselEnabled()
        )
    val posterEnhancersEnabled by preferences.PosterEnhancersEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isPosterEnhancersEnabled()
        )
    val continueWatchingEnabled by preferences.ContinueWatchingEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isContinueWatchingEnabled()
        )
    val nextUpEnabled by preferences.NextUpEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isNextUpEnabled()
        )
    val useMyMediaTabEnabled by preferences.UseMyMediaTabEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isUseMyMediaTabEnabled()
        )

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_interface),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                InterfaceSection {
                    InterfaceSwitchItem(
                        icon = Icons.Rounded.ViewCarousel,
                        title = stringResource(R.string.interface_feature_carousel),
                        subtitle = stringResource(R.string.interface_feature_carousel_subtitle),
                        checked = featureCarouselEnabled,
                        onCheckedChange = preferences::setFeatureCarouselEnabled,
                        accentColor = Color(0xFF8B5CF6)
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    InterfaceSwitchItem(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(R.string.interface_continue_watching),
                        subtitle = stringResource(R.string.interface_continue_watching_subtitle),
                        checked = continueWatchingEnabled,
                        onCheckedChange = preferences::setContinueWatchingEnabled,
                        accentColor = Color(0xFFF59E0B)
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    InterfaceSwitchItem(
                        icon = Icons.Rounded.SkipNext,
                        title = stringResource(R.string.interface_next_up),
                        subtitle = stringResource(R.string.interface_next_up_subtitle),
                        checked = nextUpEnabled,
                        onCheckedChange = preferences::setNextUpEnabled,
                        accentColor = Color(0xFF3B82F6)
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    InterfaceSwitchItem(
                        icon = Icons.Rounded.VideoLibrary,
                        title = stringResource(R.string.interface_use_my_media_tab),
                        subtitle = stringResource(R.string.interface_use_my_media_tab_subtitle),
                        checked = useMyMediaTabEnabled,
                        onCheckedChange = preferences::setUseMyMediaTabEnabled,
                        accentColor = Color(0xFFEC4899)
                    )
                    if (isEmbyServer) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        InterfaceSwitchItem(
                            icon = Icons.Rounded.AutoFixHigh,
                            title = stringResource(R.string.interface_emby_poster_overlays),
                            subtitle = stringResource(R.string.interface_emby_poster_overlays_subtitle),
                            checked = posterEnhancersEnabled,
                            onCheckedChange = preferences::setPosterEnhancersEnabled,
                            accentColor = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InterfaceSection(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun InterfaceSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

