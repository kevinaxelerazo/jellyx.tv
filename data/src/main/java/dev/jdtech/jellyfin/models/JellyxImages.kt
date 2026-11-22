package dev.jdtech.jellyfin.models

import android.net.Uri
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

data class JellyxImages(
    val primary: Uri? = null,
    val backdrop: Uri? = null,
    val logo: Uri? = null,
    val showPrimary: Uri? = null,
    val showBackdrop: Uri? = null,
    val showLogo: Uri? = null,
)

fun BaseItemDto.toJellyxImages(jellyfinRepository: JellyfinRepository): JellyxImages {
    val baseUrl = Uri.parse(jellyfinRepository.getBaseUrl())
    val primary =
        imageTags?.get(ImageType.PRIMARY)?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$id/Images/${ImageType.PRIMARY}")
                .appendQueryParameter("tag", tag)
                .build()
        }
    val backdrop =
        backdropImageTags?.firstOrNull()?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$id/Images/${ImageType.BACKDROP}/0")
                .appendQueryParameter("tag", tag)
                .build()
        }
    val logo =
        imageTags?.get(ImageType.LOGO)?.let { tag ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$id/Images/${ImageType.LOGO}")
                .appendQueryParameter("tag", tag)
                .build()
        }
    val showPrimary =
        seriesId?.let { sId ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$sId/Images/${ImageType.PRIMARY}")
                .apply { seriesPrimaryImageTag?.let { tag -> appendQueryParameter("tag", tag) } }
                .build()
        }
    val backdropOwnerId = parentBackdropItemId ?: seriesId
    val parentBackdropTag = parentBackdropImageTags?.firstOrNull()
    val showBackdrop =
        backdropOwnerId?.let { ownerId ->
            baseUrl
                .buildUpon()
                .appendEncodedPath("items/$ownerId/Images/${ImageType.BACKDROP}/0")
                .apply { parentBackdropTag?.let { tag -> appendQueryParameter("tag", tag) } }
                .build()
        }
    val showLogo =
        (parentLogoItemId ?: seriesId)?.let { ownerId ->
            parentLogoImageTag?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("items/$ownerId/Images/${ImageType.LOGO}")
                    .appendQueryParameter("tag", tag)
                    .build()
            }
        }

    return JellyxImages(
        primary = primary,
        backdrop = backdrop,
        logo = logo,
        showPrimary = showPrimary,
        showBackdrop = showBackdrop,
        showLogo = showLogo,
    )
}

fun JellyxMovieDto.toLocalJellyxImages(itemId: UUID): JellyxImages {
    return JellyxImages(
        primary = Uri.Builder().appendEncodedPath("images/$itemId/primary").build(),
        backdrop = Uri.Builder().appendEncodedPath("images/$itemId/backdrop").build(),
    )
}

fun JellyxShowDto.toLocalJellyxImages(itemId: UUID): JellyxImages {
    return JellyxImages(
        primary = Uri.Builder().appendEncodedPath("images/$itemId/primary").build(),
        backdrop = Uri.Builder().appendEncodedPath("images/$itemId/backdrop").build(),
    )
}

fun JellyxSeasonDto.toLocalJellyxImages(itemId: UUID): JellyxImages {
    return JellyxImages(
        primary = Uri.Builder().appendEncodedPath("images/$itemId/primary").build(),
        backdrop = Uri.Builder().appendEncodedPath("images/$itemId/backdrop").build(),
        showPrimary = Uri.Builder().appendEncodedPath("images/$seriesId/primary").build(),
        showBackdrop = Uri.Builder().appendEncodedPath("images/$seriesId/backdrop").build(),
    )
}

fun JellyxEpisodeDto.toLocalJellyxImages(itemId: UUID): JellyxImages {
    return JellyxImages(
        primary = Uri.Builder().appendEncodedPath("images/$itemId/primary").build(),
        backdrop = Uri.Builder().appendEncodedPath("images/$itemId/backdrop").build(),
        showPrimary = Uri.Builder().appendEncodedPath("images/$seriesId/primary").build(),
        showBackdrop = Uri.Builder().appendEncodedPath("images/$seriesId/backdrop").build(),
    )
}
