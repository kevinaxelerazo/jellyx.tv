package dev.jdtech.jellyfin.presentation.film

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.genres.GenresViewModel
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import java.util.UUID

@Composable
fun GenresScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    isLoading: (Boolean) -> Unit,
    viewModel: GenresViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val firstGenreFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isLoadingGenres, state.isLoadingItems) {
        isLoading(state.isLoadingGenres || state.isLoadingItems)
    }

    LaunchedEffect(state.genres) {
        if (state.genres.isNotEmpty() && state.selectedGenre == null) {
            runCatching { firstGenreFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = MaterialTheme.spacings.large)
                .padding(top = MaterialTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
    ) {
        val selectedGenre = state.selectedGenre
        if (selectedGenre == null) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall)) {
                Text(
                    text = stringResource(CoreR.string.genres),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(FilmR.string.select_genre_to_filter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.genres.isEmpty() && state.isLoadingGenres) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    JellyxLoadingLogo()
                }
            } else if (state.genres.isEmpty() && !state.isLoadingGenres) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No se encontraron géneros en tu servidor",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 165.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = MaterialTheme.spacings.large),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.genres, key = { _, genre -> genre }) { index, genre ->
                        Surface(
                            onClick = { viewModel.selectGenre(genre) },
                            colors =
                                ClickableSurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                ),
                            border =
                                ClickableSurfaceDefaults.border(
                                    focusedBorder =
                                        Border(
                                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(52.dp)
                                    .then(
                                        if (index == 0) {
                                            Modifier.focusRequester(firstGenreFocusRequester)
                                        } else {
                                            Modifier
                                        }
                                    ),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = genre,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            BackHandler(enabled = true) { viewModel.clearSelectedGenre() }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { viewModel.clearSelectedGenre() }) {
                    Text(text = "← ${stringResource(FilmR.string.change_genre)}")
                }
                Text(
                    text = selectedGenre,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "(${state.items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.isLoadingItems && state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    JellyxLoadingLogo()
                }
            } else if (!state.isLoadingItems && state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(CoreR.string.collection_no_media),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(bottom = MaterialTheme.spacings.large),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ItemCard(
                            item = item,
                            direction = Direction.VERTICAL,
                            onClick = { clicked ->
                                when (clicked) {
                                    is JellyxMovie -> navigateToMovie(clicked.id)
                                    is JellyxShow -> navigateToShow(clicked.id)
                                    else -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

