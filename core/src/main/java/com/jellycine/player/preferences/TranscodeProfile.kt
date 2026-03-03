package com.jellycine.player.preferences

/**
 * Named transcoding profile used by quality selectors and stream URL builders.
 */
data class TranscodeProfile(
    val label: String,
    val maxBitrate: Int?,
    val maxHeight: Int?
)

object TranscodeProfiles {
    const val ORIGINAL = "Original"

    val PRESETS: List<TranscodeProfile> = listOf(
        TranscodeProfile(ORIGINAL, null, null),

        TranscodeProfile("4K (200 Mbps)", 200_000_000, 2160),
        TranscodeProfile("4K (160 Mbps)", 160_000_000, 2160),
        TranscodeProfile("4K (140 Mbps)", 140_000_000, 2160),
        TranscodeProfile("4K (120 Mbps)", 120_000_000, 2160),
        TranscodeProfile("4K (100 Mbps)", 100_000_000, 2160),
        TranscodeProfile("4K (80 Mbps)", 80_000_000, 2160),
        TranscodeProfile("4K (60 Mbps)", 60_000_000, 2160),
        TranscodeProfile("4K (40 Mbps)", 40_000_000, 2160),

        TranscodeProfile("1080p (60 Mbps)", 60_000_000, 1080),
        TranscodeProfile("1080p (50 Mbps)", 50_000_000, 1080),
        TranscodeProfile("1080p (40 Mbps)", 40_000_000, 1080),
        TranscodeProfile("1080p (30 Mbps)", 30_000_000, 1080),
        TranscodeProfile("1080p (25 Mbps)", 25_000_000, 1080),
        TranscodeProfile("1080p (15 Mbps)", 15_000_000, 1080),
        TranscodeProfile("1080p (12 Mbps)", 12_000_000, 1080),
        TranscodeProfile("1080p (10 Mbps)", 10_000_000, 1080),
        TranscodeProfile("1080p (8 Mbps)", 8_000_000, 1080),
        TranscodeProfile("1080p (6 Mbps)", 6_000_000, 1080),
        TranscodeProfile("1080p (5 Mbps)", 5_000_000, 1080),
        TranscodeProfile("1080p (4 Mbps)", 4_000_000, 1080),

        TranscodeProfile("720p (4 Mbps)", 4_000_000, 720),
        TranscodeProfile("720p (3 Mbps)", 3_000_000, 720),
        TranscodeProfile("720p (2 Mbps)", 2_000_000, 720),
        TranscodeProfile("720p (1.5 Mbps)", 1_500_000, 720),
        TranscodeProfile("720p (1 Mbps)", 1_000_000, 720),

        TranscodeProfile("480p (3 Mbps)", 3_000_000, 480),
        TranscodeProfile("480p (2 Mbps)", 2_000_000, 480),
        TranscodeProfile("480p (1 Mbps)", 1_000_000, 480),

        TranscodeProfile("360p (1 Mbps)", 1_000_000, 360)
    )

    val OPTIONS: List<String> = PRESETS.map { it.label }

    private val PRESETS_BY_LABEL: Map<String, TranscodeProfile> =
        PRESETS.associateBy { it.label }

    fun byLabel(label: String): TranscodeProfile? {
        return PRESETS_BY_LABEL[label]
    }

    fun maxHeightForOption(label: String): Int? {
        return byLabel(label)?.maxHeight
    }
}
