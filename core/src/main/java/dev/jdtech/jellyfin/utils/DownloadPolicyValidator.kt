package dev.jdtech.jellyfin.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.StatFs
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxSource
import java.io.File
import org.jellyfin.sdk.model.api.MediaStreamType

sealed interface DownloadPolicyResult {
    data object Allowed : DownloadPolicyResult

    data class Denied(val reason: String) : DownloadPolicyResult
}

object DownloadPolicyValidator {
    const val MIN_FREE_STORAGE_BYTES: Long = 2L * 1024L * 1024L * 1024L // 2 GB

    fun validateDeviceAndStorage(
        context: Context,
        storageDir: File,
        availableBytesOverride: Long? = null,
    ): DownloadPolicyResult {
        if (isTelevisionDevice(context)) {
            return DownloadPolicyResult.Denied("Las descargas están deshabilitadas en Android TV.")
        }

        val availableBytes = availableBytesOverride ?: StatFs(storageDir.path).availableBytes
        if (availableBytes < MIN_FREE_STORAGE_BYTES) {
            return DownloadPolicyResult.Denied(
                "Se requieren al menos 2 GB de almacenamiento libre para descargar."
            )
        }

        return DownloadPolicyResult.Allowed
    }

    fun validateItem(item: JellyxItem): DownloadPolicyResult {
        if (!item.canDownload) {
            return DownloadPolicyResult.Denied(
                "La descarga directa no está habilitada para este elemento."
            )
        }
        return DownloadPolicyResult.Allowed
    }

    fun select1080pSdrSourceForDownload(sources: List<JellyxSource>): JellyxSource? {
        if (sources.isEmpty()) return null

        val ranked =
            sources.sortedWith(
                compareByDescending<JellyxSource> { isSdrSource(it) }
                    .thenByDescending { is1080pOrLower(it) }
                    .thenByDescending { videoHeight(it) in 720..1080 }
                    .thenBy { it.size }
            )

        return ranked.firstOrNull()
    }

    internal fun isTelevisionDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback =
            context.packageManager?.hasSystemFeature(PackageManager.FEATURE_LEANBACK) == true
        return isTvMode || hasLeanback
    }

    private fun isSdrSource(source: JellyxSource): Boolean {
        val videoStream =
            source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO } ?: return true
        val rangeType = videoStream.videoRangeType?.name.orEmpty()
        val title = videoStream.displayTitle.orEmpty()
        val doviTitle = videoStream.videoDoViTitle.orEmpty()
        val isHdrOrDv =
            doviTitle.isNotBlank() ||
                rangeType.contains("HDR", ignoreCase = true) ||
                rangeType.contains("DOVI", ignoreCase = true) ||
                rangeType.contains("HLG", ignoreCase = true) ||
                title.contains("Dolby Vision", ignoreCase = true) ||
                title.contains("HDR", ignoreCase = true)
        return !isHdrOrDv
    }

    private fun is1080pOrLower(source: JellyxSource): Boolean {
        val height = videoHeight(source)
        val width = videoWidth(source)
        return (height == 0 && width == 0) || (height <= 1080 && width <= 1920)
    }

    private fun videoHeight(source: JellyxSource): Int =
        source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }?.height ?: 0

    private fun videoWidth(source: JellyxSource): Int =
        source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }?.width ?: 0
}
