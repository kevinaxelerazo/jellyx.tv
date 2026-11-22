package dev.jdtech.jellyfin.film.presentation.media

import dev.jdtech.jellyfin.models.JellyxCollection

sealed interface MediaAction {
    data class OnItemClick(val item: JellyxCollection) : MediaAction

    data object OnFavoritesClick : MediaAction

    data object OnRetryClick : MediaAction
}
