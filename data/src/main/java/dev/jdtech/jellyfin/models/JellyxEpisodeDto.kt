package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(
    tableName = "episodes",
    foreignKeys =
        [
            ForeignKey(
                entity = JellyxSeasonDto::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("seasonId"),
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = JellyxShowDto::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("seriesId"),
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("seasonId"), Index("seriesId")],
)
data class JellyxEpisodeDto(
    @PrimaryKey val id: UUID,
    val serverId: String?,
    val seasonId: UUID,
    val seriesId: UUID,
    val name: String,
    val seriesName: String,
    val overview: String,
    val indexNumber: Int,
    val indexNumberEnd: Int?,
    val parentIndexNumber: Int,
    val runtimeTicks: Long,
    val premiereDate: LocalDateTime?,
    val communityRating: Float?,
    val chapters: List<JellyxChapter>?,
)

fun JellyxEpisode.toJellyxEpisodeDto(serverId: String? = null): JellyxEpisodeDto {
    return JellyxEpisodeDto(
        id = id,
        serverId = serverId,
        seasonId = seasonId,
        seriesId = seriesId,
        name = name,
        seriesName = seriesName,
        overview = overview,
        indexNumber = indexNumber,
        indexNumberEnd = indexNumberEnd,
        parentIndexNumber = parentIndexNumber,
        runtimeTicks = runtimeTicks,
        premiereDate = premiereDate,
        communityRating = communityRating,
        chapters = chapters,
    )
}
