package dev.jdtech.jellyfin.core.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.models.JellyxRatings
import dev.jdtech.jellyfin.models.RottenTomatoesStatus
import java.util.Locale

@Composable
fun RatingsRow(
    ratings: JellyxRatings,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
) {
    val hasAny =
        ratings.imdbRating != null ||
            ratings.rtCriticPercentage != null ||
            ratings.rtAudiencePercentage != null ||
            ratings.tmdbPercentage != null
    if (!hasAny) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ratings.imdbRating?.let { imdb ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_imdb),
                    contentDescription = "IMDb",
                    modifier = Modifier.size(width = 24.dp, height = 14.dp),
                )
                Text(
                    text = String.format(Locale.US, "%.1f", imdb),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
            }
        }

        ratings.rtCriticPercentage?.let { rt ->
            val iconRes =
                if (ratings.rtCriticStatus == RottenTomatoesStatus.ROTTEN) {
                    R.drawable.ic_rotten_splat
                } else {
                    R.drawable.ic_rotten_fresh
                }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = "Rotten Tomatoes",
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "$rt%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
            }
        }

        ratings.rtAudiencePercentage?.let { audience ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_popcorn),
                    contentDescription = "Rotten Tomatoes Audience",
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "$audience%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
            }
        }

        ratings.tmdbPercentage?.let { tmdb ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_tmdb),
                    contentDescription = "TMDb",
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "$tmdb%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
fun CompactRatingsOverlay(
    ratings: JellyxRatings,
    modifier: Modifier = Modifier,
) {
    val hasAny =
        ratings.imdbRating != null ||
            ratings.tmdbPercentage != null ||
            ratings.rtCriticPercentage != null ||
            ratings.rtAudiencePercentage != null
    if (!hasAny) return

    Box(
        modifier =
            modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
                .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 5.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ratings.imdbRating?.let { imdb ->
                CompactRatingItem(
                    iconRes = R.drawable.ic_imdb,
                    contentDescription = "IMDb",
                    iconModifier = Modifier.size(width = 16.dp, height = 9.dp),
                    text = String.format(Locale.US, "%.1f", imdb),
                )
            }

            ratings.tmdbPercentage?.let { tmdb ->
                CompactRatingItem(
                    iconRes = R.drawable.ic_tmdb,
                    contentDescription = "TMDb",
                    iconModifier = Modifier.size(10.dp),
                    text = "$tmdb%",
                )
            }

            ratings.rtCriticPercentage?.let { rt ->
                val iconRes =
                    if (ratings.rtCriticStatus == RottenTomatoesStatus.ROTTEN) {
                        R.drawable.ic_rotten_splat
                    } else {
                        R.drawable.ic_rotten_fresh
                    }
                CompactRatingItem(
                    iconRes = iconRes,
                    contentDescription = "Rotten Tomatoes",
                    iconModifier = Modifier.size(10.dp),
                    text = "$rt%",
                )
            }

            if (ratings.rtCriticPercentage == null) {
                ratings.rtAudiencePercentage?.let { audience ->
                    CompactRatingItem(
                        iconRes = R.drawable.ic_popcorn,
                        contentDescription = "Rotten Tomatoes Audience",
                        iconModifier = Modifier.size(10.dp),
                        text = "$audience%",
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRatingItem(
    iconRes: Int,
    contentDescription: String,
    iconModifier: Modifier,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = iconModifier,
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}
