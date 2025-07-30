package com.jellycine.app.feature.player.core

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider

/**
 * Player ViewModel
 */
@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    private lateinit var mediaRepository: MediaRepository

    fun initializePlayer(context: Context, mediaId: String) {
        viewModelScope.launch {
            try {
                _playerState.value = _playerState.value.copy(isLoading = true, error = null)

                mediaRepository = MediaRepositoryProvider.getInstance(context)
                exoPlayer = PlayerUtils.createPlayer(context)

                val streamingResult = mediaRepository.getStreamingUrl(mediaId)
                if (streamingResult.isFailure) {
                    val error = streamingResult.exceptionOrNull()?.message ?: "Failed to get streaming URL"
                    _playerState.value = _playerState.value.copy(isLoading = false, error = error)
                    return@launch
                }

                val streamingUrl = streamingResult.getOrNull()!!
                val mediaItem = MediaItem.fromUri(streamingUrl)
                
                exoPlayer?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                    addListener(playerListener)
                }

                _playerState.value = _playerState.value.copy(isLoading = false, isPlaying = true)

            } catch (e: Exception) {
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _playerState.value = _playerState.value.copy(currentPosition = position)
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
        _playerState.value = _playerState.value.copy(volume = volume)
    }

    fun setBrightness(brightness: Float) {
        _playerState.value = _playerState.value.copy(brightness = brightness)
    }

    fun toggleControls() {
        _playerState.value = _playerState.value.copy(showControls = !_playerState.value.showControls)
    }

    fun releasePlayer() {
        exoPlayer?.apply {
            removeListener(playerListener)
            release()
        }
        exoPlayer = null
        _playerState.value = PlayerState()
    }

    fun clearError() {
        _playerState.value = _playerState.value.copy(error = null)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _playerState.value = _playerState.value.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                isPlaying = playbackState == Player.STATE_READY && exoPlayer?.playWhenReady == true
            )
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playerState.value = _playerState.value.copy(
                error = error.message ?: "Playback error occurred",
                isLoading = false,
                isPlaying = false
            )
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _playerState.value = _playerState.value.copy(
                currentPosition = newPosition.positionMs,
                duration = exoPlayer?.duration ?: 0L
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
