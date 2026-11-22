package dev.jdtech.jellyfin.player.local.domain

import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxChapter
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxSources
import dev.jdtech.jellyfin.player.core.domain.models.ExternalSubtitle
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.TrickplayInfo
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

class PlaylistManager
@Inject
internal constructor(
    private val repository: JellyfinRepository,
    private val smartSourceSelector: SmartSourceSelector,
) {
    private var startItem: JellyxItem? = null
    private var items: List<JellyxItem> = emptyList()
    private val playerItems: MutableList<PlayerItem> = mutableListOf()
    var currentItemIndex: Int = 0

    suspend fun getInitialItem(
        itemId: UUID,
        itemKind: BaseItemKind,
        mediaSourceIndex: Int? = null,
        startFromBeginning: Boolean = false,
    ): PlayerItem? {
        Timber.d("Retrieving initial player item")

        val initialItem =
            when (itemKind) {
                BaseItemKind.MOVIE -> {
                    val movie = repository.getMovie(itemId)

                    items = listOf(movie)
                    movie
                }
                BaseItemKind.SERIES -> {
                    val nextUpEpisode = repository.getNextUp(itemId).firstOrNull()

                    val season =
                        if (nextUpEpisode != null) {
                            repository.getSeason(nextUpEpisode.seasonId)
                        } else {
                            val seasons = repository.getSeasons(itemId)
                            if (seasons.isEmpty()) {
                                return null
                            }
                            seasons.first()
                        }

                    val episodes =
                        repository
                            .getEpisodes(
                                seriesId = itemId,
                                seasonId = season.id,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .filter { !it.missing }

                    if (episodes.isEmpty()) {
                        return null
                    }

                    val episode = nextUpEpisode ?: episodes.first()

                    items = episodes
                    episode
                }
                BaseItemKind.SEASON -> {
                    val season = repository.getSeason(itemId)
                    val episodes =
                        repository
                            .getEpisodes(
                                seriesId = season.seriesId,
                                seasonId = season.id,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .filter { !it.missing }

                    if (episodes.isEmpty()) {
                        return null
                    }

                    val episode = episodes.first()

                    items = episodes
                    episode
                }
                BaseItemKind.EPISODE -> {
                    val episode = repository.getEpisode(itemId)

                    val episodes =
                        repository
                            .getEpisodes(
                                seriesId = episode.seriesId,
                                seasonId = episode.seasonId,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .filter { !it.missing }

                    items = episodes
                    episode
                }
                BaseItemKind.TV_CHANNEL -> {
                    val cachedChannels = items.filterIsInstance<JellyxChannel>()
                    val channels =
                        if (cachedChannels.any { it.id == itemId }) {
                            cachedChannels
                        } else {
                            repository.getLiveTvChannels()
                        }
                    val selected =
                        channels.firstOrNull { it.id == itemId }
                            ?: repository.getLiveTvChannel(itemId)
                    items = if (channels.isNotEmpty()) channels else listOf(selected)
                    selected
                }
                else -> null
            }

        if (initialItem == null) {
            return null
        }

        startItem = initialItem

        currentItemIndex = items.indexOfFirst { it.id == initialItem.id }

        val playbackPosition =
            if (initialItem is JellyxChannel || startFromBeginning) {
                0L
            } else {
                initialItem.playbackPositionTicks.div(10000)
            }
        playerItems.clear()
        val playerItem = initialItem.toPlayerItem(mediaSourceIndex, playbackPosition)
        playerItems.add(playerItem)

        return playerItem
    }

    fun resetLoadedNeighbors(keepItemId: UUID) {
        playerItems.removeAll { it.itemId != keepItemId }
        val idx = items.indexOfFirst { it.id == keepItemId }
        if (idx >= 0) {
            currentItemIndex = idx
        }
    }

    suspend fun refreshItemSource(itemId: UUID, playbackPositionMs: Long): PlayerItem? {
        val targetItem =
            items.firstOrNull { it.id == itemId }
                ?: startItem?.takeIf { it.id == itemId }
                ?: return null
        val refreshed = targetItem.toPlayerItem(null, playbackPositionMs)
        val existingIdx = playerItems.indexOfFirst { it.itemId == itemId }
        if (existingIdx >= 0) {
            playerItems[existingIdx] = refreshed
        } else {
            playerItems.add(refreshed)
        }
        return refreshed
    }

    fun getSeasonEpisodes(): List<JellyxEpisode> = items.filterIsInstance<JellyxEpisode>()

    fun getCurrentEpisodeSeasonAndSeries(): Pair<UUID, UUID>? {
        val episode =
            (items.getOrNull(currentItemIndex) as? JellyxEpisode)
                ?: (startItem as? JellyxEpisode)
                ?: return null
        return episode.seasonId to episode.seriesId
    }

    fun getChannelIdAtOffset(delta: Int): UUID? {
        if (startItem !is JellyxChannel || items.isEmpty()) return null
        val nextIndex = ((currentItemIndex + delta) % items.size + items.size) % items.size
        return items.getOrNull(nextIndex)?.id
    }

    fun getLiveChannels(): List<JellyxChannel> {
        return items.filterIsInstance<JellyxChannel>()
    }

    fun getCurrentChannel(): JellyxChannel? {
        return (items.getOrNull(currentItemIndex) as? JellyxChannel)
            ?: (startItem as? JellyxChannel)
    }

    fun updateChannelFavorite(channelId: UUID, favorite: Boolean): List<JellyxChannel> {
        val currentId = items.getOrNull(currentItemIndex)?.id ?: startItem?.id
        val updated =
            items
                .filterIsInstance<JellyxChannel>()
                .map { channel ->
                    if (channel.id == channelId) channel.copy(favorite = favorite) else channel
                }
                .sortedWith(
                    compareByDescending<JellyxChannel> { it.favorite }
                        .thenBy { it.channelNumber?.toDoubleOrNull() ?: Double.MAX_VALUE }
                        .thenBy { it.displayName.lowercase() }
                )
        items = updated
        if (currentId != null) {
            val newIndex = items.indexOfFirst { it.id == currentId }
            if (newIndex >= 0) {
                currentItemIndex = newIndex
            }
        }
        val startChannel = startItem as? JellyxChannel
        if (startChannel != null && startChannel.id == channelId) {
            startItem = startChannel.copy(favorite = favorite)
        }
        return getLiveChannels()
    }

    suspend fun getPreviousPlayerItem(): PlayerItem? {
        Timber.d("Retrieving previous player item")

        val itemIndex = currentItemIndex - 1
        val playerItem =
            when (startItem) {
                is JellyxMovie,
                is JellyxChannel -> null
                is JellyxEpisode -> {
                    if (currentItemIndex <= 0) {
                        null
                    } else {
                        val item = items[itemIndex]
                        if (playerItems.firstOrNull { it.itemId == item.id } == null) {
                            try {
                                item.toPlayerItem(null, 0L)
                            } catch (e: Exception) {
                                Timber.e("Failed to retrieve previous player item: $e")
                                null
                            }
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }

        if (playerItem != null) {
            playerItems.add(playerItem)
        }

        return playerItem
    }

    suspend fun getNextPlayerItem(): PlayerItem? {
        Timber.d("Retrieving next player item")

        val itemIndex = currentItemIndex + 1
        val playerItem =
            when (startItem) {
                is JellyxMovie,
                is JellyxChannel -> null
                is JellyxEpisode -> {
                    if (currentItemIndex >= items.lastIndex) {
                        null
                    } else {
                        val item = items[itemIndex]
                        if (playerItems.firstOrNull { it.itemId == item.id } == null) {
                            try {
                                item.toPlayerItem(null, 0L)
                            } catch (e: Exception) {
                                Timber.e("Failed to retrieve next player item: $e")
                                null
                            }
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }

        if (playerItem != null) {
            playerItems.add(playerItem)
        }

        return playerItem
    }

    fun setCurrentMediaItemIndex(itemId: UUID) {
        currentItemIndex = items.indexOfFirst { it.id == itemId }
    }

    private suspend fun JellyxItem.toPlayerItem(
        mediaSourceIndex: Int?,
        playbackPosition: Long,
    ): PlayerItem {
        Timber.d("Converting JellyxItem ${this.id} to PlayerItem")

        val mediaSources = repository.getMediaSources(id, true)
        val mediaSource = smartSourceSelector.selectSource(mediaSources, mediaSourceIndex)
        val videoStream =
            mediaSource.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }
        val audioStream =
            mediaSource.mediaStreams.firstOrNull { it.type == MediaStreamType.AUDIO }
        val externalSubtitles =
            mediaSource.mediaStreams
                .filter { mediaStream ->
                    mediaStream.isExternal &&
                        mediaStream.type == MediaStreamType.SUBTITLE &&
                        !mediaStream.path.isNullOrBlank()
                }
                .map { mediaStream ->
                    ExternalSubtitle(
                        mediaStream.title,
                        mediaStream.language,
                        mediaStream.path!!.toUri(),
                        when (mediaStream.codec) {
                            "subrip" -> MimeTypes.APPLICATION_SUBRIP
                            "webvtt" -> MimeTypes.TEXT_VTT
                            "ass" -> MimeTypes.TEXT_SSA
                            else -> MimeTypes.TEXT_UNKNOWN
                        },
                    )
                }
        val trickplayInfo =
            when (this) {
                is JellyxSources -> {
                    this.trickplayInfo?.get(mediaSource.id)?.let {
                        TrickplayInfo(
                            width = it.width,
                            height = it.height,
                            tileWidth = it.tileWidth,
                            tileHeight = it.tileHeight,
                            thumbnailCount = it.thumbnailCount,
                            interval = it.interval,
                            bandwidth = it.bandwidth,
                        )
                    }
                }
                else -> null
            }
        return PlayerItem(
            name = name,
            itemId = id,
            mediaSourceId = mediaSource.id,
            mediaSourceUri = mediaSource.path,
            playbackPosition = playbackPosition,
            parentIndexNumber = if (this is JellyxEpisode) parentIndexNumber else null,
            indexNumber = if (this is JellyxEpisode) indexNumber else null,
            indexNumberEnd = if (this is JellyxEpisode) indexNumberEnd else null,
            externalSubtitles = externalSubtitles,
            chapters = chapters.toPlayerChapters(),
            trickplayInfo = trickplayInfo,
            isLiveTv = this is JellyxChannel,
            sourceName = mediaSource.name,
            sourceBitrate = mediaSource.bitrate,
            videoRangeLabel = videoStream?.videoRangeType?.serialName ?: "SDR",
            videoStreamBitrate = videoStream?.bitRate,
            audioStreamBitrate = audioStream?.bitRate,
            totalSourcesCount = mediaSources.size,
            liveStreamId = mediaSource.liveStreamId,
        )
    }

    private fun List<JellyxChapter>.toPlayerChapters(): List<PlayerChapter> {
        return this.map { chapter ->
            PlayerChapter(startPosition = chapter.startPosition, name = chapter.name)
        }
    }
}
