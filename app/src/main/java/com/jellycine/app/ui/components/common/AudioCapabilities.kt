package com.jellycine.app.ui.components.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jellycine.detail.AudioCapabilities
import com.jellycine.detail.CodecCapabilityManager

/**
 * Composable function to remember audio capabilities
 */
@Composable
fun rememberAudioCapabilities(context: Context): AudioCapabilities {
    return remember {
        CodecCapabilityManager.detectDeviceAudioCapabilities(context)
    }
}
