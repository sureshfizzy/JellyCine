package com.jellycine.app.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jellycine.app.R
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.repository.AuthRepository
import kotlinx.coroutines.launch

private fun AuthRepository.SavedServer.isActiveServer(activeServerId: String?): Boolean {
    return id == activeServerId
}

@Stable
internal class ServerSwitchDialogsState {
    var showServerSwitchDialog by mutableStateOf(false)
        private set
    var showUserSwitchDialog by mutableStateOf(false)
        private set
    var userSwitchUsers by mutableStateOf<List<AuthRepository.SavedServer>>(emptyList())
        private set
    var userSwitchServerName by mutableStateOf<String?>(null)
        private set
    var userSwitchServerUrl by mutableStateOf<String?>(null)
        private set
    var serverPendingRemoval by mutableStateOf<AuthRepository.SavedServer?>(null)
        private set

    fun openServers() {
        showServerSwitchDialog = true
    }

    fun dismissServers() {
        showServerSwitchDialog = false
    }

    fun openUsers(serverName: String?, users: List<AuthRepository.SavedServer>) {
        showServerSwitchDialog = false
        userSwitchServerName = serverName
        userSwitchServerUrl = users.firstOrNull()?.serverUrl
        userSwitchUsers = users
        showUserSwitchDialog = true
    }

    fun dismissUsers() {
        showUserSwitchDialog = false
        userSwitchUsers = emptyList()
        userSwitchServerName = null
        userSwitchServerUrl = null
    }

    fun returnToServers() {
        showUserSwitchDialog = false
        userSwitchUsers = emptyList()
        userSwitchServerName = null
        userSwitchServerUrl = null
        showServerSwitchDialog = true
    }

    fun requestRemoval(server: AuthRepository.SavedServer) {
        serverPendingRemoval = server
    }

    fun clearRemoval() {
        serverPendingRemoval = null
    }
}

@Composable
internal fun rememberServerSwitchDialogsState(): ServerSwitchDialogsState {
    return remember { ServerSwitchDialogsState() }
}

@Composable
internal fun ServerSwitchDialogsHost(
    state: ServerSwitchDialogsState,
    savedServers: List<AuthRepository.SavedServer>,
    activeServerId: String?,
    currentServerName: String?,
    currentServerUrl: String?,
    isSwitching: Boolean,
    onAddServer: () -> Unit,
    onAddUser: (serverUrl: String, serverName: String?) -> Unit,
    onServerSelected: (AuthRepository.SavedServer, () -> Unit) -> Unit,
    onRequestRemoveServer: (AuthRepository.SavedServer) -> Unit = {},
    onRequestRemoveUser: (AuthRepository.SavedServer) -> Unit = {},
    onRemoveServer: ((String, () -> Unit) -> Unit)? = null,
    showRemoveAction: Boolean = true,
    dismissServerDialogOnRequest: Boolean = true,
    dismissUserDialogOnRequest: Boolean = true,
    showServerCloseAction: Boolean = true,
    onServerDialogDismiss: (() -> Unit)? = null,
    onUserDialogDismiss: (() -> Unit)? = null
) {
    val dismissServers = onServerDialogDismiss ?: state::dismissServers
    val dismissUsers = onUserDialogDismiss ?: state::dismissUsers

    if (state.showServerSwitchDialog) {
        ServerSwitchDialog(
            servers = savedServers,
            activeServerId = activeServerId,
            isSwitching = isSwitching,
            showRemoveAction = showRemoveAction,
            dismissOnRequest = dismissServerDialogOnRequest,
            showCloseAction = showServerCloseAction,
            onDismiss = dismissServers,
            onAddServer = {
                state.dismissServers()
                onAddServer()
            },
            onRequestRemoveServer = onRequestRemoveServer,
            onOpenServerUsers = { serverName, users ->
                state.openUsers(serverName, users)
            },
            onServerSelected = { server ->
                onServerSelected(server, state::dismissServers)
            }
        )
    }

    if (state.showUserSwitchDialog) {
        UserSwitchDialog(
            users = state.userSwitchUsers,
            activeServerId = activeServerId,
            serverName = state.userSwitchServerName,
            isSwitching = isSwitching,
            showRemoveAction = showRemoveAction,
            dismissOnRequest = dismissUserDialogOnRequest,
            onDismiss = dismissUsers,
            onAddUser = {
                val targetServerUrl = state.userSwitchServerUrl ?: currentServerUrl
                targetServerUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { serverUrl ->
                        state.dismissUsers()
                        onAddUser(serverUrl, state.userSwitchServerName ?: currentServerName)
                    }
            },
            onRequestRemoveUser = onRequestRemoveUser,
            onUserSelected = { server ->
                onServerSelected(server, state::dismissUsers)
            }
        )
    }

    if (showRemoveAction && onRemoveServer != null) {
        state.serverPendingRemoval?.let { server ->
            RemoveServerConfirmDialog(
                server = server,
                isRemoving = isSwitching,
                onDismiss = {
                    if (!isSwitching) {
                        state.clearRemoval()
                    }
                },
                onConfirm = {
                    onRemoveServer(server.id, state::clearRemoval)
                }
            )
        }
    }
}

