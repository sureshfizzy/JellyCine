package com.jellycine.player.core

enum class SkippableSegmentType {
    RECAP,
    INTRO,
    PREVIEW,
    CREDITS
}

data class SkippableSegmentAction(
    val type: SkippableSegmentType,
    val startMs: Long,
    val endMs: Long,
    val seekToMs: Long
)

fun PlayerState.findActiveSkippableSegment(positionMs: Long, durationMs: Long): SkippableSegmentAction? {
    if (durationMs <= 0L) return null

    return listOfNotNull(
        candidateSegment(SkippableSegmentType.RECAP, recapStartMs, recapEndMs, positionMs, durationMs),
        candidateSegment(SkippableSegmentType.INTRO, introStartMs, introEndMs, positionMs, durationMs),
        candidateSegment(SkippableSegmentType.PREVIEW, previewStartMs, previewEndMs, positionMs, durationMs),
        candidateSegment(SkippableSegmentType.CREDITS, creditsStartMs, creditsEndMs, positionMs, durationMs)
    ).maxByOrNull { it.startMs }
}

private fun candidateSegment(
    type: SkippableSegmentType,
    startMs: Long?,
    endMs: Long?,
    positionMs: Long,
    durationMs: Long
): SkippableSegmentAction? {
    val segmentStartMs = startMs ?: return null
    val boundedDurationMs = durationMs.takeIf { it > segmentStartMs }
    val segmentEndMs = endMs ?: boundedDurationMs ?: Long.MAX_VALUE
    if (segmentEndMs <= segmentStartMs) return null
    if (positionMs !in segmentStartMs until segmentEndMs) return null

    return SkippableSegmentAction(
        type = type,
        startMs = segmentStartMs,
        endMs = segmentEndMs,
        seekToMs = boundedDurationMs ?: endMs ?: segmentEndMs
    )
}
