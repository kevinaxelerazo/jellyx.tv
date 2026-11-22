package dev.jdtech.jellyfin.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.toJellyxEpisode
import dev.jdtech.jellyfin.models.toJellyxMovie
import dev.jdtech.jellyfin.models.toJellyxSource
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

@AndroidEntryPoint
class DownloadReceiver : BroadcastReceiver() {

    @Inject lateinit var database: ServerDatabaseDao

    @Inject lateinit var downloader: Downloader

    @Inject lateinit var repository: JellyfinRepository

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onReceive(context: Context, intent: Intent) =
        launchAsync(ioScope) {
            if (intent.action == "android.intent.action.DOWNLOAD_COMPLETE") {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != -1L) {
                    val downloadManager = context.getSystemService(DownloadManager::class.java)
                    var downloadStatus: Int? = null
                    downloadManager
                        ?.query(DownloadManager.Query().setFilterById(id))
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                downloadStatus =
                                    cursor.getInt(
                                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                                    )
                            }
                        }

                    val isSuccessful = downloadStatus == DownloadManager.STATUS_SUCCESSFUL
                    val source = database.getSourceByDownloadId(id)
                    if (source != null) {
                        val path = source.path.removeSuffix(".download")
                        val pendingFile = File(source.path)
                        val finalFile = File(path)
                        val successfulRename =
                            isSuccessful &&
                                ((pendingFile.exists() && pendingFile.renameTo(finalFile)) ||
                                    (!pendingFile.exists() &&
                                        finalFile.exists() &&
                                        finalFile.length() > 0L))
                        if (successfulRename) {
                            database.setSourcePath(source.id, path)
                        } else if (!isSuccessful || !pendingFile.exists()) {
                            val items = mutableListOf<JellyxItem>()
                            items.addAll(
                                database.getMovies().map {
                                    it.toJellyxMovie(database, repository.getUserId())
                                }
                            )
                            items.addAll(
                                database.getEpisodes().map {
                                    it.toJellyxEpisode(database, repository.getUserId())
                                }
                            )

                            items
                                .firstOrNull { it.id == source.itemId }
                                ?.let {
                                    downloader.deleteItem(it, source.toJellyxSource(database))
                                }
                        }
                    } else {
                        val mediaStream = database.getMediaStreamByDownloadId(id)
                        if (mediaStream != null) {
                            val path = mediaStream.path.removeSuffix(".download")
                            val pendingFile = File(mediaStream.path)
                            val finalFile = File(path)
                            val successfulRename =
                                isSuccessful &&
                                    ((pendingFile.exists() && pendingFile.renameTo(finalFile)) ||
                                        (!pendingFile.exists() &&
                                            finalFile.exists() &&
                                            finalFile.length() > 0L))
                            if (successfulRename) {
                                database.setMediaStreamPath(mediaStream.id, path)
                            } else {
                                pendingFile.delete()
                                database.deleteMediaStream(mediaStream.id)
                            }
                        }
                    }
                }
            }
        }
}
