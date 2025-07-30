package com.jellycine.app.feature.player.viewmodel

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
import com.jellycine.app.feature.player.domain.model.PlayerState
import com.jellycine.app.feature.player.domain.usecase.InitializePlayerUseCase

/**
 * ViewModel for managing video player state and operations
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val initializePlayerUseCase: InitializePlayerUseCase
) : ViewModel() {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    private lateinit var mediaRepository: MediaRepository

    /**
     * Initialize the player with media content
     */
    fun initializePlayer(context: Context, mediaId: String) {
        viewModelScope.launch {
            try {
                _playerState.value = _playerState.value.copy(isLoading = true, error = null)

                // Initialize MediaRepository
                mediaRepository = MediaRepositoryProvider.getInstance(context)

                // Initialize ExoPlayer
                exoPlayer = initializePlayerUseCase.execute(context)

                // Get streaming URL from repository
                val streamingResult = mediaRepository.getStreamingUrl(mediaId)
                if (streamingResult.isFailure) {
                    val error = streamingResult.exceptionOrNull()?.message ?: "Failed to get streaming URL"
                    _playerState.value = _playerState.value.copy(
                        isLoading = false,
                        error = error
                    )
                    return@launch
                }

                val streamingUrl = streamingResult.getOrNull()!!

                // Create and set media item
                val mediaItem = MediaItem.fromUri(streamingUrl)
                exoPlayer?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true

                    // Add player listener
                    addListener(playerListener)
                }

                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    isPlaying = true
                )

            } catch (e: Exception) {
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    /**
     * Release player resources
     */
    fun releasePlayer() {
        exoPlayer?.apply {
            removeListener(playerListener)
            release()
        }
        exoPlayer = null
        _playerState.value = PlayerState()
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _playerState.value = _playerState.value.copy(error = null)
    }

    /**
     * Retry playback
     */
    fun retryPlayback() {
        exoPlayer?.let { player ->
            player.prepare()
            player.playWhenReady = true
        }
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
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
