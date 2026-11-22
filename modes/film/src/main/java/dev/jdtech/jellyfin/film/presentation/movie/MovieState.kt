package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.models.JellyxItemPerson
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.VideoMetadata

data class MovieState(
    val movie: JellyxMovie? = null,
    val videoMetadata: VideoMetadata? = null,
    val actors: List<JellyxItemPerson> = emptyList(),
    val director: JellyxItemPerson? = null,
    val writers: List<JellyxItemPerson> = emptyList(),
    val displayExtraInfo: Boolean = false,
    val error: Exception? = null,
)
