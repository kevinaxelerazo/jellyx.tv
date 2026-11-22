package dev.jdtech.jellyfin.film.presentation.show

import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItemPerson
import dev.jdtech.jellyfin.models.JellyxSeason
import dev.jdtech.jellyfin.models.JellyxShow
import java.util.UUID

data class ShowState(
    val show: JellyxShow? = null,
    val nextUp: JellyxEpisode? = null,
    val seasons: List<JellyxSeason> = emptyList(),
    val downloadedCountBySeason: Map<UUID, Int> = emptyMap(),
    val actors: List<JellyxItemPerson> = emptyList(),
    val director: JellyxItemPerson? = null,
    val writers: List<JellyxItemPerson> = emptyList(),
    val error: Exception? = null,
)
