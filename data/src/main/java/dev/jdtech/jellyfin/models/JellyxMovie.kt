package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayAccess

data class JellyxMovie(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    override val sources: List<JellyxSource>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    val premiereDate: LocalDateTime?,
    val people: List<JellyxItemPerson>,
    val genres: List<String>,
    val communityRating: Float?,
    override val criticRating: Float? = null,
    override val providerIds: Map<String, String> = emptyMap(),
    val officialRating: String?,
    val status: String,
    val productionYear: Int?,
    val endDate: LocalDateTime?,
    val trailer: String?,
    override val unplayedItemCount: Int? = null,
    override val images: JellyxImages,
    override val chapters: List<JellyxChapter>,
    override val trickplayInfo: Map<String, JellyxTrickplayInfo>?,
) : JellyxItem, JellyxSources

suspend fun BaseItemDto.toJellyxMovie(
    jellyfinRepository: JellyfinRepository,
    serverDatabase: ServerDatabaseDao? = null,
): JellyxMovie {
    val sources = mutableListOf<JellyxSource>()
    sources.addAll(mediaSources?.map { it.toJellyxSource(jellyfinRepository, id) } ?: emptyList())
    if (serverDatabase != null) {
        sources.addAll(serverDatabase.getSources(id).map { it.toJellyxSource(serverDatabase) })
    }
    return JellyxMovie(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        sources = sources,
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        runtimeTicks = runTimeTicks ?: 0,
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0,
        premiereDate = premiereDate,
        communityRating = communityRating,
        criticRating = criticRating,
        providerIds = providerIds.orEmpty().mapNotNull { (k, v) -> v?.let { k to it } }.toMap(),
        genres = genres ?: emptyList(),
        people = people?.map { it.toJellyxPerson(jellyfinRepository) } ?: emptyList(),
        officialRating = officialRating,
        status = status ?: "Ended",
        productionYear = productionYear,
        endDate = endDate,
        trailer = remoteTrailers?.getOrNull(0)?.url,
        images = toJellyxImages(jellyfinRepository),
        chapters = toJellyxChapters(),
        trickplayInfo =
            trickplay
                ?.mapNotNull { (width, info) ->
                    info?.get(info.keys.max())?.toJellyxTrickplayInfo()?.let { width to it }
                }
                ?.toMap(),
    )
}

suspend fun JellyxMovieDto.toJellyxMovie(
    database: ServerDatabaseDao,
    userId: UUID,
): JellyxMovie {
    val userData = database.getUserDataOrCreateNew(id, userId)
    val sources = database.getSources(id).map { it.toJellyxSource(database) }
    val trickplayInfos = mutableMapOf<String, JellyxTrickplayInfo>()
    for (source in sources) {
        database.getTrickplayInfo(source.id)?.toJellyxTrickplayInfo()?.let {
            trickplayInfos[source.id] = it
        }
    }
    return JellyxMovie(
        id = id,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        played = userData.played,
        favorite = userData.favorite,
        runtimeTicks = runtimeTicks,
        playbackPositionTicks = userData.playbackPositionTicks,
        premiereDate = premiereDate,
        genres = emptyList(),
        people = emptyList(),
        communityRating = communityRating,
        officialRating = officialRating,
        status = status,
        productionYear = productionYear,
        endDate = endDate,
        canDownload = false,
        canPlay = true,
        sources = database.getSources(id).map { it.toJellyxSource(database) },
        trailer = null,
        images = toLocalJellyxImages(itemId = id),
        chapters = chapters ?: emptyList(),
        trickplayInfo = trickplayInfos,
    )
}
