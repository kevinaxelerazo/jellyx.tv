package dev.jdtech.jellyfin.models

import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType

enum class JellyxSegmentType {
    INTRO,
    OUTRO,
    RECAP,
    PREVIEW,
    COMMERCIAL,
    UNKNOWN,
}

private fun MediaSegmentType.toJellyxSegmentType(): JellyxSegmentType =
    when (this) {
        MediaSegmentType.UNKNOWN -> JellyxSegmentType.UNKNOWN
        MediaSegmentType.INTRO -> JellyxSegmentType.INTRO
        MediaSegmentType.OUTRO -> JellyxSegmentType.OUTRO
        MediaSegmentType.RECAP -> JellyxSegmentType.RECAP
        MediaSegmentType.PREVIEW -> JellyxSegmentType.PREVIEW
        MediaSegmentType.COMMERCIAL -> JellyxSegmentType.COMMERCIAL
    }

data class JellyxSegment(val type: JellyxSegmentType, val startTicks: Long, val endTicks: Long)

fun JellyxSegmentDto.toJellyxSegment(): JellyxSegment {
    return JellyxSegment(type = type, startTicks = startTicks, endTicks = endTicks)
}

fun MediaSegmentDto.toJellyxSegment(): JellyxSegment {
    return JellyxSegment(
        type = type.toJellyxSegmentType(),
        startTicks = startTicks / 10000,
        endTicks = endTicks / 10000,
    )
}
