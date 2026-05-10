package com.jellycine.app.player.mpv

import android.content.Context
import android.util.Log
import com.jellycine.player.preferences.PlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MpvWarmPool {
    private const val TAG = "MpvWarmPool"

    private val lock = Any()
    private var warmedPlayer: WarmedPlayer? = null
    private var warmingConfig: MpvWarmConfig? = null

    suspend fun warmIfPreferred(context: Context) {
        val appContext = context.applicationContext
        val preferences = PlayerPreferences(appContext)
        if (preferences.getPlayerEngine() != PlayerPreferences.PLAYER_ENGINE_MPV) {
            release()
            return
        }

        val config = MpvWarmConfig.from(preferences)
        var replacedPlayer: MpvPlayerController? = null
        val shouldStartWarmup = synchronized(lock) {
            if (warmedPlayer?.config == config || warmingConfig == config) {
                false
            } else {
                replacedPlayer = warmedPlayer?.controller
                warmedPlayer = null
                warmingConfig = config
                true
            }
        }
        if (!shouldStartWarmup) {
            return
        }
        replacedPlayer?.release()

        val controller = try {
            withContext(Dispatchers.Default) {
                MpvPlayerController(
                    context = appContext,
                    hardwareDecoding = config.hardwareDecoding,
                    videoOutput = config.videoOutput,
                    audioOutput = config.audioOutput,
                    listener = NoopListener
                )
            }
        } catch (error: CancellationException) {
            synchronized(lock) {
                if (warmingConfig == config) warmingConfig = null
            }
            throw error
        } catch (error: Exception) {
            synchronized(lock) {
                if (warmingConfig == config) warmingConfig = null
            }
            Log.d(TAG, "MPV warmup skipped", error)
            return
        }

        val shouldKeep = synchronized(lock) {
            val currentConfig = MpvWarmConfig.from(PlayerPreferences(appContext))
            val canKeep = warmingConfig == config &&
                currentConfig == config &&
                PlayerPreferences(appContext).getPlayerEngine() == PlayerPreferences.PLAYER_ENGINE_MPV
            warmingConfig = null
            if (canKeep) {
                warmedPlayer = WarmedPlayer(config, controller)
            }
            canKeep
        }

        if (!shouldKeep) {
            controller.release()
        }
    }

    fun acquire(
        context: Context,
        listener: MpvPlayerController.Listener
    ): MpvPlayerController? {
        val config = MpvWarmConfig.from(PlayerPreferences(context.applicationContext))
        var stalePlayer: MpvPlayerController? = null
        var player: MpvPlayerController? = null
        synchronized(lock) {
            val warmed = warmedPlayer
            if (warmed != null) {
                warmedPlayer = null
                if (warmed.config == config) {
                    player = warmed.controller
                } else {
                    stalePlayer = warmed.controller
                }
            }
        }

        stalePlayer?.release()
        player?.setListener(listener)
        return player
    }

    fun release() {
        val player = synchronized(lock) {
            warmingConfig = null
            warmedPlayer?.controller.also {
                warmedPlayer = null
            }
        }
        player?.release()
    }

    private data class WarmedPlayer(
        val config: MpvWarmConfig,
        val controller: MpvPlayerController
    )

    private data class MpvWarmConfig(
        val hardwareDecoding: String,
        val videoOutput: String,
        val audioOutput: String,
        val cacheSizeMb: Int,
        val cacheTimeSeconds: Int,
        val subtitleTextSize: String,
        val subtitleTextColor: String,
        val subtitleBackgroundColor: String,
        val subtitleEdgeType: String,
        val subtitleTextOpacityPercent: Int,
        val subtitlePosition: Int
    ) {
        companion object {
            fun from(preferences: PlayerPreferences): MpvWarmConfig {
                return MpvWarmConfig(
                    hardwareDecoding = preferences.getMpvHardwareDecoding(),
                    videoOutput = preferences.getMpvVideoOutput(),
                    audioOutput = preferences.getMpvAudioOutput(),
                    cacheSizeMb = preferences.getPlayerCacheSizeMb(),
                    cacheTimeSeconds = preferences.getPlayerCacheTimeSeconds(),
                    subtitleTextSize = preferences.getSubtitleTextSize(),
                    subtitleTextColor = preferences.getSubtitleTextColor(),
                    subtitleBackgroundColor = preferences.getSubtitleBackgroundColor(),
                    subtitleEdgeType = preferences.getSubtitleEdgeType(),
                    subtitleTextOpacityPercent = preferences.getSubtitleTextOpacityPercent(),
                    subtitlePosition = preferences.getSubtitlePosition()
                )
            }
        }
    }

    private object NoopListener : MpvPlayerController.Listener {
        override fun onBuffering() = Unit
        override fun onReady() = Unit
        override fun onEnded() = Unit
    }
}