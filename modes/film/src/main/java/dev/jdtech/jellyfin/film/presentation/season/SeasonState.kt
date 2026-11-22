package dev.jdtech.jellyfin.film.presentation.season

import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxSeason

data class SeasonState(
    val season: JellyxSeason? = null,
    val episodes: List<JellyxEpisode> = emptyList(),
    val showOnlyDownloaded: Boolean = false,
    val downloadedCount: Int = 0,
    val error: Exception? = null,
)
