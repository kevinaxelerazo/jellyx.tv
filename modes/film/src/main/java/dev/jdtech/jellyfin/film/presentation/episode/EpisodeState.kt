package dev.jdtech.jellyfin.film.presentation.episode

import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItemPerson
import dev.jdtech.jellyfin.models.VideoMetadata

data class EpisodeState(
    val episode: JellyxEpisode? = null,
    val videoMetadata: VideoMetadata? = null,
    val actors: List<JellyxItemPerson> = emptyList(),
    val displayExtraInfo: Boolean = false,
    val error: Exception? = null,
)
