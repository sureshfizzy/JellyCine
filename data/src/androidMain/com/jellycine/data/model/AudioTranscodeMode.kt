package com.jellycine.data.model

enum class AudioTranscodeMode(
    val preferenceValue: String,
    val displayName: String,
    val maxAudioChannels: String
) {
    AUTO(
        preferenceValue = "auto",
        displayName = "Auto",
        maxAudioChannels = "6"
    ),
    STEREO(
        preferenceValue = "stereo",
        displayName = "Stereo",
        maxAudioChannels = "2"
    ),
    SURROUND_5_1(
        preferenceValue = "5.1",
        displayName = "5.1",
        maxAudioChannels = "6"
    ),
    PASSTHROUGH(
        preferenceValue = "passthrough",
        displayName = "Passthrough",
        maxAudioChannels = "8"
    );

    companion object {
        fun fromPreferenceValue(value: String?): AudioTranscodeMode {
            return entries.firstOrNull { mode ->
                mode.preferenceValue.equals(value, ignoreCase = true)
            } ?: AUTO
        }

        fun fromDisplayName(displayName: String?): AudioTranscodeMode {
            return entries.firstOrNull { mode ->
                mode.displayName.equals(displayName, ignoreCase = true)
            } ?: AUTO
        }
    }
}
