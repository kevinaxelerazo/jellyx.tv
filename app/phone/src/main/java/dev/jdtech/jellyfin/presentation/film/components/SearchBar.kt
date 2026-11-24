package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.search.DEFAULT_STREAMING_PLATFORM_SHORTCUTS
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchState
import dev.jdtech.jellyfin.models.toNativeRatings
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FilmSearchBar(
    state: SearchState,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
    paddingStart: Dp = 0.dp,
    paddingEnd: Dp = 0.dp,
    prefilledQuery: String? = null,
    onPrefilledQueryConsumed: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safePadding = rememberSafePadding()

    var query by rememberSaveable { mutableStateOf("") }
    var skipNextFocus by remember { mutableStateOf(false) }

    val searchBarPaddingStart by
        animateDpAsState(
            targetValue = if (expanded) 0.dp else paddingStart,
            label = "search_bar_padding_start",
        )

    val searchBarPaddingEnd by
        animateDpAsState(
            targetValue = if (expanded) 0.dp else paddingEnd,
            label = "search_bar_padding_end",
        )

    val searchBarInputPaddingStart by
        animateDpAsState(
            targetValue = if (expanded) safePadding.start else 0.dp,
            label = "search_bar_padding_start",
        )

    val searchBarInputPaddingEnd by
        animateDpAsState(
            targetValue = if (expanded) safePadding.end else 0.dp,
            label = "search_bar_padding_end",
        )

    LaunchedEffect(expanded) {
        if (expanded) {
            if (skipNextFocus) {
                skipNextFocus = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            } else {
                focusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(prefilledQuery) {
        if (!prefilledQuery.isNullOrBlank()) {
            skipNextFocus = true
            query = prefilledQuery
            onExpand(true)
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            onPrefilledQueryConsumed()
        }
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        val matchedPlatform =
            DEFAULT_STREAMING_PLATFORM_SHORTCUTS.firstOrNull {
                it.searchQuery.equals(trimmed, ignoreCase = true) ||
                    it.id.equals(trimmed, ignoreCase = true)
            }
        if (matchedPlatform != null) {
            onAction(SearchAction.SelectPlatform(matchedPlatform.id))
        } else {
            if (query.isNotBlank()) {
                // Debounce scales with input length. Max debounce of 300ms.
                delay(minOf(50L + (query.count() * 50L), 300L))
            }
            onAction(SearchAction.Search(query))
        }
    }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { onExpand(true) },
                expanded = expanded,
                onExpandedChange = { onExpand(it) },
                modifier =
                    Modifier.padding(
                            start = searchBarInputPaddingStart,
                            end = searchBarInputPaddingEnd,
                        )
                        .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = stringResource(FilmR.string.search_placeholder),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                },
                leadingIcon = {
                    AnimatedContent(targetState = expanded, label = "search_to_back") {
                        targetExpanded ->
                        if (targetExpanded) {
                            IconButton(onClick = { onExpand(false) }) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_arrow_left),
                                    contentDescription = null,
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_search),
                                contentDescription = null,
                            )
                        }
                    }
                },
                trailingIcon = {
                    if (state.loading) {
                        JellyxLoadingLogo(size = 28.dp)
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
        expanded = expanded,
        onExpandedChange = { onExpand(it) },
        modifier = modifier.padding(start = searchBarPaddingStart, end = searchBarPaddingEnd),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding =
                    PaddingValues(
                        start = safePadding.start + MaterialTheme.spacings.default,
                        top = MaterialTheme.spacings.small,
                        end = safePadding.end + MaterialTheme.spacings.default,
                        bottom = MaterialTheme.spacings.small,
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(DEFAULT_STREAMING_PLATFORM_SHORTCUTS, key = { it.id }) { platform ->
                    val isSelected =
                        state.activePlatformId == platform.id ||
                            query.equals(platform.searchQuery, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color =
                            if (isSelected) Color(platform.brandColorHex)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier =
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                if (query.equals(platform.searchQuery, ignoreCase = true)) {
                                    onAction(SearchAction.SelectPlatform(platform.id))
                                } else {
                                    query = platform.searchQuery
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(20.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(platform.brandColorHex)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = platform.shortBadge,
                                    color = Color.White,
                                    fontSize = if (platform.shortBadge.length > 2) 7.sp else 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = platform.label,
                                style = MaterialTheme.typography.labelLarge,
                                color =
                                    if (isSelected) Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            val activePlatform =
                DEFAULT_STREAMING_PLATFORM_SHORTCUTS.firstOrNull { it.id == state.activePlatformId }
            if (activePlatform != null && !state.loading) {
                Text(
                    text = "${activePlatform.label} · ${state.items.size} títulos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            start = safePadding.start + MaterialTheme.spacings.default,
                            end = safePadding.end + MaterialTheme.spacings.default,
                            bottom = MaterialTheme.spacings.extraSmall,
                        ),
                )
            }

            LazyVerticalGrid(
                columns = GridCellsAdaptiveWithMinColumns(minSize = 160.dp, minColumns = 2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding =
                    PaddingValues(
                        start = safePadding.start + MaterialTheme.spacings.default,
                        top = MaterialTheme.spacings.small,
                        end = safePadding.end + MaterialTheme.spacings.default,
                        bottom = safePadding.bottom + MaterialTheme.spacings.default,
                    ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        direction = Direction.VERTICAL,
                        ratings = state.ratingsByItemId[item.id] ?: item.toNativeRatings(),
                        showRatingsOverlay = true,
                        onClick = { onAction(SearchAction.OnItemClick(item)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
