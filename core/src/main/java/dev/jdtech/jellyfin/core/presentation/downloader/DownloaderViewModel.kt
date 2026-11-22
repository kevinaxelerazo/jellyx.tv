package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.DownloadManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxSourceType
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class DownloaderViewModel @Inject constructor(private val downloader: Downloader) : ViewModel() {
    private val _state = MutableStateFlow(DownloaderState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<DownloaderEvent>()
    val events = eventsChannel.receiveAsFlow()

    var downloadId: Long? = null

    private var pollJob: Job? = null

    fun update(item: JellyxItem) {
        viewModelScope.launch {
            if (item.isDownloading()) {
                val source =
                    item.sources.firstOrNull { it.type == JellyxSourceType.LOCAL }
                        ?: return@launch
                this@DownloaderViewModel.downloadId = source.downloadId
                pollDownloadProgress(source.downloadId)
            }
        }
    }

    private fun download(item: JellyxItem, storageIndex: Int = 0) {
        viewModelScope.launch {
            _state.emit(DownloaderState(status = DownloadManager.STATUS_PENDING))
            val (downloadId, uiText) =
                downloader.downloadItem(
                    item = item,
                    sourceId = item.sources.firstOrNull()?.id.orEmpty(),
                    storageIndex = storageIndex,
                )
            if (downloadId != -1L) {
                this@DownloaderViewModel.downloadId = downloadId
                pollDownloadProgress(downloadId)
            } else {
                _state.emit(
                    DownloaderState(status = DownloadManager.STATUS_FAILED, errorText = uiText)
                )
            }
        }
    }

    private fun cancelDownload(item: JellyxItem) {
        viewModelScope.launch {
            pollJob?.cancel()
            pollJob = null

            // Cancel the download
            downloadId?.let { downloader.cancelDownload(item = item, downloadId = it) }

            // Emit empty DownloadState
            _state.emit(DownloaderState())
        }
    }

    private fun deleteDownload(item: JellyxItem) {
        viewModelScope.launch {
            downloader.deleteItem(
                item = item,
                source = item.sources.first { it.type == JellyxSourceType.LOCAL },
            )
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private fun pollDownloadProgress(downloadId: Long?) {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                while (isActive) {
                    val (status, progress) = downloader.getProgress(downloadId)
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            runCatching { downloader.reconcileCompletedDownloads() }
                            _state.emit(DownloaderState(status = status, progress = 1f))
                            eventsChannel.send(DownloaderEvent.Successful)
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            _state.emit(
                                DownloaderState(
                                    status = status,
                                    progress = 0f,
                                    errorText =
                                        _state.value.errorText
                                            ?: UiText.DynamicString(
                                                "No se pudo completar la descarga."
                                            ),
                                )
                            )
                            break
                        }
                        else -> {
                            _state.emit(
                                DownloaderState(
                                    status = status,
                                    progress = progress.coerceAtLeast(0) / 100f,
                                )
                            )
                        }
                    }
                    delay(1000L)
                }
            }
    }

    fun onAction(action: DownloaderAction) {
        when (action) {
            is DownloaderAction.Download -> download(action.item, action.storageIndex)
            is DownloaderAction.DeleteDownload -> deleteDownload(action.item)
            is DownloaderAction.CancelDownload -> cancelDownload(action.item)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
