package dev.jdtech.jellyfin.film.presentation.search

import dev.jdtech.jellyfin.models.JellyxItem

data class SearchState(val items: List<JellyxItem> = emptyList(), val loading: Boolean = false)
