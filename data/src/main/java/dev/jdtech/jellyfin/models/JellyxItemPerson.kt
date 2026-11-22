package dev.jdtech.jellyfin.models

import android.net.Uri
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind

data class JellyxItemPersonImage(val uri: Uri?, val blurHash: String?)

fun BaseItemPerson.toJellyxImage(repository: JellyfinRepository): JellyxItemPersonImage {
    val baseUrl = Uri.parse(repository.getBaseUrl())
    return JellyxItemPersonImage(
        uri =
            primaryImageTag?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("items/$id/Images/${ImageType.PRIMARY}")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.get(primaryImageTag),
    )
}

data class JellyxItemPerson(
    val id: UUID,
    val name: String,
    val type: PersonKind,
    val role: String,
    val image: JellyxItemPersonImage,
)

fun BaseItemPerson.toJellyxPerson(repository: JellyfinRepository): JellyxItemPerson {
    return JellyxItemPerson(
        id = id,
        name = name.orEmpty(),
        type = type,
        role = role.orEmpty(),
        image = toJellyxImage(repository),
    )
}
