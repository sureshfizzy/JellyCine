package com.vela.app.ui.screens.dashboard.settings

import com.vela.app.player.mpv.MPVPlayer
import com.vela.data.model.MediaSource
import com.vela.data.model.isCloudMountPath
import com.vela.data.model.isStrmSource
import com.vela.data.model.parseStrmPlaylistUrl
import com.vela.data.model.shouldUseOriginalContainerDownload
import com.vela.data.model.strmOriginalPlaybackUrl
import com.vela.player.preferences.PlayerPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrmOriginalPathTest {

    @Test
    fun strmHttpPathIsUsed() {
        val source = MediaSource(
            container = "strm",
            path = "https://cdn.example/movie.mkv"
        )
        assertTrue(source.isStrmSource())
        assertEquals("https://cdn.example/movie.mkv", source.strmOriginalPlaybackUrl())
    }

    @Test
    fun strmFilePathIsIgnored() {
        val source = MediaSource(
            container = "strm",
            path = "/media/library/movie.strm"
        )
        assertTrue(source.isStrmSource())
        assertNull(source.strmOriginalPlaybackUrl())
    }

    @Test
    fun localFileIsNotStrm() {
        val source = MediaSource(
            container = "mkv",
            path = "/media/library/movie.mkv"
        )
        assertFalse(source.isStrmSource())
        assertNull(source.strmOriginalPlaybackUrl())
    }

    @Test
    fun strmNameWithHttpPathIsUsed() {
        val source = MediaSource(
            container = "mkv",
            name = "movie.strm",
            path = "http://192.168.0.21:5244/d/movie.mkv"
        )
        assertTrue(source.isStrmSource())
        assertEquals("http://192.168.0.21:5244/d/movie.mkv", source.strmOriginalPlaybackUrl())
    }

    @Test
    fun strmKeepsUserHardwareDecodingPreference() {
        val source = MediaSource(
            container = "strm",
            path = "https://cdn.example/movie.mkv"
        )
        assertEquals(
            PlayerPreferences.DEFAULT_MPV_HARDWARE_DECODING,
            MPVPlayer.hardwareDecodingFor(
                mediaSource = source,
                userPreference = PlayerPreferences.DEFAULT_MPV_HARDWARE_DECODING
            )
        )
        assertTrue(MPVPlayer.isRemoteHttpPlayback(source))
    }

    @Test
    fun ordinaryRemoteHttpSourceIsNotMistakenForStrm() {
        val source = MediaSource(
            container = "mkv",
            name = "movie.mkv",
            path = "https://cdn.example/movie.mkv",
            isRemote = true
        )
        assertFalse(source.isStrmSource())
        assertNull(source.strmOriginalPlaybackUrl())
    }

    @Test
    fun localFileKeepsHardwareDecodingPreference() {
        val source = MediaSource(
            container = "mkv",
            path = "/media/library/movie.mkv"
        )
        assertEquals(
            PlayerPreferences.DEFAULT_MPV_HARDWARE_DECODING,
            MPVPlayer.hardwareDecodingFor(
                mediaSource = source,
                userPreference = PlayerPreferences.DEFAULT_MPV_HARDWARE_DECODING
            )
        )
    }

    @Test
    fun parseStrmPlaylistSkipsCommentsAndBlanks() {
        val content = """
            #EXTM3U
            # comment

            https://cdn.example/movie.mkv
            https://cdn.example/ignored.mkv
        """.trimIndent()
        assertEquals("https://cdn.example/movie.mkv", parseStrmPlaylistUrl(content))
    }

    @Test
    fun parseStrmPlaylistIgnoresNonHttpLines() {
        assertNull(parseStrmPlaylistUrl("/media/library/movie.strm"))
        assertNull(parseStrmPlaylistUrl("# only a comment"))
        assertNull(parseStrmPlaylistUrl(""))
    }

    @Test
    fun cloudMountDirectPlayUsesStreamNotDownload() {
        val source = MediaSource(
            container = "mp4",
            path = "/mnt/115open/Secret/well/MIDA-764.mp4",
            supportsDirectPlay = true
        )
        assertTrue(isCloudMountPath(source.path.orEmpty()))
        assertFalse(source.shouldUseOriginalContainerDownload())
    }

    @Test
    fun localDirectPlayStillUsesOriginalContainerDownload() {
        val source = MediaSource(
            container = "mkv",
            path = "/media/library/movie.mkv",
            supportsDirectPlay = true
        )
        assertFalse(isCloudMountPath(source.path.orEmpty()))
        assertTrue(source.shouldUseOriginalContainerDownload())
    }
}
