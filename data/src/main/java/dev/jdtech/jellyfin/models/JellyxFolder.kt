package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class JellyxFolder(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String = "",
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean = false,
    override val canDownload: Boolean = false,
    override val sources: List<JellyxSource> = emptyList(),
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int?,
    override val images: JellyxImages,
    override val chapters: List<JellyxChapter> = emptyList(),
) : JellyxItem

fun BaseItemDto.toJellyxFolder(jellyfinRepository: JellyfinRepository): JellyxFolder {
    return JellyxFolder(
        id = id,
        name = name.orEmpty(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        unplayedItemCount = userData?.unplayedItemCount,
        images = toJellyxImages(jellyfinRepository),
    )
}
