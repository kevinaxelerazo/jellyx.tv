package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun EpisodeCard(episode: JellyxEpisode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.background

    Row(
        modifier =
            modifier
                .height(84.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
    ) {
        Box {
            ItemPoster(
                item = episode,
                direction = Direction.HORIZONTAL,
                modifier = Modifier.clip(MaterialTheme.shapes.small),
            )
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            ) {
                if (episode.played) PlayedBadge()
            }
            when {
                episode.isDownloading() ->
                    DownloadingPieBadge(
                        modifier =
                            Modifier.align(Alignment.BottomEnd).padding(MaterialTheme.spacings.small)
                    )
                episode.isDownloaded() ->
                    DownloadedBadge(
                        modifier =
                            Modifier.align(Alignment.BottomEnd).padding(MaterialTheme.spacings.small)
                    )
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacings.default / 2))
        Box(modifier = Modifier.fillMaxHeight()) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            stringResource(
                                id = dev.jdtech.jellyfin.core.R.string.episode_name,
                                episode.indexNumber,
                                episode.name,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (episode.isDownloaded()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier =
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = stringResource(FilmR.string.downloaded_badge_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    } else if (episode.isDownloading()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(FilmR.string.downloading_badge_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Text(
                    text = episode.overview,
                    modifier = Modifier.alpha(0.7f),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Canvas(
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(MaterialTheme.spacings.default)
            ) {
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, backgroundColor),
                            startY = 0f,
                        )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EpisodeCardPreview() {
    JellyxTheme { EpisodeCard(episode = dummyEpisode, onClick = {}) }
}
