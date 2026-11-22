package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(tableName = "ratings_cache")
data class JellyxRatingsDto(
    @PrimaryKey val itemId: UUID,
    val imdbRating: Float?,
    val tmdbPercentage: Int?,
    val rtCriticPercentage: Int?,
    val rtAudiencePercentage: Int?,
    val updatedAtEpochMs: Long,
)

fun JellyxRatingsDto.toJellyxRatings(): JellyxRatings {
    val status = rtCriticPercentage?.let {
        if (it >= 60) RottenTomatoesStatus.FRESH else RottenTomatoesStatus.ROTTEN
    }
    return JellyxRatings(
        imdbRating = imdbRating,
        tmdbPercentage = tmdbPercentage,
        rtCriticPercentage = rtCriticPercentage,
        rtCriticStatus = status,
        rtAudiencePercentage = rtAudiencePercentage,
    )
}
