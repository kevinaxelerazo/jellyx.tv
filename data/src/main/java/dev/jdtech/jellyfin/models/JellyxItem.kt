package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

interface JellyxItem {
    val id: UUID
    val name: String
    val originalTitle: String?
    val overview: String
    val played: Boolean
    val favorite: Boolean
    val canPlay: Boolean
    val canDownload: Boolean
    val sources: List<JellyxSource>
    val runtimeTicks: Long
    val playbackPositionTicks: Long
    val unplayedItemCount: Int?
    val images: JellyxImages
    val chapters: List<JellyxChapter>
    val criticRating: Float?
        get() = null

    val providerIds: Map<String, String>
        get() = emptyMap()
}

suspend fun BaseItemDto.toJellyxItem(
    jellyfinRepository: JellyfinRepository,
    serverDatabase: ServerDatabaseDao? = null,
): JellyxItem? {
    return when (type) {
        BaseItemKind.MOVIE -> toJellyxMovie(jellyfinRepository, serverDatabase)
        BaseItemKind.EPISODE -> toJellyxEpisode(jellyfinRepository, serverDatabase)
        BaseItemKind.SEASON -> toJellyxSeason(jellyfinRepository)
        BaseItemKind.SERIES -> toJellyxShow(jellyfinRepository)
        BaseItemKind.BOX_SET -> toJellyxBoxSet(jellyfinRepository)
        BaseItemKind.FOLDER -> toJellyxFolder(jellyfinRepository)
        BaseItemKind.TV_CHANNEL -> toJellyxChannel(jellyfinRepository)
        else -> null
    }
}

fun JellyxItem.isDownloading(): Boolean {
    return sources
        .filter { it.type == JellyxSourceType.LOCAL }
        .any { it.path.endsWith(".download") }
}

fun JellyxItem.isDownloaded(): Boolean {
    return sources
        .filter { it.type == JellyxSourceType.LOCAL }
        .any { !it.path.endsWith(".download") && it.size > 0L }
}
