package com.vela.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vela.app.ui.screens.dashboard.media.ContentType
import com.vela.app.ui.screens.dashboard.search.FederatedViewScreen
import com.vela.app.ui.screens.dashboard.settings.ServersScreen
import com.vela.app.ui.screens.dashboard.settings.Settings
import com.vela.data.model.BaseItemDto
import com.vela.shared.R

private enum class AppHomeTab(
    val labelRes: Int,
    val icon: ImageVector
) {
    SERVERS(R.string.settings_server_label, Icons.Rounded.Dns),
    FEDERATED(R.string.federated_search_title, Icons.Rounded.AllInclusive),
    SETTINGS(R.string.settings, Icons.Rounded.Settings)
}

/**
 * 应用级首页不依赖某个活动服务器，服务器管理、聚合搜索和本地设置都从这里进入。
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (selectedTab) {
            AppHomeTab.SERVERS -> ServersScreen(
                onServerSwitched = onServerSwitched,
                onAddUser = onAddUser,
                reserveHomeNavigationSpace = true
            )
            AppHomeTab.FEDERATED -> FederatedViewScreen(
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToLibrary = { library ->
                    val contentType = when (library.collectionType) {
                        "movies" -> ContentType.MOVIES
                        "tvshows" -> ContentType.SERIES
                        else -> ContentType.ALL
                    }
                    onNavigateToViewAll(
                        contentType.name,
                        library.id,
                        library.name ?: ""
                    )
                }
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
                onAddUser = onAddUser
            )
        }

        AppHomeNavigationBar(
            selectedTab = selectedTab,
            onSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun AppHomeNavigationBar(
    selectedTab: AppHomeTab,
    onSelected: (AppHomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .height(68.dp),
        shape = RoundedCornerShape(34.dp),
        color = Color(0xE6222329),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 14.dp
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
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
                        selectedIconColor = Color(0xFFE1E4FF),
                        selectedTextColor = Color(0xFFE1E4FF),
                        indicatorColor = Color(0xFF39436D),
                        unselectedIconColor = Color.White.copy(alpha = 0.72f),
                        unselectedTextColor = Color.White.copy(alpha = 0.72f)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
