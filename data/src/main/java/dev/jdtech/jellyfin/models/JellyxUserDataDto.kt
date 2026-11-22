package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import java.util.UUID

@Entity(tableName = "userdata", primaryKeys = ["userId", "itemId"])
data class JellyxUserDataDto(
    val userId: UUID,
    val itemId: UUID,
    val played: Boolean,
    val favorite: Boolean,
    val playbackPositionTicks: Long,
    val toBeSynced: Boolean = false,
)

fun JellyxItem.toJellyxUserDataDto(userId: UUID): JellyxUserDataDto {
    return JellyxUserDataDto(
        userId = userId,
        itemId = id,
        played = played,
        favorite = favorite,
        playbackPositionTicks = playbackPositionTicks,
    )
}
