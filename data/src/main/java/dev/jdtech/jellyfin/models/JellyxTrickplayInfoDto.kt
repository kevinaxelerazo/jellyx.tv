package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey

@Entity(
    tableName = "trickplayInfos",
    foreignKeys =
        [
            ForeignKey(
                entity = JellyxSourceDto::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("sourceId"),
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class JellyxTrickplayInfoDto(
    @PrimaryKey val sourceId: String,
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val interval: Int,
    val bandwidth: Int,
)

fun JellyxTrickplayInfo.toJellyxTrickplayInfoDto(sourceId: String): JellyxTrickplayInfoDto {
    return JellyxTrickplayInfoDto(
        sourceId = sourceId,
        width = width,
        height = height,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
        bandwidth = bandwidth,
    )
}
