package dev.jdtech.jellyfin.film.presentation.media

import dev.jdtech.jellyfin.models.JellyxCollection

data class MediaState(
    val libraries: List<JellyxCollection> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
