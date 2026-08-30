package com.vela.app.player.mpv

import com.vela.data.model.MediaStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MpvSubtitleLoadingTest {

    @Test
    fun embeddedAssStaysOnContainerTimeline() {
        val stream = MediaStream(
            type = "Subtitle",
            index = 2,
            codec = "ass",
            isExternal = false,
            deliveryMethod = "Embed",
            isTextSubtitleStream = true,
            supportsExternalStream = true
        )
        assertFalse(MPVPlayer.shouldFetchExternalSubtitle(stream))
        assertTrue(MPVPlayer.isTextSubtitle(stream))
    }

    @Test
    fun embeddedAssWithoutExternalFlagStaysEmbedded() {
        val stream = MediaStream(
            type = "Subtitle",
            index = 3,
            codec = "ssa",
            isExternal = false
        )
        assertFalse(MPVPlayer.shouldFetchExternalSubtitle(stream))
    }

    @Test
    fun actualExternalAssIsFetched() {
        val stream = MediaStream(
            type = "Subtitle",
            index = 3,
            codec = "ass",
            isExternal = true,
            deliveryMethod = "External"
        )
        assertTrue(MPVPlayer.shouldFetchExternalSubtitle(stream))
    }

    @Test
    fun bitmapSubtitleIsNotFetchedAsText() {
        val stream = MediaStream(
            type = "Subtitle",
            index = 4,
            codec = "hdmv_pgs_subtitle",
            isExternal = false,
            isTextSubtitleStream = false,
            supportsExternalStream = true
        )
        assertFalse(MPVPlayer.shouldFetchExternalSubtitle(stream))
        assertFalse(MPVPlayer.isTextSubtitle(stream))
    }

    @Test
    fun audioStreamIsIgnored() {
        val stream = MediaStream(
            type = "Audio",
            index = 1,
            codec = "aac"
        )
        assertFalse(MPVPlayer.shouldFetchExternalSubtitle(stream))
    }

    @Test
    fun validHttpHeadersAreForwardedWithoutRequestLineInjection() {
        assertEquals(
            "Referer: https://example.com,Cookie: session=abc",
            MPVPlayer.httpHeaderFields(
                linkedMapOf(
                    "Referer" to "https://example.com",
                    "Cookie" to "session=abc",
                    "Bad Header" to "ignored",
                    "X-Injected" to "safe\r\nX-Evil: true"
                )
            )
        )
    }

    @Test
    fun preferredIndexWinsOverDefault() {
        val streams = listOf(
            MediaStream(type = "Subtitle", index = 2, isDefault = true),
            MediaStream(type = "Subtitle", index = 3, isDefault = false)
        )
        assertEquals(
            3,
            MPVPlayer.resolvedSubtitleStreamIndex(
                preferredIndex = 3,
                mediaSourceDefaultIndex = 2,
                mediaStreams = streams
            )
        )
    }

    @Test
    fun mediaSourceDefaultIsUsedWhenPreferredMissing() {
        assertEquals(
            2,
            MPVPlayer.resolvedSubtitleStreamIndex(
                preferredIndex = null,
                mediaSourceDefaultIndex = 2,
                mediaStreams = emptyList()
            )
        )
    }

    @Test
    fun defaultFlagFillsInWhenServerIndexMissing() {
        val streams = listOf(
            MediaStream(type = "Subtitle", index = 2, isDefault = false, codec = "srt"),
            MediaStream(type = "Subtitle", index = 3, isDefault = true, codec = "ass")
        )
        assertEquals(
            3,
            MPVPlayer.resolvedSubtitleStreamIndex(
                preferredIndex = null,
                mediaSourceDefaultIndex = null,
                mediaStreams = streams
            )
        )
    }

    @Test
    fun negativeServerDefaultDoesNotDisableFlaggedTrack() {
        val streams = listOf(
            MediaStream(type = "Subtitle", index = 2, isDefault = true, codec = "ass")
        )
        assertEquals(
            2,
            MPVPlayer.resolvedSubtitleStreamIndex(
                preferredIndex = null,
                mediaSourceDefaultIndex = -1,
                mediaStreams = streams
            )
        )
    }

    @Test
    fun noSubtitleIndexWhenNothingIsSelected() {
        assertNull(
            MPVPlayer.resolvedSubtitleStreamIndex(
                preferredIndex = null,
                mediaSourceDefaultIndex = null,
                mediaStreams = listOf(
                    MediaStream(type = "Subtitle", index = 2, isDefault = false)
                )
            )
        )
    }

    @Test
    fun playbackSecretsAreRedactedFromLogs() {
        assertEquals(
            "http://host/Videos/1/Subtitles/2/0/Stream.ass?api_key=***",
            MPVPlayer.redactPlaybackSecret(
                "http://host/Videos/1/Subtitles/2/0/Stream.ass?api_key=secret-token"
            )
        )
        assertEquals(
            "Stream.ass?api_key=***",
            MPVPlayer.redactPlaybackSecret("Stream.ass?api_key=secret-token")
        )
        assertEquals("none", MPVPlayer.redactPlaybackSecret(null))
    }

    @Test
    fun httpSubtitlesAreCachedLocallySoSeekCanShowCurrentCue() {
        val cache = kotlin.io.path.createTempDirectory("mpv-subs").toFile()
        val result = MPVPlayer.materializeSubtitleFiles(
            cacheDir = cache,
            urls = mapOf(2 to "http://host/Videos/1/Subtitles/2/0/Stream.ass?api_key=secret"),
            download = { _, target ->
                target.writeText("Dialogue: 0,0:10:00.00,0:10:05.00,Default,,0,0,0,,hello")
                true
            }
        )
        val cached = File(result.getValue(2))
        assertTrue(cached.isFile)
        assertTrue(cached.path.contains("mpv-subs"))
        assertTrue(cached.name.endsWith("2.ass"))
        assertTrue(cached.readText().contains("hello"))
    }

    @Test
    fun localSubtitlePathsAreLeftAlone() {
        val cache = kotlin.io.path.createTempDirectory("mpv-subs").toFile()
        val original = mapOf(2 to "/data/data/app/cache/existing.ass")
        assertEquals(original, MPVPlayer.materializeSubtitleFiles(cache, original))
    }

    @Test
    fun failedSubtitleDownloadKeepsRemoteUrl() {
        val cache = kotlin.io.path.createTempDirectory("mpv-subs").toFile()
        val remote = "http://host/Videos/1/Subtitles/2/0/Stream.ass"
        val result = MPVPlayer.materializeSubtitleFiles(
            cacheDir = cache,
            urls = mapOf(2 to remote),
            download = { _, _ -> false }
        )
        assertEquals(remote, result.getValue(2))
    }
}
