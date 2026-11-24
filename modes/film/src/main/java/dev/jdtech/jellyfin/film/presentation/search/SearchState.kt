package dev.jdtech.jellyfin.film.presentation.search

import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxRatings
import java.util.UUID

data class SearchState(
    val items: List<JellyxItem> = emptyList(),
    val loading: Boolean = false,
    val activePlatformId: String? = null,
    val ratingsByItemId: Map<UUID, JellyxRatings> = emptyMap(),
)
