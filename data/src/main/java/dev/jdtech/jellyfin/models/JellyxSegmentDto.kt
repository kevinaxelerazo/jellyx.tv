package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import java.util.UUID

@Entity(tableName = "segments", primaryKeys = ["itemId", "type"])
data class JellyxSegmentDto(
    val itemId: UUID,
    val type: JellyxSegmentType,
    val startTicks: Long,
    val endTicks: Long,
)

fun JellyxSegment.toJellyxSegmentsDto(itemId: UUID): JellyxSegmentDto {
    return JellyxSegmentDto(
        itemId = itemId,
        type = type,
        startTicks = startTicks,
        endTicks = endTicks,
    )
}
