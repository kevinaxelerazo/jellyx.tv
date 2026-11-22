package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class JellyxBoxSet(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String = "",
    override val played: Boolean = false,
    override val favorite: Boolean = false,
    override val canPlay: Boolean = false,
    override val canDownload: Boolean = false,
    override val sources: List<JellyxSource> = emptyList(),
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int? = null,
    override val images: JellyxImages,
    override val chapters: List<JellyxChapter> = emptyList(),
) : JellyxItem

fun BaseItemDto.toJellyxBoxSet(jellyfinRepository: JellyfinRepository): JellyxBoxSet {
    return JellyxBoxSet(
        id = id,
        name = name.orEmpty(),
        images = toJellyxImages(jellyfinRepository),
    )
}
