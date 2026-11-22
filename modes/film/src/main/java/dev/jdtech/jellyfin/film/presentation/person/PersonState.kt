package dev.jdtech.jellyfin.film.presentation.person

import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxPerson
import dev.jdtech.jellyfin.models.JellyxShow

data class PersonState(
    val person: JellyxPerson? = null,
    val starredInMovies: List<JellyxMovie> = emptyList(),
    val starredInShows: List<JellyxShow> = emptyList(),
    val error: Exception? = null,
)
