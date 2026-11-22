package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.PlayAccess

data class JellyxEpisode(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    val indexNumber: Int,
    val indexNumberEnd: Int?,
    val parentIndexNumber: Int,
    override val sources: List<JellyxSource>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    val premiereDate: DateTime?,
    val seriesId: UUID,
    val seriesName: String,
    val seasonId: UUID,
    val seasonName: String?,
    val communityRating: Float?,
    override val criticRating: Float? = null,
    override val providerIds: Map<String, String> = emptyMap(),
    val people: List<JellyxItemPerson>,
    override val unplayedItemCount: Int? = null,
    val missing: Boolean = false,
    override val images: JellyxImages,
    override val chapters: List<JellyxChapter>,
    override val trickplayInfo: Map<String, JellyxTrickplayInfo>?,
) : JellyxItem, JellyxSources

suspend fun BaseItemDto.toJellyxEpisode(
    jellyfinRepository: JellyfinRepository,
    database: ServerDatabaseDao? = null,
): JellyxEpisode? {
    val sources = mutableListOf<JellyxSource>()
    sources.addAll(mediaSources?.map { it.toJellyxSource(jellyfinRepository, id) } ?: emptyList())
    if (database != null) {
        sources.addAll(database.getSources(id).map { it.toJellyxSource(database) })
    }
    return try {
        JellyxEpisode(
            id = id,
            name = name.orEmpty(),
            originalTitle = originalTitle,
            overview = overview.orEmpty(),
            indexNumber = indexNumber ?: 0,
            indexNumberEnd = indexNumberEnd,
            parentIndexNumber = parentIndexNumber ?: 0,
            sources = sources,
            played = userData?.played == true,
            favorite = userData?.isFavorite == true,
            canPlay = playAccess != PlayAccess.NONE,
            canDownload = canDownload == true,
            runtimeTicks = runTimeTicks ?: 0,
            playbackPositionTicks = userData?.playbackPositionTicks ?: 0L,
            premiereDate = premiereDate,
            seriesId = seriesId!!,
            seriesName = seriesName.orEmpty(),
            seasonId = seasonId!!,
            seasonName = seasonName,
            communityRating = communityRating,
            criticRating = criticRating,
            providerIds = providerIds.orEmpty().mapNotNull { (k, v) -> v?.let { k to it } }.toMap(),
            people = people?.map { it.toJellyxPerson(jellyfinRepository) } ?: emptyList(),
            missing = locationType == LocationType.VIRTUAL,
            images = toJellyxImages(jellyfinRepository),
            chapters = toJellyxChapters(),
            trickplayInfo =
                trickplay
                    ?.mapNotNull { (width, info) ->
                        info?.get(info.keys.max())?.toJellyxTrickplayInfo()?.let { width to it }
                    }
                    ?.toMap(),
        )
    } catch (_: NullPointerException) {
        null
    }
}

suspend fun JellyxEpisodeDto.toJellyxEpisode(
    database: ServerDatabaseDao,
    userId: UUID,
): JellyxEpisode {
    val userData = database.getUserDataOrCreateNew(id, userId)
    val sources = database.getSources(id).map { it.toJellyxSource(database) }
    val trickplayInfos = mutableMapOf<String, JellyxTrickplayInfo>()
    for (source in sources) {
        database.getTrickplayInfo(source.id)?.toJellyxTrickplayInfo()?.let {
            trickplayInfos[source.id] = it
        }
    }
    return JellyxEpisode(
        id = id,
        name = name,
        originalTitle = "",
        overview = overview,
        indexNumber = indexNumber,
        indexNumberEnd = indexNumberEnd,
        parentIndexNumber = parentIndexNumber,
        sources = sources,
        played = userData.played,
        favorite = userData.favorite,
        canPlay = true,
        canDownload = false,
        runtimeTicks = runtimeTicks,
        playbackPositionTicks = userData.playbackPositionTicks,
        premiereDate = premiereDate,
        seriesId = seriesId,
        seriesName = seriesName,
        seasonId = seasonId,
        seasonName = null,
        communityRating = communityRating,
        people = emptyList(),
        images = toLocalJellyxImages(itemId = id),
        chapters = chapters ?: emptyList(),
        trickplayInfo = trickplayInfos,
    )
}
