package com.jellycine.ui.screens.dashboard.settings

import androidx.compose.ui.*
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.resources.*
import org.jetbrains.compose.resources.stringResource
import com.jellycine.preferences.Preferences
import com.jellycine.ui.components.common.ThemeMusicSettingsItem
import com.jellycine.data.repository.AuthRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun InterfaceSettingsScreen(
    preferences: Preferences,
    authRepository: AuthRepository,
    onBackPressed: () -> Unit = {}
) {
    val currentServerType by authRepository.getServerType().collectAsStateWithLifecycle(initialValue = null)
    val isEmbyServer = currentServerType.equals("EMBY", ignoreCase = true)
    val featureCarouselEnabled by preferences.FeatureCarouselEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isFeatureCarouselEnabled()
        )
    val featureCarouselHeight by preferences.FeatureCarouselHeight()
        .collectAsStateWithLifecycle(
            initialValue = preferences.getFeatureCarouselHeight()
        )
    val autoplayTrailersEnabled by preferences.autoplayTrailersEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isAutoplayTrailersEnabled()
        )
    val themeMusicMode by preferences.themeMusicMode()
        .collectAsStateWithLifecycle(initialValue = preferences.getThemeMusicMode())
    val themeMusicVolume by preferences.themeMusicVolume()
        .collectAsStateWithLifecycle(initialValue = preferences.getThemeMusicVolume())
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
    val mergeVersionsEnabled by preferences.MergeVersionsEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isMergeVersionsEnabled()
        )
    val showReviewsEnabled by preferences.ShowReviewsEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isShowReviewsEnabled()
        )
    val seerrStudiosEnabled by preferences.SeerrStudiosEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isSeerrStudiosEnabled()
        )
    val seerrNetworksEnabled by preferences.SeerrNetworksEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isSeerrNetworksEnabled()
        )

    BackHandler { onBackPressed() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.settings_interface),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back_button),
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
                SettingsSection {
                    FeatureCarouselSettingsItem(
                        icon = Icons.Rounded.ViewCarousel,
                        title = stringResource(Res.string.interface_feature_carousel),
                        subtitle = stringResource(Res.string.interface_feature_carousel_subtitle),
                        checked = featureCarouselEnabled,
                        onCheckedChange = preferences::setFeatureCarouselEnabled,
                        selectedHeight = featureCarouselHeight,
                        onHeightSelected = preferences::setFeatureCarouselHeight,
                        autoplayTrailersChecked = autoplayTrailersEnabled,
                        onAutoplayTrailersCheckedChange = preferences::setFeatureCarouselAutoplayTrailersEnabled,
                        accentColor = Color(0xFF8B5CF6)
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    ThemeMusicSettingsItem(
                        selectedMode = themeMusicMode,
                        onModeSelected = preferences::setThemeMusicMode,
                        accentColor = Color(0xFF8B5CF6),
                        volume = themeMusicVolume,
                        onVolumeChanged = preferences::setThemeMusicVolume
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    InterfaceSwitchItem(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(Res.string.interface_continue_watching),
                        subtitle = stringResource(Res.string.interface_continue_watching_subtitle),
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
                        title = stringResource(Res.string.interface_next_up),
                        subtitle = stringResource(Res.string.interface_next_up_subtitle),
                        checked = nextUpEnabled,
                        onCheckedChange = preferences::setNextUpEnabled,
                        accentColor = Color(0xFF3B82F6)
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    InterfaceSwitchItem(
                        icon = Icons.AutoMirrored.Rounded.MergeType,
                        title = stringResource(Res.string.interface_merge_versions),
                        subtitle = stringResource(Res.string.interface_merge_versions_subtitle),
                        checked = mergeVersionsEnabled,
                        onCheckedChange = preferences::setMergeVersionsEnabled,
                        accentColor = Color(0xFF14B8A6)
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    InterfaceSwitchItem(
                        icon = Icons.Rounded.RateReview,
                        title = stringResource(Res.string.interface_show_reviews),
                        subtitle = stringResource(Res.string.interface_show_reviews_subtitle),
                        checked = showReviewsEnabled,
                        onCheckedChange = preferences::setShowReviewsEnabled,
                        accentColor = Color(0xFF6FA8FF)
                    )
                    if (isEmbyServer) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        InterfaceSwitchItem(
                            icon = Icons.Rounded.AutoFixHigh,
                            title = stringResource(Res.string.interface_emby_poster_overlays),
                            subtitle = stringResource(Res.string.interface_emby_poster_overlays_subtitle),
                            checked = posterEnhancersEnabled,
                            onCheckedChange = preferences::setPosterEnhancersEnabled,
                            accentColor = Color(0xFF10B981)
                        )
                    }
                }
            }
            item {
                SettingsLabel(
                    text = stringResource(Res.string.interface_seerr_settings)
                )
                SettingsSection {
                    InterfaceSwitchItem(
                        icon = Icons.Rounded.Business,
                        title = stringResource(Res.string.interface_seerr_studios),
                        subtitle = stringResource(Res.string.interface_seerr_studios_subtitle),
                        checked = seerrStudiosEnabled,
                        onCheckedChange = preferences::setSeerrStudiosEnabled,
                        accentColor = Color(0xFFF97316)
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    InterfaceSwitchItem(
                        icon = Icons.Rounded.Tv,
                        title = stringResource(Res.string.interface_seerr_networks),
                        subtitle = stringResource(Res.string.interface_seerr_networks_subtitle),
                        checked = seerrNetworksEnabled,
                        onCheckedChange = preferences::setSeerrNetworksEnabled,
                        accentColor = Color(0xFF06B6D4)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.72f),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun FeatureCarouselSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    selectedHeight: String,
    onHeightSelected: (String) -> Unit,
    autoplayTrailersChecked: Boolean,
    onAutoplayTrailersCheckedChange: (Boolean) -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                onCheckedChange = onCheckedChange,
                colors = accentSwitchColors(accentColor)
            )
        }

        if (checked) {
            Text(
                text = stringResource(Res.string.interface_feature_carousel_height),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            CarouselHeightSelector(
                selectedHeight = selectedHeight,
                onHeightSelected = onHeightSelected,
                accentColor = accentColor
            )
            
            Text(
                text = stringResource(Res.string.interface_feature_carousel_autoplay_trailers),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.interface_feature_carousel_autoplay_trailers_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = autoplayTrailersChecked,
                    onCheckedChange = onAutoplayTrailersCheckedChange,
                    colors = accentSwitchColors(accentColor)
                )
            }
        }
    }
}

@Composable
private fun CarouselHeightSelector(
    selectedHeight: String,
    onHeightSelected: (String) -> Unit,
    accentColor: Color
) {
    val options = listOf(
        Preferences.FEATURE_CAROUSEL_HEIGHT_LARGE to stringResource(Res.string.interface_feature_carousel_height_large),
        Preferences.FEATURE_CAROUSEL_HEIGHT_MEDIUM to stringResource(Res.string.interface_feature_carousel_height_medium),
        Preferences.FEATURE_CAROUSEL_HEIGHT_SMALL to stringResource(Res.string.interface_feature_carousel_height_small)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = selectedHeight == option.first
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = if (isSelected) accentColor else Color.Transparent
                    )
                    .clickable { onHeightSelected(option.first) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.second,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f)
                )
            }
            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )
            }
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
            onCheckedChange = onCheckedChange,
            colors = accentSwitchColors(accentColor)
        )
    }
}

