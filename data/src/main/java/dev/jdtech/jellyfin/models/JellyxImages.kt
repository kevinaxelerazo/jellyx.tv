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
            buildScaledImageUri(
                baseUrl = baseUrl,
                encodedPath = "items/$id/Images/${ImageType.PRIMARY}",
                tag = tag,
                maxWidth = PRIMARY_MAX_WIDTH,
                quality = PRIMARY_QUALITY,
            )
        }
    val backdrop =
        backdropImageTags?.firstOrNull()?.let { tag ->
            buildScaledImageUri(
                baseUrl = baseUrl,
                encodedPath = "items/$id/Images/${ImageType.BACKDROP}/0",
                tag = tag,
                maxWidth = BACKDROP_MAX_WIDTH,
                quality = BACKDROP_QUALITY,
            )
        }
    val logo =
        imageTags?.get(ImageType.LOGO)?.let { tag ->
            buildScaledImageUri(
                baseUrl = baseUrl,
                encodedPath = "items/$id/Images/${ImageType.LOGO}",
                tag = tag,
                maxWidth = LOGO_MAX_WIDTH,
                quality = LOGO_QUALITY,
            )
        }
    val showPrimary =
        seriesId?.let { sId ->
            buildScaledImageUri(
                baseUrl = baseUrl,
                encodedPath = "items/$sId/Images/${ImageType.PRIMARY}",
                tag = seriesPrimaryImageTag,
                maxWidth = PRIMARY_MAX_WIDTH,
                quality = PRIMARY_QUALITY,
            )
        }
    val backdropOwnerId = parentBackdropItemId ?: seriesId
    val parentBackdropTag = parentBackdropImageTags?.firstOrNull()
    val showBackdrop =
        backdropOwnerId?.let { ownerId ->
            buildScaledImageUri(
                baseUrl = baseUrl,
                encodedPath = "items/$ownerId/Images/${ImageType.BACKDROP}/0",
                tag = parentBackdropTag,
                maxWidth = BACKDROP_MAX_WIDTH,
                quality = BACKDROP_QUALITY,
            )
        }
    val showLogo =
        (parentLogoItemId ?: seriesId)?.let { ownerId ->
            parentLogoImageTag?.let { tag ->
                buildScaledImageUri(
                    baseUrl = baseUrl,
                    encodedPath = "items/$ownerId/Images/${ImageType.LOGO}",
                    tag = tag,
                    maxWidth = LOGO_MAX_WIDTH,
                    quality = LOGO_QUALITY,
                )
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

private const val PRIMARY_MAX_WIDTH = "500"
private const val PRIMARY_QUALITY = "85"
private const val BACKDROP_MAX_WIDTH = "1280"
private const val BACKDROP_QUALITY = "85"
private const val LOGO_MAX_WIDTH = "600"
private const val LOGO_QUALITY = "90"

private fun buildScaledImageUri(
    baseUrl: Uri,
    encodedPath: String,
    tag: String?,
    maxWidth: String,
    quality: String,
): Uri =
    baseUrl
        .buildUpon()
        .appendEncodedPath(encodedPath)
        .apply { tag?.let { appendQueryParameter("tag", it) } }
        .appendQueryParameter("maxWidth", maxWidth)
        .appendQueryParameter("quality", quality)
        .build()

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
