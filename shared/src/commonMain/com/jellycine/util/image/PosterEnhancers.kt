package com.jellycine.util.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.data.repository.AuthRepository
import com.jellycine.preferences.Preferences
import kotlinx.coroutines.flow.flowOf

/** True when the server is Emby and the user turned on "Emby poster overlays". */
@Composable
fun rememberDisablePosterEnhancers(
    preferences: Preferences?,
    authRepository: AuthRepository
): Boolean {
    val serverType by authRepository.getServerType().collectAsStateWithLifecycle(initialValue = null)
    val toggleFlow = remember(preferences) { preferences?.PosterEnhancersEnabled() ?: flowOf(false) }
    val toggleEnabled by toggleFlow.collectAsStateWithLifecycle(
        initialValue = preferences?.isPosterEnhancersEnabled() ?: false
    )
    return serverType.equals("EMBY", ignoreCase = true) && toggleEnabled
}