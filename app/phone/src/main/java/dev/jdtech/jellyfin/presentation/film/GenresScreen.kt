package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.genres.GenresViewModel
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ItemCard
import dev.jdtech.jellyfin.presentation.theme.spacings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    onItemClick: (JellyxItem) -> Unit,
    viewModel: GenresViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.genres) {
        if (state.genres.isNotEmpty() && state.selectedGenre == null) {
            viewModel.ensureInitialGenreSelected()
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = MaterialTheme.spacings.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        Text(
            text = stringResource(CoreR.string.genres),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = MaterialTheme.spacings.small),
        )

        if (state.isLoadingGenres) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                JellyxLoadingLogo()
            }
        } else if (state.genres.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No se encontraron géneros en tu servidor",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = state.selectedGenre ?: stringResource(FilmR.string.select_genre),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(CoreR.string.genres)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier =
                        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    state.genres.forEach { genre ->
                        val isSelected = genre == state.selectedGenre
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = genre,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color =
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                expanded = false
                                viewModel.selectGenre(genre)
                            },
                        )
                    }
                }
            }

            if (state.isLoadingItems) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    JellyxLoadingLogo()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(bottom = MaterialTheme.spacings.large),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ItemCard(
                            item = item,
                            direction = Direction.VERTICAL,
                            onClick = onItemClick,
                        )
                    }
                }
            }
        }
    }
}

