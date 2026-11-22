package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxShow

enum class Direction {
    HORIZONTAL,
    VERTICAL,
}

@Composable
fun ItemPoster(item: JellyxItem, direction: Direction, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var imageUri =
        when (direction) {
            Direction.HORIZONTAL -> {
                when (item) {
                    is JellyxMovie, is JellyxShow -> item.images.backdrop ?: item.images.primary
                    is JellyxEpisode ->
                        item.images.primary
                            ?: item.images.showBackdrop
                            ?: item.images.backdrop
                            ?: item.images.showPrimary
                    else ->
                        item.images.primary
                            ?: item.images.backdrop
                            ?: item.images.showBackdrop
                            ?: item.images.showPrimary
                }
            }
            Direction.VERTICAL -> {
                when (item) {
                    is JellyxEpisode -> item.images.showPrimary ?: item.images.primary
                    else ->
                        item.images.primary
                            ?: item.images.showPrimary
                            ?: item.images.backdrop
                            ?: item.images.showBackdrop
                }
            }
        }

    // Ugly workaround to append the files directory when loading local images
    if (imageUri != null && imageUri.scheme == null) {
        imageUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(imageUri.path)
                .build()
    }

    Box(
        modifier =
            modifier
                .aspectRatio(if (direction == Direction.HORIZONTAL) 1.77f else 0.66f)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri == null && item is JellyxChannel) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_tv),
                contentDescription = item.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
