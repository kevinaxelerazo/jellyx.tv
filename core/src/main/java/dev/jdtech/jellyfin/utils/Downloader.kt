package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxSource
import dev.jdtech.jellyfin.models.UiText

interface Downloader {
    suspend fun downloadItem(
        item: JellyxItem,
        sourceId: String,
        storageIndex: Int = 0,
    ): Pair<Long, UiText?>

    suspend fun cancelDownload(item: JellyxItem, downloadId: Long)

    suspend fun deleteItem(item: JellyxItem, source: JellyxSource)

    suspend fun getProgress(downloadId: Long?): Pair<Int, Int>

    suspend fun reconcileCompletedDownloads() = Unit
}
