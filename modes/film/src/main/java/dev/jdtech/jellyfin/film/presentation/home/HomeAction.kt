package dev.jdtech.jellyfin.film.presentation.home

import dev.jdtech.jellyfin.models.JellyxCollection
import dev.jdtech.jellyfin.models.JellyxItem

sealed interface HomeAction {
    data class OnItemClick(val item: JellyxItem) : HomeAction

    data class OnLibraryClick(val library: JellyxCollection) : HomeAction

    data object OnRetryClick : HomeAction

    data object OnSearchClick : HomeAction

    data object OnSettingsClick : HomeAction

    data object OnManageServers : HomeAction
}
