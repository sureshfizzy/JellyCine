package com.jellycine.app.feature.player.domain.usecase

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for initializing ExoPlayer with optimal settings for spatial audio
 */
@Singleton
class InitializePlayerUseCase @Inject constructor() {

    /**
     * Creates and configures an ExoPlayer instance with spatial audio support
     */
    fun execute(context: Context): ExoPlayer {
        // Create track selector with spatial audio preferences
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setConstrainAudioChannelCountToDeviceCapabilities(true)
                .setPreferredAudioLanguage("en")
                .build()
        }

        // Configure audio attributes for spatial audio
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // Create ExoPlayer with optimized settings
        return ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }
}
