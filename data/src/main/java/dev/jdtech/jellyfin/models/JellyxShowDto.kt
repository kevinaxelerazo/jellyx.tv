package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "shows")
data class JellyxShowDto(
    @PrimaryKey val id: UUID,
    val serverId: String?,
    val name: String,
    val originalTitle: String?,
    val overview: String,
    val runtimeTicks: Long,
    val communityRating: Float?,
    val officialRating: String?,
    val status: String,
    val productionYear: Int?,
    val endDate: LocalDateTime?,
)

fun JellyxShow.toJellyxShowDto(serverId: String? = null): JellyxShowDto {
    return JellyxShowDto(
        id = id,
        serverId = serverId,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        runtimeTicks = runtimeTicks,
        communityRating = communityRating,
        officialRating = officialRating,
        status = status,
        productionYear = productionYear,
        endDate = endDate,
    )
}