@Composable
internal fun ProfileImageLoader(
    imageUrl: String?,
    serverTypeRaw: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    val profileRequest = remember(imageUrl) {
        imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(300)
                .build()
        }
    }

    LaunchedEffect(profileRequest) {
        hasError = false
    }
    val avatarImage = profileRequest == null || hasError
    val placeholderResId = remember(serverTypeRaw) {
        when {
            serverTypeRaw.equals("EMBY", ignoreCase = true) -> R.drawable.ic_emby_placeholder
            else -> R.drawable.ic_jellyfin_placeholder
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (avatarImage) {
            Image(
                painter = painterResource(id = placeholderResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.62f)
            )
        } else {
            AsyncImage(
                model = profileRequest,
                contentDescription = stringResource(R.string.settings_profile_picture),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                onError = {
                    hasError = true
                }
            )
        }
    }
}

@Composable
private fun AmoledDialogFrame(
    dismissOnRequest: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val sheetInteractionSource = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = {
            if (dismissOnRequest) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissOnRequest,
            dismissOnClickOutside = dismissOnRequest,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f))
                .clickable(
                    enabled = dismissOnRequest,
                    indication = null,
                    interactionSource = scrimInteractionSource
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 880.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .blur(64.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x2211B6FF),
                                    Color(0x1400E5FF),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(36.dp)
                        )
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = sheetInteractionSource
                        ) {},
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFF020202),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF090909),
                                        Color(0xFF020202)
                                    )
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
internal fun ServerSwitchDialog(
    servers: List<AuthRepository.SavedServer>,
    activeServerId: String?,
    isSwitching: Boolean,
    showRemoveAction: Boolean = true,
    dismissOnRequest: Boolean = true,
    showCloseAction: Boolean = true,
    onDismiss: () -> Unit,
    onAddServer: () -> Unit,
    onRequestRemoveServer: (AuthRepository.SavedServer) -> Unit,
    onOpenServerUsers: (String, List<AuthRepository.SavedServer>) -> Unit,
    onServerSelected: (AuthRepository.SavedServer) -> Unit
) {
    val serverGroups = remember(servers, activeServerId) {
        servers
            .groupBy { NetworkModule.canonicalServerUrlKey(it.serverUrl) }
            .map { (_, groupedUsers) ->
                val sortedUsers = groupedUsers.sortedWith(
                    compareByDescending<AuthRepository.SavedServer> {
                        if (it.isActiveServer(activeServerId)) 1 else 0
                    }.thenBy { it.username.lowercase() }
                )
                val activeUser = sortedUsers.firstOrNull { it.isActiveServer(activeServerId) }
                val primary = activeUser ?: sortedUsers.first()
                ServerGroupUiModel(
                    serverName = primary.serverName,
                    serverUrl = primary.serverUrl,
                    users = sortedUsers,
                    activeUser = activeUser
                )
            }
            .sortedWith(
                compareByDescending<ServerGroupUiModel> { if (it.activeUser != null) 1 else 0 }
                    .thenBy { it.serverName.lowercase() }
            )
    }

    AmoledDialogFrame(
        dismissOnRequest = dismissOnRequest,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.settings_switch_server),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (serverGroups.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_saved_servers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    serverGroups.forEachIndexed { index, group ->
                        val hasMultipleUsers = group.users.size > 1
                        val singleUser = group.users.firstOrNull()
                        val clickGroup = !isSwitching && (hasMultipleUsers || singleUser != null)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = clickGroup) {
                                    if (hasMultipleUsers) {
                                        onOpenServerUsers(group.serverName, group.users)
                                    } else {
                                        singleUser?.let(onServerSelected)
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.serverName.ifBlank { stringResource(R.string.settings_media_server) },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (group.activeUser != null) Color(0xFF4FD06B) else Color.White
                                )
                                Text(
                                    text = group.serverUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                                if (hasMultipleUsers) {
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.settings_saved_users_count,
                                            group.users.size,
                                            group.users.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.58f)
                                    )
                                }
                            }
                            when {
                                isSwitching && group.activeUser != null -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF4FD06B)
                                    )
                                }

                                hasMultipleUsers -> {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = stringResource(R.string.settings_change_user),
                                        tint = Color.White.copy(alpha = 0.48f)
                                    )
                                }

                                singleUser?.isActiveServer(activeServerId) == true -> {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.settings_active_server),
                                        tint = Color(0xFF4FD06B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                else -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (showRemoveAction && singleUser != null) {
                                            IconButton(
                                                enabled = !isSwitching,
                                                onClick = { onRequestRemoveServer(singleUser) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = stringResource(R.string.settings_remove_user),
                                                    tint = Color(0xFFFF6B6B),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.48f)
                                        )
                                    }
                                }
                            }
                        }
                        if (index < serverGroups.lastIndex) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (showCloseAction) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showCloseAction) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_close), color = Color(0xFFD0D0D0))
                    }
                }

                TextButton(
                    enabled = !isSwitching,
                    onClick = onAddServer
                ) {
                    Text(stringResource(R.string.settings_add_server), color = Color(0xFFF97316))
                }
            }
        }
    }
}

