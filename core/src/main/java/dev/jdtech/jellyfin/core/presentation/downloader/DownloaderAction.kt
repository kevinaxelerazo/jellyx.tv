package dev.jdtech.jellyfin.core.presentation.downloader

import dev.jdtech.jellyfin.models.JellyxItem

sealed interface DownloaderAction {
    data class Download(val item: JellyxItem, val storageIndex: Int = 0) : DownloaderAction

    data class DeleteDownload(val item: JellyxItem) : DownloaderAction

    data class CancelDownload(val item: JellyxItem) : DownloaderAction
}
