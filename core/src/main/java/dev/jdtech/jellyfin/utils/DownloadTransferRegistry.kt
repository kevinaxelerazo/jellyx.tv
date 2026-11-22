package dev.jdtech.jellyfin.utils

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadTransferRegistry @Inject constructor() {
    data class TransferState(
        val uniqueWorkName: String,
        @Volatile var status: Int,
        @Volatile var progress: Int,
    )

    private val transfers = ConcurrentHashMap<Long, TransferState>()

    fun put(downloadId: Long, uniqueWorkName: String, status: Int, progress: Int = 0) {
        transfers[downloadId] =
            TransferState(
                uniqueWorkName = uniqueWorkName,
                status = status,
                progress = progress,
            )
    }

    fun update(downloadId: Long, status: Int, progress: Int) {
        transfers[downloadId]?.let {
            it.status = status
            it.progress = progress
        }
    }

    fun get(downloadId: Long): TransferState? = transfers[downloadId]

    fun remove(downloadId: Long): TransferState? = transfers.remove(downloadId)

    fun contains(downloadId: Long): Boolean = transfers.containsKey(downloadId)
}

