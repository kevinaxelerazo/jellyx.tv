package dev.jdtech.jellyfin.utils

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.text.format.Formatter
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxSource
import dev.jdtech.jellyfin.models.JellyxSources
import dev.jdtech.jellyfin.models.JellyxTrickplayInfo
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.toJellyxEpisode
import dev.jdtech.jellyfin.models.toJellyxEpisodeDto
import dev.jdtech.jellyfin.models.toJellyxMediaStreamDto
import dev.jdtech.jellyfin.models.toJellyxMovie
import dev.jdtech.jellyfin.models.toJellyxMovieDto
import dev.jdtech.jellyfin.models.toJellyxSeasonDto
import dev.jdtech.jellyfin.models.toJellyxSegmentsDto
import dev.jdtech.jellyfin.models.toJellyxShowDto
import dev.jdtech.jellyfin.models.toJellyxSource
import dev.jdtech.jellyfin.models.toJellyxSourceDto
import dev.jdtech.jellyfin.models.toJellyxTrickplayInfoDto
import dev.jdtech.jellyfin.models.toJellyxUserDataDto
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.ImagesDownloaderWorker
import dev.jdtech.jellyfin.work.MediaDownloadWorker
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber

class DownloaderImpl(
    private val context: Context,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
    private val transferRegistry: DownloadTransferRegistry,
) : Downloader {
    private val downloadIdGenerator = AtomicLong(System.currentTimeMillis())

    override suspend fun downloadItem(
        item: JellyxItem,
        sourceId: String,
        storageIndex: Int,
    ): Pair<Long, UiText?> = coroutineScope {
        try {
            val itemPolicy = DownloadPolicyValidator.validateItem(item)
            if (itemPolicy is DownloadPolicyResult.Denied) {
                return@coroutineScope Pair(-1, UiText.DynamicString(itemPolicy.reason))
            }

            checkNetworkDownloadPolicy()?.let { deniedReason ->
                return@coroutineScope Pair(-1, UiText.DynamicString(deniedReason))
            }

            val allSources = jellyfinRepository.getMediaSources(item.id, true)
            val source =
                if (item is JellyxEpisode || item is JellyxMovie) {
                    DownloadPolicyValidator.select1080pSdrSourceForDownload(allSources)
                        ?: allSources.firstOrNull { it.id == sourceId }
                        ?: allSources.firstOrNull()
                } else {
                    allSources.firstOrNull { it.id == sourceId }
                        ?: DownloadPolicyValidator.select1080pSdrSourceForDownload(allSources)
                        ?: allSources.firstOrNull()
                } ?: throw IllegalStateException("No playable media source found")

            if (source.path.isBlank()) {
                return@coroutineScope Pair(-1, UiText.StringResource(CoreR.string.unknown_error))
            }

            val storageDir =
                File(context.noBackupFilesDir, OFFLINE_CACHE_DIR_NAME).apply { mkdirs() }
            val policyResult = DownloadPolicyValidator.validateDeviceAndStorage(context, storageDir)
            if (policyResult is DownloadPolicyResult.Denied) {
                return@coroutineScope Pair(-1, UiText.DynamicString(policyResult.reason))
            }

            val stats = StatFs(storageDir.path)
            if (source.size > 0L && stats.availableBytes < source.size) {
                return@coroutineScope Pair(
                    -1,
                    UiText.StringResource(
                        CoreR.string.not_enough_storage,
                        Formatter.formatFileSize(context, source.size),
                        Formatter.formatFileSize(context, stats.availableBytes),
                    ),
                )
            }

            val segments = jellyfinRepository.getSegments(item.id)
            val trickplayInfo =
                if (item is JellyxSources) {
                    item.trickplayInfo?.get(source.id) ?: item.trickplayInfo?.get(sourceId)
                } else {
                    null
                }

            val finalFile = File(storageDir, "${item.id}.${source.id}.jellyx")
            val pendingFile = File(storageDir, "${item.id}.${source.id}.jellyx.download")
            if (pendingFile.exists()) {
                pendingFile.delete()
            }
            if (finalFile.exists()) {
                finalFile.delete()
            }

            val downloadId = downloadIdGenerator.incrementAndGet()
            val accessToken = jellyfinRepository.getAccessToken()?.takeIf { it.isNotBlank() }

            when (item) {
                is JellyxMovie -> {
                    database.insertMovie(
                        item.toJellyxMovieDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                }
                is JellyxEpisode -> {
                    val show = jellyfinRepository.getShow(item.seriesId)
                    database.insertShow(
                        show.toJellyxShowDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                    val season = jellyfinRepository.getSeason(item.seasonId)
                    database.insertSeason(season.toJellyxSeasonDto())
                    database.insertEpisode(
                        item.toJellyxEpisodeDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )

                    startImagesDownloader(show)
                    startImagesDownloader(season)
                }
            }

            val sourceDto = source.toJellyxSourceDto(item.id, pendingFile.path)
            database.insertSource(sourceDto.copy(downloadId = downloadId))
            database.insertUserData(item.toJellyxUserDataDto(jellyfinRepository.getUserId()))

            val subIds = mutableListOf<String>()
            val subUrls = mutableListOf<String>()
            val subPendingPaths = mutableListOf<String>()
            for (mediaStream in source.mediaStreams.filter { it.isExternal }) {
                val streamUrl = mediaStream.path?.takeIf { it.isNotBlank() } ?: continue
                val subId = UUID.randomUUID()
                val subPendingFile =
                    File(storageDir, "${item.id}.${source.id}.$subId.jellyx.download")
                database.insertMediaStream(
                    mediaStream.toJellyxMediaStreamDto(subId, source.id, subPendingFile.path)
                )
                subIds.add(subId.toString())
                subUrls.add(streamUrl)
                subPendingPaths.add(subPendingFile.path)
            }

            segments.forEach { database.insertSegment(it.toJellyxSegmentsDto(item.id)) }
            if (trickplayInfo != null) {
                downloadTrickplayData(item.id, source.id, trickplayInfo)
            }
            startImagesDownloader(item)

            val uniqueWorkName = uniqueWorkNameFor(item.id)
            transferRegistry.put(
                downloadId = downloadId,
                uniqueWorkName = uniqueWorkName,
                status = DownloadManager.STATUS_PENDING,
                progress = 0,
            )

            val allowMetered = appPreferences.getValue(appPreferences.downloadOverMobileData)
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED
                    )
                    .build()

            val request =
                OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                    .setConstraints(constraints)
                    .setInputData(
                        workDataOf(
                            MediaDownloadWorker.KEY_ITEM_ID to item.id.toString(),
                            MediaDownloadWorker.KEY_ITEM_TITLE to item.name,
                            MediaDownloadWorker.KEY_SOURCE_ID to source.id,
                            MediaDownloadWorker.KEY_SOURCE_URL to source.path,
                            MediaDownloadWorker.KEY_EXPECTED_BYTES to source.size,
                            MediaDownloadWorker.KEY_PENDING_PATH to pendingFile.path,
                            MediaDownloadWorker.KEY_FINAL_PATH to finalFile.path,
                            MediaDownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                            MediaDownloadWorker.KEY_ACCESS_TOKEN to accessToken,
                            MediaDownloadWorker.KEY_UNIQUE_WORK_NAME to uniqueWorkName,
                            MediaDownloadWorker.KEY_SUB_IDS to subIds.toTypedArray(),
                            MediaDownloadWorker.KEY_SUB_URLS to subUrls.toTypedArray(),
                            MediaDownloadWorker.KEY_SUB_PENDING_PATHS to subPendingPaths.toTypedArray(),
                        )
                    )
                    .build()

            workManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, request)

            return@coroutineScope Pair(downloadId, null)
        } catch (e: Exception) {
            try {
                val fallbackSource =
                    jellyfinRepository.getMediaSources(item.id).let { sources ->
                        DownloadPolicyValidator.select1080pSdrSourceForDownload(sources)
                            ?: sources.firstOrNull { it.id == sourceId }
                    }
                if (fallbackSource != null) {
                    deleteItem(item, fallbackSource)
                }
            } catch (_: Exception) {}
            Timber.e(e)
            return@coroutineScope Pair(
                -1,
                if (e.message != null) UiText.DynamicString(e.message!!)
                else UiText.StringResource(CoreR.string.unknown_error),
            )
        }
    }

    override suspend fun cancelDownload(item: JellyxItem, downloadId: Long) {
        val transfer = transferRegistry.remove(downloadId)
        workManager.cancelUniqueWork(transfer?.uniqueWorkName ?: uniqueWorkNameFor(item.id))
        val source =
            database.getSourceByDownloadId(downloadId)?.toJellyxSource(database) ?: return
        deleteItem(item, source)
    }

    override suspend fun deleteItem(item: JellyxItem, source: JellyxSource) {
        source.downloadId?.let { id ->
            val transfer = transferRegistry.remove(id)
            workManager.cancelUniqueWork(transfer?.uniqueWorkName ?: uniqueWorkNameFor(item.id))
        }
        when (item) {
            is JellyxMovie -> {
                database.deleteMovie(item.id)
            }
            is JellyxEpisode -> {
                database.deleteEpisode(item.id)
                val remainingEpisodes = database.getEpisodesBySeasonId(item.seasonId)
                if (remainingEpisodes.isEmpty()) {
                    database.deleteSeason(item.seasonId)
                    database.deleteUserData(item.seasonId)
                    File(context.filesDir, "trickplay/${item.seasonId}").deleteRecursively()
                    File(context.filesDir, "images/${item.seasonId}").deleteRecursively()
                    val remainingSeasons = database.getSeasonsByShowId(item.seriesId)
                    if (remainingSeasons.isEmpty()) {
                        database.deleteShow(item.seriesId)
                        database.deleteUserData(item.seriesId)
                        File(context.filesDir, "trickplay/${item.seriesId}").deleteRecursively()
                        File(context.filesDir, "images/${item.seriesId}").deleteRecursively()
                    }
                }
            }
        }

        database.deleteSource(source.id)
        File(source.path).delete()
        if (source.path.endsWith(".download")) {
            File(source.path.removeSuffix(".download")).delete()
        }

        val mediaStreams = database.getMediaStreamsBySourceId(source.id)
        for (mediaStream in mediaStreams) {
            File(mediaStream.path).delete()
        }
        database.deleteMediaStreamsBySourceId(source.id)

        database.deleteUserData(item.id)

        File(context.filesDir, "trickplay/${item.id}").deleteRecursively()
        File(context.filesDir, "images/${item.id}").deleteRecursively()
    }

    override suspend fun getProgress(downloadId: Long?): Pair<Int, Int> {
        if (downloadId == null) {
            return Pair(-1, -1)
        }
        transferRegistry.get(downloadId)?.let { transfer ->
            return Pair(transfer.status, transfer.progress)
        }
        val source = database.getSourceByDownloadId(downloadId)
        if (
            source != null &&
                !source.path.endsWith(".download") &&
                File(source.path).let { it.exists() && it.length() > 0L }
        ) {
            return Pair(DownloadManager.STATUS_SUCCESSFUL, 100)
        }
        if (source != null) {
            val isWorkActive = isUniqueWorkActive(uniqueWorkNameFor(source.itemId))
            if (isWorkActive) {
                return Pair(DownloadManager.STATUS_RUNNING, 0)
            }
        }
        return Pair(DownloadManager.STATUS_FAILED, 0)
    }

    override suspend fun reconcileCompletedDownloads() =
        withContext(Dispatchers.IO) {
            for (source in database.getPendingDownloadSources()) {
                if (source.downloadId != null && transferRegistry.contains(source.downloadId!!)) {
                    continue
                }
                if (isUniqueWorkActive(uniqueWorkNameFor(source.itemId))) {
                    continue
                }
                runCatching {
                    val pendingFile = File(source.path)
                    val finalPath = source.path.removeSuffix(".download")
                    val finalFile = File(finalPath)
                    if (finalFile.exists() && finalFile.length() > 0L) {
                        pendingFile.delete()
                        database.setSourcePath(source.id, finalPath)
                    } else {
                        pendingFile.delete()
                        val userId = jellyfinRepository.getUserId()
                        val movie =
                            database.getMovieOrNull(source.itemId)?.toJellyxMovie(database, userId)
                        val episode =
                            if (movie == null) {
                                database
                                    .getEpisodeOrNull(source.itemId)
                                    ?.toJellyxEpisode(database, userId)
                            } else {
                                null
                            }
                        val item = movie ?: episode
                        if (item != null) {
                            deleteItem(item, source.toJellyxSource(database))
                        } else {
                            database.deleteSource(source.id)
                            database.deleteMediaStreamsBySourceId(source.id)
                        }
                    }
                }
            }
            for (mediaStream in database.getPendingDownloadMediaStreams()) {
                if (
                    mediaStream.downloadId != null &&
                        transferRegistry.contains(mediaStream.downloadId!!)
                ) {
                    continue
                }
                runCatching {
                    val pendingFile = File(mediaStream.path)
                    val finalPath = mediaStream.path.removeSuffix(".download")
                    val finalFile = File(finalPath)
                    if (finalFile.exists() && finalFile.length() > 0L) {
                        pendingFile.delete()
                        database.setMediaStreamPath(mediaStream.id, finalPath)
                    } else {
                        pendingFile.delete()
                        database.deleteMediaStream(mediaStream.id)
                    }
                }
            }
        }

    private fun isUniqueWorkActive(uniqueWorkName: String): Boolean {
        return runCatching {
                val infos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
                infos.any {
                    it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.BLOCKED
                }
            }
            .getOrDefault(false)
    }

    private fun checkNetworkDownloadPolicy(): String? {
        val allowMetered = appPreferences.getValue(appPreferences.downloadOverMobileData)
        val allowRoaming = appPreferences.getValue(appPreferences.downloadWhenRoaming)
        if (allowMetered && allowRoaming) return null

        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return "Sin conexión de red disponible."
        val caps = cm.getNetworkCapabilities(network)

        if (!allowMetered && cm.isActiveNetworkMetered) {
            return "La descarga por datos móviles está desactivada en ajustes."
        }
        if (
            !allowRoaming &&
                caps != null &&
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        ) {
            return "La descarga en roaming está desactivada en ajustes."
        }
        return null
    }

    private suspend fun downloadTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: JellyxTrickplayInfo,
    ) {
        val maxIndex =
            ceil(
                    trickplayInfo.thumbnailCount
                        .toDouble()
                        .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                )
                .toInt()
        val byteArrays = mutableListOf<ByteArray>()
        for (i in 0..maxIndex) {
            jellyfinRepository.getTrickplayData(itemId, trickplayInfo.width, i)?.let { byteArray ->
                byteArrays.add(byteArray)
            }
        }
        saveTrickplayData(itemId, sourceId, trickplayInfo, byteArrays)
    }

    private suspend fun saveTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: JellyxTrickplayInfo,
        byteArrays: List<ByteArray>,
    ) {
        val basePath = "trickplay/$itemId/$sourceId"
        database.insertTrickplayInfo(trickplayInfo.toJellyxTrickplayInfoDto(sourceId))
        File(context.filesDir, basePath).mkdirs()
        for ((i, byteArray) in byteArrays.withIndex()) {
            val file = File(context.filesDir, "$basePath/$i")
            file.writeBytes(byteArray)
        }
    }

    private fun startImagesDownloader(item: JellyxItem) {
        val downloadImagesRequest =
            OneTimeWorkRequestBuilder<ImagesDownloaderWorker>()
                .setInputData(workDataOf(ImagesDownloaderWorker.KEY_ITEM_ID to item.id.toString()))
                .build()

        workManager.enqueue(downloadImagesRequest)
    }

    private fun uniqueWorkNameFor(itemId: UUID): String = "jellyx_dl_$itemId"

    companion object {
        private const val OFFLINE_CACHE_DIR_NAME = "offline_cache"
    }
}
