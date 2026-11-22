package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayAccess

data class JellyxShow(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    override val sources: List<JellyxSource>,
    val seasons: List<JellyxSeason>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int?,
    val genres: List<String>,
    val people: List<JellyxItemPerson>,
    override val runtimeTicks: Long,
    val communityRating: Float?,
    override val criticRating: Float? = null,
    override val providerIds: Map<String, String> = emptyMap(),
    val officialRating: String?,
    val status: String,
    val productionYear: Int?,
    val endDate: DateTime?,
    val trailer: String?,
    override val images: JellyxImages,
    override val chapters: List<JellyxChapter> = emptyList(),
) : JellyxItem

fun BaseItemDto.toJellyxShow(jellyfinRepository: JellyfinRepository): JellyxShow {
    return JellyxShow(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        unplayedItemCount = userData?.unplayedItemCount,
        sources = emptyList(),
        seasons = emptyList(),
        genres = genres ?: emptyList(),
        people = people?.map { it.toJellyxPerson(jellyfinRepository) } ?: emptyList(),
        runtimeTicks = runTimeTicks ?: 0,
        communityRating = communityRating,
        criticRating = criticRating,
        providerIds = providerIds.orEmpty().mapNotNull { (k, v) -> v?.let { k to it } }.toMap(),
        officialRating = officialRating,
        status = status ?: "Ended",
        productionYear = productionYear,
        endDate = endDate,
        trailer = remoteTrailers?.getOrNull(0)?.url,
        images = toJellyxImages(jellyfinRepository),
    )
}

suspend fun JellyxShowDto.toJellyxShow(
    database: ServerDatabaseDao,
    userId: UUID,
): JellyxShow {
    val userData = database.getUserDataOrCreateNew(id, userId)
    return JellyxShow(
        id = id,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        played = userData.played,
        favorite = userData.favorite,
        canPlay = true,
        canDownload = false,
        unplayedItemCount = null,
        sources = emptyList(),
        seasons = emptyList(),
        genres = emptyList(),
        people = emptyList(),
        runtimeTicks = runtimeTicks,
        communityRating = communityRating,
        officialRating = officialRating,
        status = status,
        productionYear = productionYear,
        endDate = endDate,
        trailer = null,
        images = toLocalJellyxImages(itemId = id),
    )
}
