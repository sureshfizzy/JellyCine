package com.vela.data.repository

import com.vela.data.model.AudioTranscodeMode
import com.vela.data.model.DeviceProfile
import com.vela.data.model.DirectPlayProfile
import com.vela.data.model.SubtitleProfile
import com.vela.data.model.TranscodingProfile

internal object PlaybackDeviceProfileFactory {
    fun create(
        maxStreamingBitrate: Long? = null,
        audioTranscodeMode: AudioTranscodeMode = AudioTranscodeMode.AUTO
    ): DeviceProfile {
        val bitrate = maxStreamingBitrate?.takeIf { it > 0L }
        val maxAudioChannels = audioTranscodeMode.maxAudioChannels
        val videoTranscodeAudioCodecs = videoTranscodeAudioCodecs(audioTranscodeMode)

        return DeviceProfile(
            name = "Vela Android",
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
                    type = "Video",
                    context = "Streaming",
                    protocol = "hls",
                    container = "mp4",
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
            AudioTranscodeMode.PASSTHROUGH -> "ac3,eac3"
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
                // MPV 可直接读取容器内文本轨；ASS/SSA 只声明 Embed，避免服务端优先抽取后丢失字体/时间基准。
                add(SubtitleProfile(format = format, method = "Embed"))
                if (format != "ass" && format != "ssa") {
                    add(SubtitleProfile(format = format, method = "External"))
                }
            }
            imageFormats.forEach { format ->
                add(SubtitleProfile(format = format, method = "Embed"))
                add(SubtitleProfile(format = format, method = "Encode"))
            }
        }
    }
}
