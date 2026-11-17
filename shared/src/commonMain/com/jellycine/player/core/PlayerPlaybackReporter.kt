package com.jellycine.player.core

import com.jellycine.data.repository.MediaRepository
import com.jellycine.playback.UserDataRefreshSignals
import com.jellycine.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

class PlayerPlaybackReporter(
    private val mediaRepository: MediaRepository,
    private val scope: CoroutineScope,
    private val positionProvider: () -> Long,
    private val isPausedProvider: () -> Boolean,
    private val onError: (message: String, error: Throwable?) -> Unit = { message, error ->
        println("PlayerPlaybackReporter: $message ${error ?: ""}")
    }
) {

    companion object {
        private const val TICKS_PER_MILLISECOND = 10_000L
        private const val PROGRESS_REPORT_INTERVAL_MS = 15_000L
    }

    private var session = PlaybackSessionContext()
    private var progressReportingJob: Job? = null
    private var hasReportedStart: Boolean = false

    fun updateSession(newSession: PlaybackSessionContext) {
        session = newSession
    }

    fun hasReportedStart(): Boolean = hasReportedStart

    fun reset() {
        progressReportingJob?.cancel()
        progressReportingJob = null
        hasReportedStart = false
        session = PlaybackSessionContext()
    }

    fun reportPlaybackStatus() {
        val sessionSnapshot = session
        val mediaId = sessionSnapshot.mediaId ?: return
        if (sessionSnapshot.isOfflinePlayback || hasReportedStart) return

        scope.launch {
            try {
                val result = mediaRepository.reportPlaybackStart(
                    itemId = mediaId,
                    playSessionId = sessionSnapshot.playSessionId,
                    mediaSourceId = sessionSnapshot.mediaSourceId,
                    positionTicks = positionProvider() * TICKS_PER_MILLISECOND,
                    playMethod = sessionSnapshot.playMethod.reportValue
                )
                if (result.isSuccess) {
                    hasReportedStart = true
                    startProgressReportingLoop()
                } else {
                    report(Res.string.player_report_start_failed, null, result.exceptionOrNull()?.message)
                }
            } catch (error: Exception) {
                report(Res.string.player_report_start_error, error)
            }
        }
    }

    fun reportPlaybackProgress() {
        if (!canReportProgress()) return
        scope.launch {
            reportPlaybackProgressNow()
        }
    }

    fun reportPlaybackStopped(failed: Boolean = false) {
        val sessionSnapshot = session
        val mediaId = sessionSnapshot.mediaId ?: return
        if (sessionSnapshot.isOfflinePlayback || !hasReportedStart) return

        hasReportedStart = false
        progressReportingJob?.cancel()
        progressReportingJob = null

        scope.launch {
            try {
                val result = mediaRepository.reportPlaybackStopped(
                    itemId = mediaId,
                    positionTicks = positionProvider() * TICKS_PER_MILLISECOND,
                    playSessionId = sessionSnapshot.playSessionId,
                    mediaSourceId = sessionSnapshot.mediaSourceId,
                    failed = failed
                )
                if (result.isSuccess) {
                    UserDataRefreshSignals.notifyUserDataChanged(mediaId)
                } else {
                    report(Res.string.player_report_stopped_failed, null, result.exceptionOrNull()?.message)
                }
            } catch (error: Exception) {
                report(Res.string.player_report_stopped_error, error)
            }
        }
    }

    fun onPlaybackPauseStateChanged() {
        if (hasReportedStart) {
            reportPlaybackProgress()
        }
    }

    fun onPlaybackPositionDiscontinuity() {
        if (hasReportedStart) {
            reportPlaybackProgress()
        }
    }

    private fun startProgressReportingLoop() {
        progressReportingJob?.cancel()
        progressReportingJob = scope.launch {
            while (hasReportedStart) {
                try {
                    delay(PROGRESS_REPORT_INTERVAL_MS)
                    reportPlaybackProgressNow()
                } catch (_: CancellationException) {
                    break
                } catch (error: Exception) {
                    report(Res.string.player_report_loop_error, error)
                    break
                }
            }
        }
    }

    private fun canReportProgress(): Boolean {
        return hasReportedStart &&
            !session.isOfflinePlayback &&
            !session.mediaId.isNullOrBlank()
    }

    private suspend fun reportPlaybackProgressNow() {
        val sessionSnapshot = session
        val mediaId = sessionSnapshot.mediaId ?: return
        if (sessionSnapshot.isOfflinePlayback || !hasReportedStart) return

        try {
            val result = mediaRepository.reportPlaybackProgress(
                itemId = mediaId,
                positionTicks = positionProvider() * TICKS_PER_MILLISECOND,
                playSessionId = sessionSnapshot.playSessionId,
                mediaSourceId = sessionSnapshot.mediaSourceId,
                isPaused = isPausedProvider(),
                playMethod = sessionSnapshot.playMethod.reportValue
            )
            if (result.isFailure) {
                report(Res.string.player_report_progress_failed, null, result.exceptionOrNull()?.message)
            }
        } catch (error: Exception) {
            report(Res.string.player_report_progress_error, error)
        }
    }

    private suspend fun report(resource: StringResource, error: Throwable?, detail: String? = null) {
        onError(if (detail != null) getString(resource, detail) else getString(resource), error)
    }
}
