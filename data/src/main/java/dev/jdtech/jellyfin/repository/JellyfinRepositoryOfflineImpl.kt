package dev.jdtech.jellyfin.repository

import android.content.Context
import androidx.paging.PagingData
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxCollection
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxPerson
import dev.jdtech.jellyfin.models.JellyxSeason
import dev.jdtech.jellyfin.models.JellyxSegment
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.models.JellyxSource
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.models.StreamingPlatformCatalog
import dev.jdtech.jellyfin.models.toJellyxEpisode
import dev.jdtech.jellyfin.models.toJellyxMovie
import dev.jdtech.jellyfin.models.toJellyxSeason
import dev.jdtech.jellyfin.models.toJellyxSegment
import dev.jdtech.jellyfin.models.toJellyxShow
import dev.jdtech.jellyfin.models.toJellyxSource
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserConfiguration

class JellyfinRepositoryOfflineImpl(
    private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
) : JellyfinRepository {

    override suspend fun getPublicSystemInfo(): PublicSystemInfo {
        throw Exception("System info not available in offline mode")
    }

    override suspend fun getUserViews(): List<BaseItemDto> {
        return emptyList()
    }

    override suspend fun getMovie(itemId: UUID): JellyxMovie =
        withContext(Dispatchers.IO) {
            database.getMovie(itemId).toJellyxMovie(database, jellyfinApi.userId!!)
        }

    override suspend fun getShow(itemId: UUID): JellyxShow =
        withContext(Dispatchers.IO) {
            database.getShow(itemId).toJellyxShow(database, jellyfinApi.userId!!)
        }

    override suspend fun getSeason(itemId: UUID): JellyxSeason =
        withContext(Dispatchers.IO) {
            database.getSeason(itemId).toJellyxSeason(database, jellyfinApi.userId!!)
        }

    override suspend fun getEpisode(itemId: UUID): JellyxEpisode =
        withContext(Dispatchers.IO) {
            database.getEpisode(itemId).toJellyxEpisode(database, jellyfinApi.userId!!)
        }

    override suspend fun getLibraries(): List<JellyxCollection> {
        return emptyList()
    }

    override suspend fun getItem(itemId: UUID): JellyxItem? {
        return null
    }

    override suspend fun getItems(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        startIndex: Int?,
        limit: Int?,
    ): List<JellyxItem> {
        return emptyList()
    }

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
    ): Flow<PagingData<JellyxItem>> {
        TODO("Not yet implemented")
    }

    override suspend fun getPerson(personId: UUID): JellyxPerson {
        TODO("Not yet implemented")
    }

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<JellyxItem> {
        TODO("Not yet implemented")
    }

    override suspend fun getFavoriteItems(): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext emptyList()
            val userId = jellyfinApi.userId ?: return@withContext emptyList()
            val movies =
                database
                    .getMoviesByServerId(serverId)
                    .map { it.toJellyxMovie(database, userId) }
                    .filter { it.favorite }
            val shows =
                database
                    .getShowsByServerId(serverId)
                    .map { it.toJellyxShow(database, userId) }
                    .filter { it.favorite }
            val episodes =
                database
                    .getEpisodesByServerId(serverId)
                    .map { it.toJellyxEpisode(database, userId) }
                    .filter { it.favorite }
            movies + shows + episodes
        }

    override suspend fun getSimilarItems(itemId: UUID, limit: Int): List<JellyxItem> = emptyList()

    override suspend fun getGenres(): List<String> = emptyList()

    override suspend fun getItemsByGenre(genre: String): List<JellyxItem> = emptyList()

    override suspend fun getSearchItems(query: String): List<JellyxItem> {
        return withContext(Dispatchers.IO) {
            val movies =
                database
                    .searchMovies(appPreferences.getValue(appPreferences.currentServer)!!, query)
                    .map { it.toJellyxMovie(database, jellyfinApi.userId!!) }
            val shows =
                database
                    .searchShows(appPreferences.getValue(appPreferences.currentServer)!!, query)
                    .map { it.toJellyxShow(database, jellyfinApi.userId!!) }
            val episodes =
                database
                    .searchEpisodes(appPreferences.getValue(appPreferences.currentServer)!!, query)
                    .map { it.toJellyxEpisode(database, jellyfinApi.userId!!) }
            movies + shows + episodes
        }
    }

    override suspend fun getItemsByStreamingPlatform(platformId: String): List<JellyxItem> {
        val spec =
            StreamingPlatformCatalog.findById(platformId)
                ?: StreamingPlatformCatalog.findByQuery(platformId)
                ?: return emptyList()
        return getSearchItems(spec.displayLabel)
    }

    override suspend fun getSuggestions(): List<JellyxItem> {
        return emptyList()
    }

    override suspend fun getResumeItems(): List<JellyxItem> {
        return withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext emptyList()
            val userId = jellyfinApi.userId ?: return@withContext emptyList()
            val movies =
                database
                    .getMoviesByServerId(serverId)
                    .map { it.toJellyxMovie(database, userId) }
                    .filter { it.playbackPositionTicks > 0 }
            val episodes =
                database
                    .getEpisodesByServerId(serverId)
                    .map { it.toJellyxEpisode(database, userId) }
                    .filter { it.playbackPositionTicks > 0 }
            movies + episodes
        }
    }

    override suspend fun getLatestMedia(parentId: UUID): List<JellyxItem> {
        return emptyList()
    }

    override suspend fun getLatestReleases(
        parentId: UUID,
        collectionType: CollectionType,
        limit: Int,
    ): List<JellyxItem> = getLatestMedia(parentId)

    override suspend fun getSeasons(seriesId: UUID, offline: Boolean): List<JellyxSeason> =
        withContext(Dispatchers.IO) {
            database.getSeasonsByShowId(seriesId).map {
                it.toJellyxSeason(database, jellyfinApi.userId!!)
            }
        }

    override suspend fun getNextUp(seriesId: UUID?): List<JellyxEpisode> {
        return withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext emptyList()
            val userId = jellyfinApi.userId ?: return@withContext emptyList()
            val result = mutableListOf<JellyxEpisode>()
            val shows =
                database
                    .getShowsByServerId(serverId)
                    .filter { if (seriesId != null) it.id == seriesId else true }
            for (show in shows) {
                val episodes =
                    database.getEpisodesByShowId(show.id).map {
                        it.toJellyxEpisode(database, userId)
                    }
                val indexOfLastPlayed = episodes.indexOfLast { it.played }
                if (indexOfLastPlayed == -1) {
                    episodes.firstOrNull()?.let { result.add(it) }
                } else {
                    episodes.getOrNull(indexOfLastPlayed + 1)?.let { result.add(it) }
                }
            }
            result.filter { it.playbackPositionTicks == 0L }
        }
    }

    override suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>?,
        startItemId: UUID?,
        limit: Int?,
        offline: Boolean,
    ): List<JellyxEpisode> =
        withContext(Dispatchers.IO) {
            val items =
                database.getEpisodesBySeasonId(seasonId).map {
                    it.toJellyxEpisode(database, jellyfinApi.userId!!)
                }
            if (startItemId != null) return@withContext items.dropWhile { it.id != startItemId }
            items
        }

    override suspend fun getMediaSources(itemId: UUID, includePath: Boolean): List<JellyxSource> =
        withContext(Dispatchers.IO) {
            database.getSources(itemId).map { it.toJellyxSource(database) }
        }

    override suspend fun closeLiveStream(liveStreamId: String) = Unit

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String {
        TODO("Not yet implemented")
    }

    override suspend fun getSegments(itemId: UUID): List<JellyxSegment> =
        withContext(Dispatchers.IO) { database.getSegments(itemId).map { it.toJellyxSegment() } }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val sources =
                    File(context.filesDir, "trickplay/$itemId").listFiles()
                        ?: return@withContext null
                File(sources.first(), index.toString()).readBytes()
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun postCapabilities() {}

    override suspend fun postPlaybackStart(itemId: UUID) {}

    override suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
    ) {
        withContext(Dispatchers.IO) {
            when {
                playedPercentage < 10 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
                playedPercentage > 90 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, true)
                }
                else -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
            }
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun markAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, true)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun unmarkAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, false)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun markAsPlayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, true)
            database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun markAsUnplayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, false)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override fun getBaseUrl(): String {
        return ""
    }

    override suspend fun updateDeviceName(name: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getUserConfiguration(): UserConfiguration? {
        return null
    }

    override suspend fun getDownloads(): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext emptyList()
            val userId = jellyfinApi.userId ?: return@withContext emptyList()
            val items = mutableListOf<JellyxItem>()
            items.addAll(
                database
                    .getMoviesByServerId(serverId)
                    .map { it.toJellyxMovie(database, userId) }
            )
            items.addAll(
                database
                    .getShowsByServerId(serverId)
                    .map { it.toJellyxShow(database, userId) }
            )
            items
        }

    override suspend fun getLiveTvChannels(): List<JellyxChannel> = emptyList()

    override suspend fun getLiveTvChannelsPreview(limit: Int): List<JellyxChannel> = emptyList()

    override suspend fun getLiveTvChannel(channelId: UUID): JellyxChannel {
        throw UnsupportedOperationException("Live TV is not available offline")
    }

    override suspend fun getSyncPlayGroups(): List<SyncPlayGroupInfo> = emptyList()

    override suspend fun createSyncPlayGroup(groupName: String): Boolean = false

    override suspend fun joinSyncPlayGroup(groupId: UUID): Boolean = false

    override suspend fun leaveSyncPlayGroup(): Boolean = false

    override suspend fun sendSyncPlayPause() = Unit

    override suspend fun sendSyncPlayUnpause() = Unit

    override suspend fun sendSyncPlaySeek(positionMs: Long) = Unit

    override fun getUserId(): UUID {
        return jellyfinApi.userId!!
    }
}
