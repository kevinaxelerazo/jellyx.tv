package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class JellyxPerson(
    val id: UUID,
    val name: String,
    val overview: String,
    val images: JellyxImages,
)

fun BaseItemDto.toJellyxPerson(repository: JellyfinRepository): JellyxPerson {
    return JellyxPerson(
        id = id,
        name = name.orEmpty(),
        overview = overview.orEmpty(),
        images = toJellyxImages(repository),
    )
}
