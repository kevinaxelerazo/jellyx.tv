package dev.jdtech.jellyfin.work

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.utils.DownloadTransferRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

@HiltWorker
class MediaDownloadWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
    private val transferRegistry: DownloadTransferRegistry,
) : CoroutineWorker(appContext, params) {

    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override suspend fun doWork(): Result {
        val itemTitle = inputData.getString(KEY_ITEM_TITLE).orEmpty().ifBlank { "Jellyx.tv" }
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return Result.failure()
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: return Result.failure()
        val expectedBytes = inputData.getLong(KEY_EXPECTED_BYTES, 0L)
        val pendingPath = inputData.getString(KEY_PENDING_PATH) ?: return Result.failure()
        val finalPath = inputData.getString(KEY_FINAL_PATH) ?: return Result.failure()
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val accessToken = inputData.getString(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() }

        val subIds = inputData.getStringArray(KEY_SUB_IDS).orEmpty()
        val subUrls = inputData.getStringArray(KEY_SUB_URLS).orEmpty()
        val subPendingPaths = inputData.getStringArray(KEY_SUB_PENDING_PATHS).orEmpty()

        val uniqueWorkName = inputData.getString(KEY_UNIQUE_WORK_NAME) ?: "jellyx_dl_$sourceId"
        if (downloadId != -1L && !transferRegistry.contains(downloadId)) {
            transferRegistry.put(
                downloadId = downloadId,
                uniqueWorkName = uniqueWorkName,
                status = DownloadManager.STATUS_RUNNING,
                progress = 0,
            )
        } else if (downloadId != -1L) {
            transferRegistry.update(downloadId, DownloadManager.STATUS_RUNNING, 0)
        }

        ensureChannel()
        runCatching { setForeground(createForegroundInfo(itemTitle, 0, downloadId)) }

        val pendingFile = File(pendingPath)
        val finalFile = File(finalPath)
        var lastNotificationMs = 0L

        return try {
            streamToFile(
                url = sourceUrl,
                accessToken = accessToken,
                destinationFile = pendingFile,
                expectedBytes = expectedBytes,
                onProgress = { pct ->
                    if (downloadId != -1L) {
                        transferRegistry.update(downloadId, DownloadManager.STATUS_RUNNING, pct)
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastNotificationMs >= 1000L) {
                        lastNotificationMs = now
                        runCatching {
                            notificationManager?.notify(
                                notificationIdFor(downloadId),
                                buildNotification(itemTitle, pct),
                            )
                        }
                    }
                },
            )

            if (!moveFile(pendingFile, finalFile)) {
                throw IllegalStateException("No se pudo mover la descarga al almacenamiento privado.")
            }
            database.setSourcePath(sourceId, finalFile.path)

            downloadExternalSubtitles(
                subIds = subIds,
                subUrls = subUrls,
                subPendingPaths = subPendingPaths,
                accessToken = accessToken,
            )

            if (downloadId != -1L) {
                transferRegistry.update(downloadId, DownloadManager.STATUS_SUCCESSFUL, 100)
            }
            Result.success()
        } catch (_: CancellationException) {
            pendingFile.delete()
            if (downloadId != -1L) {
                transferRegistry.remove(downloadId)
            }
            Result.failure()
        } catch (e: Exception) {
            Timber.e(e, "MediaDownloadWorker failed for source %s", sourceId)
            pendingFile.delete()
            if (downloadId != -1L) {
                transferRegistry.update(downloadId, DownloadManager.STATUS_FAILED, 0)
            }
            Result.failure()
        }
    }

    private suspend fun downloadExternalSubtitles(
        subIds: Array<out String>,
        subUrls: Array<out String>,
        subPendingPaths: Array<out String>,
        accessToken: String?,
    ) {
        val count = minOf(subIds.size, subUrls.size, subPendingPaths.size)
        for (i in 0 until count) {
            val subId = runCatching { UUID.fromString(subIds[i]) }.getOrNull() ?: continue
            val targetUrl = subUrls[i].takeIf { it.isNotBlank() } ?: continue
            val subPendingPath = subPendingPaths[i].takeIf { it.isNotBlank() } ?: continue
            val subPendingFile = File(subPendingPath)
            val subFinalFile = File(subPendingPath.removeSuffix(".download"))
            runCatching {
                    streamToFile(
                        url = targetUrl,
                        accessToken = accessToken,
                        destinationFile = subPendingFile,
                        expectedBytes = 0L,
                        onProgress = {},
                    )
                    if (moveFile(subPendingFile, subFinalFile)) {
                        database.setMediaStreamPath(subId, subFinalFile.path)
                    } else {
                        subPendingFile.delete()
                        database.deleteMediaStream(subId)
                    }
                }
                .onFailure {
                    subPendingFile.delete()
                    database.deleteMediaStream(subId)
                }
        }
    }

    private suspend fun streamToFile(
        url: String,
        accessToken: String?,
        destinationFile: File,
        expectedBytes: Long,
        onProgress: (Int) -> Unit,
    ) {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                .apply {
                    if (accessToken != null) {
                        header("X-Emby-Token", accessToken)
                    }
                }
                .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} al descargar contenido")
            }
            val body = response.body
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: expectedBytes
            destinationFile.parentFile?.mkdirs()

            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            var downloadedBytes = 0L
            var lastReportedPct = -1

            body.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    while (currentCoroutineContext().isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0L) {
                            val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Download cancelled")
            }
            if (downloadedBytes <= 0L) {
                throw IllegalStateException("Archivo descargado vacío")
            }
        }
    }

    private fun moveFile(sourceFile: File, targetFile: File): Boolean {
        if (sourceFile.renameTo(targetFile)) {
            return true
        }
        return runCatching {
                sourceFile.copyTo(targetFile, overwrite = true)
                sourceFile.delete()
                true
            }
            .getOrDefault(false)
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Descargas offline (Jellyx.tv)",
                NotificationManager.IMPORTANCE_LOW,
            )
        notificationManager?.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(title: String, progress: Int, downloadId: Long): ForegroundInfo {
        val notification = buildNotification(title, progress)
        val notifId = notificationIdFor(downloadId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun buildNotification(title: String, progress: Int) =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(CoreR.drawable.ic_download)
            .setContentTitle(title)
            .setContentText("Guardando en caché privada ($progress%)")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()

    private fun notificationIdFor(downloadId: Long): Int =
        BASE_NOTIFICATION_ID + ((downloadId and 0x7FFF).toInt())

    companion object {
        const val KEY_ITEM_ID = "KEY_ITEM_ID"
        const val KEY_ITEM_TITLE = "KEY_ITEM_TITLE"
        const val KEY_SOURCE_ID = "KEY_SOURCE_ID"
        const val KEY_SOURCE_URL = "KEY_SOURCE_URL"
        const val KEY_EXPECTED_BYTES = "KEY_EXPECTED_BYTES"
        const val KEY_PENDING_PATH = "KEY_PENDING_PATH"
        const val KEY_FINAL_PATH = "KEY_FINAL_PATH"
        const val KEY_DOWNLOAD_ID = "KEY_DOWNLOAD_ID"
        const val KEY_ACCESS_TOKEN = "KEY_ACCESS_TOKEN"
        const val KEY_UNIQUE_WORK_NAME = "KEY_UNIQUE_WORK_NAME"
        const val KEY_SUB_IDS = "KEY_SUB_IDS"
        const val KEY_SUB_URLS = "KEY_SUB_URLS"
        const val KEY_SUB_PENDING_PATHS = "KEY_SUB_PENDING_PATHS"

        private const val CHANNEL_ID = "jellyx_offline_downloads"
        private const val BASE_NOTIFICATION_ID = 44_000
        private const val STREAM_BUFFER_BYTES = 64 * 1024
    }
}
