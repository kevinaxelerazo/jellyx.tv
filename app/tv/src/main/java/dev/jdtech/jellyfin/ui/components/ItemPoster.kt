package dev.jdtech.jellyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
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
    val imageUri =
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

    AsyncImage(
        model = imageUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(if (direction == Direction.HORIZONTAL) 1.77f else 0.66f)
                .background(MaterialTheme.colorScheme.surface),
    )
}
