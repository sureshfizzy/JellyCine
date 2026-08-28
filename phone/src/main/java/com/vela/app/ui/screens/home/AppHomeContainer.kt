package com.vela.app.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vela.app.ui.screens.dashboard.media.ContentType
import com.vela.app.ui.screens.dashboard.search.FederatedViewScreen
import com.vela.app.ui.screens.dashboard.settings.ServersScreen
import com.vela.app.ui.screens.dashboard.settings.Settings
import com.vela.data.model.BaseItemDto
import com.vela.shared.R
import com.vela.shared.ui.theme.velaMotion

private enum class AppHomeTab(
    val labelRes: Int,
    val icon: ImageVector
) {
    SERVERS(R.string.settings_server_label, Icons.Rounded.Dns),
    FEDERATED(R.string.federated_search_title, Icons.Rounded.AllInclusive),
    SETTINGS(R.string.settings, Icons.Rounded.Settings)
}

/**
 * 应用级首页不依赖某个活动服务器；Scaffold 统一管理导航栏与安全区，内容页不再猜测底栏高度。
 */
@Composable
fun AppHomeContainer(
    onServerSwitched: () -> Unit,
    onNavigateToDetail: (BaseItemDto) -> Unit,
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    onLogout: () -> Unit = {},
    onNavigateToPlayerSettings: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToConnections: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCacheSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToServerInfo: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> }
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppHomeTab.SERVERS) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 子页面各自拥有 top app bar；外层只分配底部导航空间，避免状态栏 inset 被消费两次。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppHomeNavigationBar(
                selectedTab = selectedTab,
                onSelected = { selectedTab = it }
            )
        }
    ) { contentPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)

        val motion = MaterialTheme.velaMotion
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                // 一级页签使用克制的 fade-through，保持空间稳定并明确内容已切换。
                (fadeIn(motion.defaultEffectsSpec()) +
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.985f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "app_home_tab"
        ) { activeTab ->
            when (activeTab) {
                AppHomeTab.SERVERS -> ServersScreen(
                    onServerSwitched = onServerSwitched,
                    onAddUser = onAddUser,
                    modifier = contentModifier
                )
                AppHomeTab.FEDERATED -> FederatedViewScreen(
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToLibrary = { library ->
                        val contentType = when (library.collectionType) {
                            "movies" -> ContentType.MOVIES
                            "tvshows" -> ContentType.SERIES
                            else -> ContentType.ALL
                        }
                        onNavigateToViewAll(contentType.name, library.id, library.name.orEmpty())
                    },
                    modifier = contentModifier
                )
                AppHomeTab.SETTINGS -> Settings(
                    onLogout = {
                        selectedTab = AppHomeTab.SERVERS
                        onLogout()
                    },
                    onNavigateToPlayerSettings = onNavigateToPlayerSettings,
                    onNavigateToInterfaceSettings = onNavigateToInterfaceSettings,
                    onNavigateToConnections = onNavigateToConnections,
                    onNavigateToServers = { selectedTab = AppHomeTab.SERVERS },
                    onNavigateToDownloads = onNavigateToDownloads,
                    onNavigateToCacheSettings = onNavigateToCacheSettings,
                    onNavigateToAbout = onNavigateToAbout,
                    onNavigateToServerInfo = onNavigateToServerInfo,
                    onNavigateToRequestedItem = onNavigateToDetail,
                    onAddServer = { selectedTab = AppHomeTab.SERVERS },
                    onAddUser = onAddUser,
                    modifier = contentModifier
                )
            }
        }
    }
}

@Composable
private fun AppHomeNavigationBar(
    selectedTab: AppHomeTab,
    onSelected: (AppHomeTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        AppHomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.labelRes)
                    )
                },
                label = { Text(stringResource(tab.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
