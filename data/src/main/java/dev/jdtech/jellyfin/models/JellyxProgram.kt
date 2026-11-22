package dev.jdtech.jellyfin.models

import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class JellyxProgram(
    val id: UUID,
    val name: String,
    val overview: String?,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val channelId: UUID?,
)

fun BaseItemDto.toJellyxProgram(): JellyxProgram {
    return JellyxProgram(
        id = id,
        name = name.orEmpty(),
        overview = overview,
        startDate = startDate,
        endDate = endDate,
        channelId = channelId,
    )
}
