package dev.jdtech.jellyfin.models

import kotlin.math.roundToInt

enum class RottenTomatoesStatus {
    FRESH,
    ROTTEN,
}

data class JellyxRatings(
    val imdbRating: Float? = null,
    val tmdbPercentage: Int? = null,
    val rtCriticPercentage: Int? = null,
    val rtCriticStatus: RottenTomatoesStatus? = null,
    val rtAudiencePercentage: Int? = null,
)

fun JellyxItem.toNativeRatings(): JellyxRatings {
    val communityRating =
        when (this) {
            is JellyxMovie -> communityRating
            is JellyxShow -> communityRating
            is JellyxEpisode -> communityRating
            else -> null
        }
    val rtPct = criticRating?.roundToInt()?.coerceIn(0, 100)
    val rtStatus = rtPct?.let {
        if (it >= 60) RottenTomatoesStatus.FRESH else RottenTomatoesStatus.ROTTEN
    }
    val hasTmdbId =
        providerIds.entries.any { it.key.equals("Tmdb", ignoreCase = true) && it.value.isNotBlank() }
    val tmdbPct =
        if (hasTmdbId && communityRating != null) {
            (communityRating * 10f).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
    return JellyxRatings(
        imdbRating = communityRating,
        tmdbPercentage = tmdbPct,
        rtCriticPercentage = rtPct,
        rtCriticStatus = rtStatus,
    )
}