private data class ServerGroupUiModel(
    val serverName: String,
    val serverUrl: String,
    val users: List<AuthRepository.SavedServer>,
    val activeUser: AuthRepository.SavedServer?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserSwitchDialog(
    users: List<AuthRepository.SavedServer>,
    activeServerId: String?,
    serverName: String?,
    isSwitching: Boolean,
    showRemoveAction: Boolean = true,
    dismissOnRequest: Boolean = true,
    onDismiss: () -> Unit,
    onAddUser: () -> Unit,
    onRequestRemoveUser: (AuthRepository.SavedServer) -> Unit,
    onUserSelected: (AuthRepository.SavedServer) -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (dismissOnRequest) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissOnRequest,
            dismissOnClickOutside = dismissOnRequest,
            usePlatformDefaultWidth = false
        )
    ) {
        val scrimInteractionSource = remember { MutableInteractionSource() }
        val sheetInteractionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f))
                .clickable(
                    enabled = dismissOnRequest,
                    indication = null,
                    interactionSource = scrimInteractionSource
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 880.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .blur(64.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x2211B6FF),
                                    Color(0x1400E5FF),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(36.dp)
                        )
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = sheetInteractionSource
                        ) {},
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFF020202),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF090909),
                                        Color(0xFF020202)
                                    )
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = serverName?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = stringResource(R.string.settings_whos_watching),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            if (users.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_no_saved_users_for_server),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            } else {
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val compactLayout = maxWidth < 640.dp
                                    val activeUserIndex = remember(users, activeServerId) {
                                        users.indexOfFirst { it.isActiveServer(activeServerId) }.coerceAtLeast(0)
                                    }

                                    if (compactLayout) {
                                        val listState = rememberLazyListState()
                                        val scope = rememberCoroutineScope()
                                        val showLeftIndicator by remember(listState) {
                                            derivedStateOf { listState.canScrollBackward }
                                        }
                                        val showRightIndicator by remember(listState) {
                                            derivedStateOf { listState.canScrollForward }
                                        }

                                        LaunchedEffect(users, activeUserIndex) {
                                            if (users.isNotEmpty()) {
                                                listState.scrollToItem(activeUserIndex)
                                            }
                                        }

                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            LazyRow(
                                                state = listState,
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                                                contentPadding = PaddingValues(horizontal = 10.dp)
                                            ) {
                                                items(items = users, key = { it.id }) { user ->
                                                    WhoWatchingUserCard(
                                                        user = user,
                                                        activeServerId = activeServerId,
                                                        isSwitching = isSwitching,
                                                        showRemoveAction = showRemoveAction,
                                                        onUserSelected = onUserSelected,
                                                        onRequestRemoveUser = onRequestRemoveUser,
                                                        modifier = Modifier.width(104.dp)
                                                    )
                                                }
                                            }

                                            if (showLeftIndicator) {
                                                WhoWatchingScrollIndicator(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterStart)
                                                        .padding(start = 4.dp),
                                                    rotateDegrees = 180f,
                                                    onClick = {
                                                        val targetIndex =
                                                            (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                                        scope.launch {
                                                            listState.animateScrollToItem(targetIndex)
                                                        }
                                                    }
                                                )
                                            }

                                            if (showRightIndicator) {
                                                WhoWatchingScrollIndicator(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterEnd)
                                                        .padding(end = 4.dp),
                                                    onClick = {
                                                        val targetIndex =
                                                            (listState.firstVisibleItemIndex + 1)
                                                                .coerceAtMost((users.lastIndex).coerceAtLeast(0))
                                                        scope.launch {
                                                            listState.animateScrollToItem(targetIndex)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        val maxItemsPerRow = if (maxWidth >= 920.dp) 5 else 4

                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(
                                                space = 28.dp,
                                                alignment = Alignment.CenterHorizontally
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(24.dp),
                                            maxItemsInEachRow = maxItemsPerRow
                                        ) {
                                            users.forEach { user ->
                                                WhoWatchingUserCard(
                                                    user = user,
                                                    activeServerId = activeServerId,
                                                    isSwitching = isSwitching,
                                                    showRemoveAction = showRemoveAction,
                                                    onUserSelected = onUserSelected,
                                                    onRequestRemoveUser = onRequestRemoveUser,
                                                    modifier = Modifier.width(112.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            OutlinedButton(
                                enabled = !isSwitching,
                                onClick = onAddUser,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    containerColor = Color(0x14000000)
                                ),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonAddAlt1,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.settings_add_user),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
private fun WhoWatchingScrollIndicator(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotateDegrees: Float = 0f
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick
            )
            .background(
                color = Color.Black.copy(alpha = 0.48f),
                shape = CircleShape
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.82f),
            modifier = Modifier
                .size(16.dp)
                .rotate(rotateDegrees)
        )
    }
}

@Composable
private fun WhoWatchingUserCard(
    user: AuthRepository.SavedServer,
    activeServerId: String?,
    isSwitching: Boolean,
    showRemoveAction: Boolean,
    onUserSelected: (AuthRepository.SavedServer) -> Unit,
    onRequestRemoveUser: (AuthRepository.SavedServer) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActiveUser = user.isActiveServer(activeServerId)
    val canSelect = !isSwitching
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.clickable(
            enabled = canSelect,
            indication = null,
            interactionSource = interactionSource
        ) { onUserSelected(user) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ProfileImageLoader(
                    imageUrl = user.profileImageUrl,
                    serverTypeRaw = user.serverTypeRaw,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }

            if (showRemoveAction && !isActiveUser) {
                FilledIconButton(
                    enabled = !isSwitching,
                    onClick = { onRequestRemoveUser(user) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xCC1B2133),
                        contentColor = Color(0xFFFF7B7B),
                        disabledContainerColor = Color(0x881B2133),
                        disabledContentColor = Color(0x66FF7B7B)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.settings_remove_user),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = user.username.ifBlank { stringResource(R.string.settings_unknown_username) },
            style = MaterialTheme.typography.titleSmall,
            color = if (isActiveUser) Color(0xFF4FD06B) else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier.height(18.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isSwitching && isActiveUser -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF4FD06B)
                    )
                }

                isActiveUser -> {
                    Text(
                        text = stringResource(R.string.settings_watching),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD0D0D0)
                    )
                }
            }
        }
    }
}

@Composable
internal fun RemoveServerConfirmDialog(
    server: AuthRepository.SavedServer,
    isRemoving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(stringResource(R.string.settings_remove_saved_account)) },
        text = {
            Text(
                text = stringResource(
                    R.string.settings_remove_saved_account_message,
                    server.username,
                    server.serverName
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isRemoving,
                onClick = onConfirm
            ) {
                if (isRemoving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFF6B6B)
                    )
                } else {
                    Text(stringResource(R.string.settings_remove), color = Color(0xFFFF6B6B))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isRemoving,
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel), color = Color(0xFF22D3EE))
            }
        }
    )
}
