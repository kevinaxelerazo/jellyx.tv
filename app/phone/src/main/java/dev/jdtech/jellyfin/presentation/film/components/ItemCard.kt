package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.presentation.components.CompactRatingsOverlay
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxRatings
import dev.jdtech.jellyfin.models.JellyxSeason
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.models.toNativeRatings
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun ItemCard(
    item: JellyxItem,
    direction: Direction,
    onClick: (JellyxItem) -> Unit,
    modifier: Modifier = Modifier,
    showSeriesName: Boolean = false,
    ratings: JellyxRatings? = null,
    showRatingsOverlay: Boolean = false,
) {
    val width =
        when (direction) {
            Direction.HORIZONTAL -> 260
            Direction.VERTICAL -> 150
        }
    Column(
        modifier =
            modifier
                .width(width.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = { onClick(item) })
    ) {
        Surface(shape = MaterialTheme.shapes.small) {
            Box {
                ItemPoster(item = item, direction = direction)
                Row(
                    modifier =
                        Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                ) {
                    if (item.played) PlayedBadge()
                    item.unplayedItemCount?.takeIf { it > 0 }?.let { ItemCountBadge(it) }
                }
                val downloadBadgeAlignment =
                    if (showRatingsOverlay) Alignment.TopStart else Alignment.BottomEnd
                when {
                    item.isDownloading() ->
                        DownloadingPieBadge(
                            modifier =
                                Modifier.align(downloadBadgeAlignment)
                                    .padding(MaterialTheme.spacings.small)
                        )
                    item.isDownloaded() ->
                        DownloadedBadge(
                            modifier =
                                Modifier.align(downloadBadgeAlignment)
                                    .padding(MaterialTheme.spacings.small)
                        )
                }
                if (showRatingsOverlay) {
                    val effectiveRatings = ratings ?: item.toNativeRatings()
                    CompactRatingsOverlay(
                        ratings = effectiveRatings,
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    )
                }
                if (direction == Direction.HORIZONTAL) {
                    val progressWidth =
                        if (item.isDownloaded() || item.isDownloading()) width - 36 else width
                    ProgressBar(
                        item = item,
                        width = progressWidth,
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(MaterialTheme.spacings.small),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        val title =
            when (item) {
                is JellyxEpisode -> item.seriesName
                is JellyxSeason -> if (showSeriesName) item.seriesName else item.name
                else -> item.name
            }
        val subtitle =
            when (item) {
                is JellyxEpisode ->
                    stringResource(
                        id = R.string.episode_name_extended,
                        item.parentIndexNumber,
                        item.indexNumber,
                        item.name,
                    )
                is JellyxSeason -> if (showSeriesName) item.name else null
                else -> null
            }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (subtitle == null) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewMovie() {
    JellyxTheme { ItemCard(item = dummyMovie, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewMovieVertical() {
    JellyxTheme { ItemCard(item = dummyMovie, direction = Direction.VERTICAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewEpisode() {
    JellyxTheme { ItemCard(item = dummyEpisode, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewEpisodeVertical() {
    JellyxTheme { ItemCard(item = dummyEpisode, direction = Direction.VERTICAL, onClick = {}) }
}
