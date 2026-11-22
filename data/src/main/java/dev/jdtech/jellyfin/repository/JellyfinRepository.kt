package dev.jdtech.jellyfin.repository

import androidx.paging.PagingData
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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserConfiguration

interface JellyfinRepository {
    suspend fun getPublicSystemInfo(): PublicSystemInfo

    suspend fun getUserViews(): List<BaseItemDto>

    suspend fun getEpisode(itemId: UUID): JellyxEpisode

    suspend fun getMovie(itemId: UUID): JellyxMovie

    suspend fun getShow(itemId: UUID): JellyxShow

    suspend fun getSeason(itemId: UUID): JellyxSeason

    suspend fun getLibraries(): List<JellyxCollection>

    suspend fun getItem(itemId: UUID): JellyxItem?

    suspend fun getItems(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        startIndex: Int? = null,
        limit: Int? = null,
    ): List<JellyxItem>

    suspend fun getItemsPaging(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
    ): Flow<PagingData<JellyxItem>>

    suspend fun getPerson(personId: UUID): JellyxPerson

    suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = true,
    ): List<JellyxItem>

    suspend fun getFavoriteItems(): List<JellyxItem>

    suspend fun getSearchItems(query: String): List<JellyxItem>

    suspend fun getSuggestions(): List<JellyxItem>

    suspend fun getSimilarItems(itemId: UUID, limit: Int = 4): List<JellyxItem>

    suspend fun getGenres(): List<String>

    suspend fun getItemsByGenre(genre: String): List<JellyxItem>

    suspend fun getResumeItems(): List<JellyxItem>

    suspend fun getLatestMedia(parentId: UUID): List<JellyxItem>

    suspend fun getSeasons(seriesId: UUID, offline: Boolean = false): List<JellyxSeason>

    suspend fun getNextUp(seriesId: UUID? = null): List<JellyxEpisode>

    suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>? = null,
        startItemId: UUID? = null,
        limit: Int? = null,
        offline: Boolean = false,
    ): List<JellyxEpisode>

    suspend fun getMediaSources(itemId: UUID, includePath: Boolean = false): List<JellyxSource>

    suspend fun closeLiveStream(liveStreamId: String)

    suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String

    suspend fun getSegments(itemId: UUID): List<JellyxSegment>

    suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray?

    suspend fun postCapabilities()

    suspend fun postPlaybackStart(itemId: UUID)

    suspend fun postPlaybackStop(itemId: UUID, positionTicks: Long, playedPercentage: Int)

    suspend fun postPlaybackProgress(itemId: UUID, positionTicks: Long, isPaused: Boolean)

    suspend fun markAsFavorite(itemId: UUID)

    suspend fun unmarkAsFavorite(itemId: UUID)

    suspend fun markAsPlayed(itemId: UUID)

    suspend fun markAsUnplayed(itemId: UUID)

    fun getBaseUrl(): String

    fun getAccessToken(): String? = null

    suspend fun updateDeviceName(name: String)

    suspend fun getUserConfiguration(): UserConfiguration?

    suspend fun getDownloads(): List<JellyxItem>

    suspend fun getLiveTvChannels(): List<JellyxChannel>

    suspend fun getLiveTvChannel(channelId: UUID): JellyxChannel

    suspend fun getSyncPlayGroups(): List<SyncPlayGroupInfo>

    suspend fun createSyncPlayGroup(groupName: String): Boolean

    suspend fun joinSyncPlayGroup(groupId: UUID): Boolean

    suspend fun leaveSyncPlayGroup(): Boolean

    suspend fun sendSyncPlayPause()

    suspend fun sendSyncPlayUnpause()

    suspend fun sendSyncPlaySeek(positionMs: Long)

    fun getUserId(): UUID
}

data class SyncPlayGroupInfo(
    val groupId: UUID,
    val groupName: String,
    val participants: List<String>,
)
