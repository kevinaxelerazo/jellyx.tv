package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(tableName = "sources")
data class JellyxSourceDto(
    @PrimaryKey val id: String,
    val itemId: UUID,
    val name: String,
    val type: JellyxSourceType,
    val path: String,
    val downloadId: Long? = null,
)

fun JellyxSource.toJellyxSourceDto(itemId: UUID, path: String): JellyxSourceDto {
    return JellyxSourceDto(
        id = id,
        itemId = itemId,
        name = name,
        type = JellyxSourceType.LOCAL,
        path = path,
    )
}
