package com.jellycine.data.repository

import com.jellycine.data.model.AudioTranscodeMode
import com.jellycine.data.model.DeviceProfile
import com.jellycine.data.model.DirectPlayProfile
import com.jellycine.data.model.SubtitleProfile
import com.jellycine.data.model.TranscodingProfile

internal object PlaybackDeviceProfileFactory {
    fun create(
        maxStreamingBitrate: Long? = null,
        audioTranscodeMode: AudioTranscodeMode = AudioTranscodeMode.AUTO
    ): DeviceProfile {
        val bitrate = maxStreamingBitrate?.takeIf { it > 0L }
        val maxAudioChannels = audioTranscodeMode.maxAudioChannels
        val videoTranscodeAudioCodecs = videoTranscodeAudioCodecs(audioTranscodeMode)

        return DeviceProfile(
            name = "JellyCine Android",
            maxStreamingBitrate = bitrate,
            maxStaticBitrate = bitrate,
            supportedMediaTypes = "Video,Audio",
            directPlayProfiles = listOf(
                DirectPlayProfile(
                    type = "Video",
                    container = "mp4,mkv,webm,ts,m2ts,mov,avi",
                    videoCodec = "h264,hevc,vp9,av1,mpeg4,mpeg2video,vp8",
                    audioCodec = "aac,mp3,ac3,eac3,dts,flac,opus,vorbis,truehd,pcm"
                ),
                DirectPlayProfile(
                    type = "Audio",
                    container = "mp3,m4a,aac,ogg,flac,wav,webm,mka",
                    audioCodec = "aac,mp3,ac3,eac3,dts,flac,opus,vorbis,truehd,pcm"
                )
            ),
            transcodingProfiles = listOf(
                TranscodingProfile(
                    type = "Video",
                    context = "Streaming",
                    protocol = "hls",
                    container = "ts",
                    videoCodec = "h264",
                    audioCodec = videoTranscodeAudioCodecs,
                    enableSubtitlesInManifest = true,
                    maxAudioChannels = maxAudioChannels
                ),
                TranscodingProfile(
                    type = "Audio",
                    context = "Streaming",
                    protocol = "http",
                    container = "mp3",
                    audioCodec = "mp3",
                    maxAudioChannels = "2"
                )
            ),
            subtitleProfiles = subtitleProfiles()
        )
    }

    private fun videoTranscodeAudioCodecs(audioTranscodeMode: AudioTranscodeMode): String {
        return when (audioTranscodeMode) {
            AudioTranscodeMode.STEREO -> "aac"
            AudioTranscodeMode.SURROUND_5_1 -> "eac3"
            else -> "aac,mp3,ac3,eac3"
        }
    }

    private fun subtitleProfiles(): List<SubtitleProfile> {
        val textFormats = listOf(
            "webvtt",
            "vtt",
            "srt",
            "subrip",
            "ttml",
            "ass",
            "ssa",
            "microdvd",
            "mov_text",
            "mpl2",
            "pjs",
            "realtext",
            "scc",
            "smi",
            "stl",
            "sub",
            "subviewer",
            "text",
            "vplayer",
            "xsub"
        )
        val imageFormats = listOf(
            "dvdsub",
            "idx",
            "pgs",
            "pgssub",
            "teletext",
            "vobsub"
        )

        return buildList {
            textFormats.forEach { format ->
                add(SubtitleProfile(format = format, method = "External"))
                add(SubtitleProfile(format = format, method = "Embed"))
            }
            imageFormats.forEach { format ->
                add(SubtitleProfile(format = format, method = "Embed"))
                add(SubtitleProfile(format = format, method = "Encode"))
            }
        }
    }
}
