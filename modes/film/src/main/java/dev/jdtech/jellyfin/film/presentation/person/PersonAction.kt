package dev.jdtech.jellyfin.film.presentation.person

import dev.jdtech.jellyfin.models.JellyxItem

sealed interface PersonAction {
    data object NavigateBack : PersonAction

    data object NavigateHome : PersonAction

    data class NavigateToItem(val item: JellyxItem) : PersonAction
}
