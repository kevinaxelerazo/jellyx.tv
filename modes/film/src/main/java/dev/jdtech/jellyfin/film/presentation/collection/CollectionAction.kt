package dev.jdtech.jellyfin.film.presentation.collection

import dev.jdtech.jellyfin.models.JellyxItem

sealed interface CollectionAction {
    data class OnItemClick(val item: JellyxItem) : CollectionAction

    data object OnBackClick : CollectionAction
}
