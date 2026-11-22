package dev.jdtech.jellyfin.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.cache.MediaCacheEvictionBus
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxCollection
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxPerson
import dev.jdtech.jellyfin.models.JellyxProgram
import dev.jdtech.jellyfin.models.JellyxSeason
import dev.jdtech.jellyfin.models.JellyxSegment
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.models.JellyxSource
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.models.toJellyxChannel
import dev.jdtech.jellyfin.models.toJellyxCollection
import dev.jdtech.jellyfin.models.toJellyxEpisode
import dev.jdtech.jellyfin.models.toJellyxItem
import dev.jdtech.jellyfin.models.toJellyxMovie
import dev.jdtech.jellyfin.models.toJellyxPerson
import dev.jdtech.jellyfin.models.toJellyxProgram
import dev.jdtech.jellyfin.models.toJellyxSeason
import dev.jdtech.jellyfin.models.toJellyxShow
import dev.jdtech.jellyfin.models.toJellyxSource
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceOptionsDto
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SeekRequestDto
import org.jellyfin.sdk.model.api.SortOrder as ItemSortOrder
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile
import org.jellyfin.sdk.model.api.UserConfiguration
import timber.log.Timber

class JellyfinRepositoryImpl(
    private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val cacheEvictionBus: MediaCacheEvictionBus? = null,
) : JellyfinRepository {
    private val segmentResolver = SegmentResolver(jellyfinApi, database)

    override suspend fun getPublicSystemInfo(): PublicSystemInfo =
        withContext(Dispatchers.IO) { jellyfinApi.systemApi.getPublicSystemInfo().content }

    override suspend fun getUserViews(): List<BaseItemDto> =
        withContext(Dispatchers.IO) {
            jellyfinApi.userViewApi.getUserViews(jellyfinApi.userId!!).content.items
        }

    override suspend fun getEpisode(itemId: UUID): JellyxEpisode =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.libraryApi
                    .getItem(itemId, jellyfinApi.userId!!)
                    .content
                    .toJellyxEpisode(this@JellyfinRepositoryImpl, database)!!
            } catch (e: Exception) {
                val userId = jellyfinApi.userId ?: throw e
                database.getEpisodeOrNull(itemId)?.toJellyxEpisode(database, userId) ?: throw e
            }
        }

    override suspend fun getMovie(itemId: UUID): JellyxMovie =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.libraryApi
                    .getItem(itemId, jellyfinApi.userId!!)
                    .content
                    .toJellyxMovie(this@JellyfinRepositoryImpl, database)
            } catch (e: Exception) {
                val userId = jellyfinApi.userId ?: throw e
                database.getMovieOrNull(itemId)?.toJellyxMovie(database, userId) ?: throw e
            }
        }

    override suspend fun getShow(itemId: UUID): JellyxShow =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.libraryApi
                    .getItem(itemId, jellyfinApi.userId!!)
                    .content
                    .toJellyxShow(this@JellyfinRepositoryImpl)
            } catch (e: Exception) {
                val userId = jellyfinApi.userId ?: throw e
                database.getShowOrNull(itemId)?.toJellyxShow(database, userId) ?: throw e
            }
        }

    override suspend fun getSeason(itemId: UUID): JellyxSeason =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.libraryApi
                    .getItem(itemId, jellyfinApi.userId!!)
                    .content
                    .toJellyxSeason(this@JellyfinRepositoryImpl)
            } catch (e: Exception) {
                val userId = jellyfinApi.userId ?: throw e
                database.getSeasonOrNull(itemId)?.toJellyxSeason(database, userId) ?: throw e
            }
        }

    override suspend fun getLibraries(): List<JellyxCollection> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi.getItems(jellyfinApi.userId!!).content.items.mapNotNull {
                it.toJellyxCollection(this@JellyfinRepositoryImpl)
            }
        }

    override suspend fun getItem(itemId: UUID): JellyxItem? =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId = itemId, userId = jellyfinApi.userId!!)
                .content
                .toJellyxItem(this@JellyfinRepositoryImpl, database)
        }

    override suspend fun getItems(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        startIndex: Int?,
        limit: Int?,
    ): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    parentId = parentId,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                    sortBy = listOf(ItemSortBy.fromName(sortBy.sortString)),
                    sortOrder = listOf(ItemSortOrder.fromName(sortOrder.sortString)),
                    startIndex = startIndex,
                    limit = limit,
                    fields = listOf(ItemFields.PROVIDER_IDS),
                )
                .content
                .items
                .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
    ): Flow<PagingData<JellyxItem>> {
        return Pager(
                config = PagingConfig(pageSize = 10, enablePlaceholders = false),
                pagingSourceFactory = {
                    ItemsPagingSource(this, parentId, includeTypes, recursive, sortBy, sortOrder)
                },
            )
            .flow
    }

    override suspend fun getPerson(personId: UUID): JellyxPerson =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(personId, jellyfinApi.userId!!)
                .content
                .toJellyxPerson(this@JellyfinRepositoryImpl)
        }

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    personIds = personIds,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                )
                .content
                .items
                .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getFavoriteItems(): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    filters = listOf(ItemFilter.IS_FAVORITE),
                    includeItemTypes =
                        listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSearchItems(query: String): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            val bySearchTerm =
                jellyfinApi.libraryApi
                    .getItems(
                        jellyfinApi.userId!!,
                        searchTerm = query,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                    )
                    .content
                    .items
                    .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }

            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext bySearchTerm

            val byStudio =
                runCatching {
                        jellyfinApi.libraryApi
                            .getItems(
                                jellyfinApi.userId!!,
                                studios = listOf(trimmed),
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                recursive = true,
                            )
                            .content
                            .items
                            .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }
                    }
                    .getOrDefault(emptyList())

            (bySearchTerm + byStudio).distinctBy { it.id }
        }

    override suspend fun getSuggestions(): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.suggestionApi
                .getSuggestions(
                    jellyfinApi.userId!!,
                    limit = 6,
                    type = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                )
                .content
                .items
                .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSimilarItems(itemId: UUID, limit: Int): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            val similar =
                runCatching {
                        jellyfinApi.libraryApi
                            .getSimilarItems(
                                itemId = itemId,
                                userId = jellyfinApi.userId!!,
                                limit = limit,
                            )
                            .content
                            .items
                            .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }
                    }
                    .getOrDefault(emptyList())

            if (similar.isNotEmpty()) {
                similar.take(limit)
            } else {
                runCatching { getSuggestions().filter { it.id != itemId }.take(limit) }
                    .getOrDefault(emptyList())
            }
        }

    override suspend fun getGenres(): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                    jellyfinApi.genreApi
                        .getGenres(userId = jellyfinApi.userId!!)
                        .content
                        .items
                        .mapNotNull { it.name?.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                }
                .getOrDefault(emptyList())
        }

    override suspend fun getItemsByGenre(genre: String): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                    jellyfinApi.libraryApi
                        .getItems(
                            userId = jellyfinApi.userId!!,
                            genres = listOf(genre),
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                        )
                        .content
                        .items
                        .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }
                }
                .getOrDefault(emptyList())
        }

    override suspend fun getResumeItems(): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getResumeItems(
                    jellyfinApi.userId!!,
                    limit = 12,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                    fields = listOf(ItemFields.PROVIDER_IDS),
                )
                .content
                .items
                .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getLatestMedia(parentId: UUID): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getLatestMedia(
                    jellyfinApi.userId!!,
                    parentId = parentId,
                    limit = 16,
                    fields = listOf(ItemFields.PROVIDER_IDS),
                )
                .content
                .mapNotNull { it.toJellyxItem(this@JellyfinRepositoryImpl, database) }
        }

    override suspend fun getSeasons(seriesId: UUID, offline: Boolean): List<JellyxSeason> =
        withContext(Dispatchers.IO) {
            if (!offline) {
                try {
                    jellyfinApi.showApi.getSeasons(seriesId, jellyfinApi.userId!!).content.items.map {
                        it.toJellyxSeason(this@JellyfinRepositoryImpl)
                    }
                } catch (e: Exception) {
                    val userId = jellyfinApi.userId ?: throw e
                    val local = database.getSeasonsByShowId(seriesId)
                    if (local.isNotEmpty()) {
                        local.map { it.toJellyxSeason(database, userId) }
                    } else {
                        throw e
                    }
                }
            } else {
                database.getSeasonsByShowId(seriesId).map {
                    it.toJellyxSeason(database, jellyfinApi.userId!!)
                }
            }
        }

    override suspend fun getNextUp(seriesId: UUID?): List<JellyxEpisode> =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.showApi
                    .getNextUp(
                        jellyfinApi.userId!!,
                        limit = 24,
                        seriesId = seriesId,
                        enableResumable = false,
                    )
                    .content
                    .items
                    .mapNotNull { it.toJellyxEpisode(this@JellyfinRepositoryImpl, database) }
            } catch (_: Exception) {
                if (seriesId != null) {
                    jellyfinApi.userId?.let { userId ->
                        database
                            .getEpisodesByShowId(seriesId)
                            .map { it.toJellyxEpisode(database, userId) }
                            .firstOrNull { !it.played }
                            ?.let { listOf(it) }
                    } ?: emptyList()
                } else {
                    emptyList()
                }
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
            if (!offline) {
                try {
                    jellyfinApi.showApi
                        .getEpisodes(
                            seriesId,
                            jellyfinApi.userId!!,
                            seasonId = seasonId,
                            fields = fields,
                            startItemId = startItemId,
                            limit = limit,
                        )
                        .content
                        .items
                        .mapNotNull { it.toJellyxEpisode(this@JellyfinRepositoryImpl, database) }
                } catch (e: Exception) {
                    val userId = jellyfinApi.userId ?: throw e
                    val local =
                        database.getEpisodesBySeasonId(seasonId).map {
                            it.toJellyxEpisode(database, userId)
                        }
                    if (local.isNotEmpty()) {
                        if (startItemId != null) local.dropWhile { it.id != startItemId } else local
                    } else {
                        throw e
                    }
                }
            } else {
                database.getEpisodesBySeasonId(seasonId).map {
                    it.toJellyxEpisode(database, jellyfinApi.userId!!)
                }
            }
        }

    override suspend fun getMediaSources(itemId: UUID, includePath: Boolean): List<JellyxSource> =
        withContext(Dispatchers.IO) {
            val localSources = database.getSources(itemId).map { it.toJellyxSource(database) }
            val remoteSources =
                try {
                    jellyfinApi.mediaInfoApi
                        .getPostedPlaybackInfo(
                            itemId,
                            PlaybackInfoDto(
                                userId = jellyfinApi.userId!!,
                                enableDirectPlay = true,
                                enableDirectStream = true,
                                enableTranscoding = includePath,
                                allowVideoStreamCopy = true,
                                allowAudioStreamCopy = true,
                                autoOpenLiveStream = includePath,
                                deviceProfile =
                                    DeviceProfile(
                                        name = "Direct play all",
                                        maxStaticBitrate = 1_000_000_000,
                                        maxStreamingBitrate = 1_000_000_000,
                                        codecProfiles = emptyList(),
                                        containerProfiles = emptyList(),
                                        directPlayProfiles =
                                            listOf(
                                                DirectPlayProfile(
                                                    container = "mp4,mkv,webm,mov,avi,ts,m2ts,mpegts,hls,m3u8",
                                                    type = DlnaProfileType.VIDEO,
                                                ),
                                                DirectPlayProfile(
                                                    container = "mp3,aac,flac,ogg,wav,opus,m4a",
                                                    type = DlnaProfileType.AUDIO,
                                                ),
                                            ),
                                        transcodingProfiles =
                                            if (includePath) {
                                                listOf(
                                                    org.jellyfin.sdk.model.api.TranscodingProfile(
                                                        container = "ts",
                                                        type =
                                                            org.jellyfin.sdk.model.api.DlnaProfileType
                                                                .VIDEO,
                                                        videoCodec = "h264,hevc",
                                                        audioCodec = "aac,mp3,ac3,eac3",
                                                        protocol =
                                                            org.jellyfin.sdk.model.api
                                                                .MediaStreamProtocol.HLS,
                                                        estimateContentLength = false,
                                                        enableMpegtsM2TsMode = false,
                                                        transcodeSeekInfo =
                                                            org.jellyfin.sdk.model.api
                                                                .TranscodeSeekInfo.AUTO,
                                                        copyTimestamps = true,
                                                        context =
                                                            org.jellyfin.sdk.model.api.EncodingContext
                                                                .STREAMING,
                                                        enableSubtitlesInManifest = false,
                                                        minSegments = 1,
                                                        segmentLength = 3,
                                                        breakOnNonKeyFrames = true,
                                                        conditions = emptyList(),
                                                    )
                                                )
                                            } else {
                                                emptyList()
                                            },
                                        subtitleProfiles =
                                            listOf(
                                                SubtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL),
                                                SubtitleProfile("ass", SubtitleDeliveryMethod.EXTERNAL),
                                            ),
                                    ),
                                maxStreamingBitrate = 1_000_000_000,
                            ),
                        )
                        .content
                        .mediaSources
                        .map { it.toJellyxSource(this@JellyfinRepositoryImpl, itemId, includePath) }
                } catch (e: Exception) {
                    if (localSources.any { !it.path.endsWith(".download") && it.size > 0L }) {
                        return@withContext localSources
                    }
                    throw e
                }
            remoteSources + localSources
        }

    override suspend fun closeLiveStream(liveStreamId: String) {
        withContext(Dispatchers.IO) {
            runCatching { jellyfinApi.mediaInfoApi.closeLiveStream(liveStreamId) }
                .onFailure { Timber.w(it, "Failed to close live stream %s", liveStreamId) }
        }
    }

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.videoApi.getVideoStreamUrl(
                    itemId,
                    static = true,
                    mediaSourceId = mediaSourceId,
                )
            } catch (e: Exception) {
                Timber.e(e)
                ""
            }
        }

    override suspend fun getSegments(itemId: UUID): List<JellyxSegment> =
        withContext(Dispatchers.IO) { segmentResolver.resolveSegments(itemId) }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                try {
                    val sources = File(context.filesDir, "trickplay/$itemId").listFiles()
                    if (sources != null) {
                        return@withContext File(sources.first(), index.toString()).readBytes()
                    }
                } catch (_: Exception) {}

                return@withContext jellyfinApi.trickPlayApi
                    .getTrickplayTileImage(itemId, width, index)
                    .content
            } catch (_: Exception) {
                return@withContext null
            }
        }

    override suspend fun postCapabilities() {
        Timber.d("Sending capabilities")
        withContext(Dispatchers.IO) {
            jellyfinApi.sessionApi.postCapabilities(
                playableMediaTypes = listOf(MediaType.VIDEO),
                supportedCommands =
                    listOf(
                        GeneralCommandType.VOLUME_UP,
                        GeneralCommandType.VOLUME_DOWN,
                        GeneralCommandType.TOGGLE_MUTE,
                        GeneralCommandType.SET_AUDIO_STREAM_INDEX,
                        GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
                        GeneralCommandType.MUTE,
                        GeneralCommandType.UNMUTE,
                        GeneralCommandType.SET_VOLUME,
                        GeneralCommandType.DISPLAY_MESSAGE,
                        GeneralCommandType.PLAY,
                        GeneralCommandType.PLAY_STATE,
                        GeneralCommandType.PLAY_NEXT,
                        GeneralCommandType.PLAY_MEDIA_SOURCE,
                    ),
                supportsMediaControl = true,
            )
        }
    }

    override suspend fun postPlaybackStart(itemId: UUID) {
        Timber.d("Sending start $itemId")
        withContext(Dispatchers.IO) {
            runCatching {
                jellyfinApi.sessionApi.reportPlaybackStart(
                    PlaybackStartInfo(
                        itemId = itemId,
                        canSeek = true,
                        isPaused = false,
                        isMuted = false,
                        playMethod = PlayMethod.DIRECT_PLAY,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                    )
                )
            }
        }
    }

    override suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
    ) {
        Timber.d("Sending stop $itemId")
        withContext(Dispatchers.IO) {
            when {
                playedPercentage < 10 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
                playedPercentage > 90 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, true)
                    cacheEvictionBus?.requestEviction(itemId)
                }
                else -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
            }
            try {
                jellyfinApi.sessionApi.reportPlaybackStopped(
                    PlaybackStopInfo(itemId = itemId, positionTicks = positionTicks, failed = false)
                )
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
    ) {
        Timber.d("Posting progress of $itemId, position: $positionTicks")
        withContext(Dispatchers.IO) {
            database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
            try {
                jellyfinApi.sessionApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        itemId = itemId,
                        canSeek = true,
                        isPaused = isPaused,
                        isMuted = false,
                        playMethod = PlayMethod.DIRECT_PLAY,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                        positionTicks = positionTicks,
                    )
                )
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, true)
            try {
                jellyfinApi.userDataApi.markFavoriteItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun unmarkAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.userDataApi.unmarkFavoriteItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsPlayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, true)
            cacheEvictionBus?.requestEviction(itemId)
            try {
                jellyfinApi.userDataApi.markPlayedItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsUnplayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.userDataApi.markUnplayedItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override fun getBaseUrl() = jellyfinApi.api.baseUrl.orEmpty()

    override fun getAccessToken(): String? = jellyfinApi.api.accessToken

    override suspend fun updateDeviceName(name: String) {
        withContext(Dispatchers.IO) {
            jellyfinApi.jellyfin.deviceInfo?.id?.let { id ->
                jellyfinApi.deviceApi.updateDeviceOptions(
                    id,
                    DeviceOptionsDto(0, customName = name),
                )
            }
        }
    }

    override suspend fun getUserConfiguration(): UserConfiguration =
        withContext(Dispatchers.IO) { jellyfinApi.userApi.getCurrentUser().content.configuration!! }

    override suspend fun getDownloads(): List<JellyxItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<JellyxItem>()
            items.addAll(
                database
                    .getMoviesByServerId(appPreferences.getValue(appPreferences.currentServer)!!)
                    .map { it.toJellyxMovie(database, jellyfinApi.userId!!) }
            )
            items.addAll(
                database
                    .getShowsByServerId(appPreferences.getValue(appPreferences.currentServer)!!)
                    .map { it.toJellyxShow(database, jellyfinApi.userId!!) }
            )
            items
        }

    override suspend fun getLiveTvChannels(): List<JellyxChannel> =
        withContext(Dispatchers.IO) {
            val rawChannels =
                jellyfinApi.liveTvApi
                    .getLiveTvChannels(
                        userId = jellyfinApi.userId,
                        addCurrentProgram = true,
                        enableUserData = true,
                        enableImages = true,
                        fields = listOf(ItemFields.TAGS, ItemFields.GENRES, ItemFields.OVERVIEW),
                    )
                    .content
                    .items

            val now = LocalDateTime.now()
            val channelIds = rawChannels.map { it.id }
            val programsByChannel =
                if (channelIds.isEmpty()) {
                    emptyMap()
                } else {
                    channelIds
                        .chunked(40)
                        .flatMap { chunk ->
                            runCatching {
                                    jellyfinApi.liveTvApi
                                        .getLiveTvPrograms(
                                            channelIds = chunk,
                                            userId = jellyfinApi.userId,
                                            hasAired = false,
                                            minEndDate = now,
                                            maxStartDate = now.plusHours(12),
                                            enableImages = false,
                                            enableUserData = false,
                                            fields = listOf(ItemFields.OVERVIEW),
                                        )
                                        .content
                                        .items
                                        .map { it.toJellyxProgram() }
                                        .filter { it.channelId != null }
                                }
                                .getOrDefault(emptyList())
                        }
                        .groupBy { it.channelId!! }
                }

            rawChannels
                .map { dto ->
                    val (currentProg, nextProg) =
                        resolveChannelPrograms(
                            dtoCurrentProgram = dto.currentProgram?.toJellyxProgram(),
                            channelPrograms = programsByChannel[dto.id].orEmpty(),
                            now = now,
                        )
                    val mapped = dto.toJellyxChannel(this@JellyfinRepositoryImpl, nextProg)
                    if (mapped.currentProgram == null && currentProg != null) {
                        mapped.copy(currentProgram = currentProg)
                    } else {
                        mapped
                    }
                }
                .sortedWith(
                    compareByDescending<JellyxChannel> { it.favorite }
                        .thenBy { it.channelNumber?.toDoubleOrNull() ?: Double.MAX_VALUE }
                        .thenBy { it.displayName.lowercase() }
                )
        }

    override suspend fun getLiveTvChannel(channelId: UUID): JellyxChannel =
        withContext(Dispatchers.IO) {
            val dto =
                jellyfinApi.liveTvApi
                    .getChannel(channelId = channelId, userId = jellyfinApi.userId)
                    .content
            val now = LocalDateTime.now()
            val channelPrograms =
                runCatching {
                        jellyfinApi.liveTvApi
                            .getLiveTvPrograms(
                                channelIds = listOf(channelId),
                                userId = jellyfinApi.userId,
                                hasAired = false,
                                minEndDate = now,
                                maxStartDate = now.plusHours(12),
                                limit = 6,
                                enableImages = false,
                                enableUserData = false,
                                fields = listOf(ItemFields.OVERVIEW),
                            )
                            .content
                            .items
                            .map { it.toJellyxProgram() }
                    }
                    .getOrDefault(emptyList())
            val (currentProg, nextProg) =
                resolveChannelPrograms(
                    dtoCurrentProgram = dto.currentProgram?.toJellyxProgram(),
                    channelPrograms = channelPrograms,
                    now = now,
                )
            val mapped = dto.toJellyxChannel(this@JellyfinRepositoryImpl, nextProg)
            if (mapped.currentProgram == null && currentProg != null) {
                mapped.copy(currentProgram = currentProg)
            } else {
                mapped
            }
        }

    private fun resolveChannelPrograms(
        dtoCurrentProgram: JellyxProgram?,
        channelPrograms: List<JellyxProgram>,
        now: LocalDateTime,
    ): Pair<JellyxProgram?, JellyxProgram?> {
        val sortedPrograms = channelPrograms.sortedBy { it.startDate ?: LocalDateTime.MIN }
        val currentProg =
            dtoCurrentProgram
                ?: sortedPrograms.firstOrNull { prog ->
                    val s = prog.startDate
                    val e = prog.endDate
                    s != null && e != null && !now.isBefore(s) && now.isBefore(e)
                }
        val nextProg =
            sortedPrograms.firstOrNull { prog ->
                val s = prog.startDate
                val currentEnd = currentProg?.endDate
                prog.id != currentProg?.id &&
                    s != null &&
                    (if (currentEnd != null) !s.isBefore(currentEnd.minusMinutes(1))
                    else s.isAfter(now))
            }
        return currentProg to nextProg
    }

    override suspend fun getSyncPlayGroups(): List<SyncPlayGroupInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                    jellyfinApi.syncPlayApi.syncPlayGetGroups().content.map { group ->
                        SyncPlayGroupInfo(
                            groupId = group.groupId,
                            groupName = group.groupName,
                            participants = group.participants,
                        )
                    }
                }
                .getOrDefault(emptyList())
        }

    override suspend fun createSyncPlayGroup(groupName: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                    jellyfinApi.syncPlayApi.syncPlayCreateGroup(
                        NewGroupRequestDto(groupName = groupName)
                    )
                    true
                }
                .getOrDefault(false)
        }

    override suspend fun joinSyncPlayGroup(groupId: UUID): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                    jellyfinApi.syncPlayApi.syncPlayJoinGroup(
                        JoinGroupRequestDto(groupId = groupId)
                    )
                    true
                }
                .getOrDefault(false)
        }

    override suspend fun leaveSyncPlayGroup(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                    jellyfinApi.syncPlayApi.syncPlayLeaveGroup()
                    true
                }
                .getOrDefault(false)
        }

    override suspend fun sendSyncPlayPause() {
        withContext(Dispatchers.IO) {
            runCatching { jellyfinApi.syncPlayApi.syncPlayPause() }
        }
    }

    override suspend fun sendSyncPlayUnpause() {
        withContext(Dispatchers.IO) {
            runCatching { jellyfinApi.syncPlayApi.syncPlayUnpause() }
        }
    }

    override suspend fun sendSyncPlaySeek(positionMs: Long) {
        withContext(Dispatchers.IO) {
            runCatching {
                jellyfinApi.syncPlayApi.syncPlaySeek(
                    SeekRequestDto(positionTicks = positionMs.coerceAtLeast(0L) * 10_000L)
                )
            }
        }
    }

    override fun getUserId(): UUID {
        return jellyfinApi.userId!!
    }
}
