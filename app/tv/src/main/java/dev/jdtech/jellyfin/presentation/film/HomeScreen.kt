package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSuggestions
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.film.presentation.home.HomeViewModel
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.presentation.film.components.HomeCarousel
import dev.jdtech.jellyfin.presentation.film.components.HomeSection
import dev.jdtech.jellyfin.presentation.film.components.HomeView
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.tv.ContinueWatchingChannelManager
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun HomeScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToPlayer: (itemId: UUID, itemKind: BaseItemKind) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    isLoading: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var skipFirstResume by remember { mutableStateOf(true) }

    LaunchedEffect(true) { viewModel.loadData() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipFirstResume) {
                    skipFirstResume = false
                } else {
                    viewModel.loadData()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val context = LocalContext.current

    LaunchedEffect(state.isLoading) { isLoading(state.isLoading) }

    LaunchedEffect(state.resumeSection) {
        val resumeItems = state.resumeSection?.homeSection?.items.orEmpty()
        if (resumeItems.isNotEmpty()) {
            ContinueWatchingChannelManager.syncContinueWatching(context, resumeItems)
        }
    }

    HomeScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is HomeAction.OnItemClick -> {
                    when (action.item) {
                        is JellyxMovie -> navigateToMovie(action.item.id)
                        is JellyxShow -> navigateToShow(action.item.id)
                        is JellyxEpisode -> {
                            navigateToPlayer(action.item.id, BaseItemKind.EPISODE)
                        }
                        is JellyxChannel -> {
                            navigateToPlayer(action.item.id, BaseItemKind.TV_CHANNEL)
                        }
                    }
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun HomeScreenLayout(state: HomeState, onAction: (HomeAction) -> Unit) {
    val itemsPadding = PaddingValues(horizontal = MaterialTheme.spacings.large)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = MaterialTheme.spacings.extraSmall,
                    bottom = MaterialTheme.spacings.large,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.large),
        ) {
            if (state.isOfflineDisplay) {
                item(key = "offline_banner") {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(itemsPadding)
                                .background(Color(0xFF1A2232), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(FilmR.string.offline_mode_banner),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = { onAction(HomeAction.OnRetryClick) }) {
                            Text(text = stringResource(FilmR.string.retry))
                        }
                    }
                }
                if (
                    state.resumeSection == null &&
                        state.favoritesSection == null &&
                        state.nextUpSection == null &&
                        state.views.isEmpty() &&
                        !state.isLoading
                ) {
                    item(key = "offline_empty") {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(itemsPadding)
                                    .padding(vertical = MaterialTheme.spacings.large),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.medium),
                            ) {
                                Text(
                                    text = stringResource(FilmR.string.offline_empty_downloads),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Button(onClick = { onAction(HomeAction.OnRetryClick) }) {
                                    Text(text = stringResource(FilmR.string.retry))
                                }
                            }
                        }
                    }
                }
            }
            state.suggestionsSection?.let { section ->
                item(key = section.id) {
                    HomeCarousel(
                        items = section.items,
                        onAction = onAction,
                        modifier = Modifier.animateItem().padding(itemsPadding),
                    )
                }
            }
            state.resumeSection?.let { section ->
                item(key = section.id) {
                    HomeSection(
                        section = section.homeSection,
                        itemsPadding = itemsPadding,
                        onAction = onAction,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            state.favoritesSection?.let { section ->
                item(key = section.id) {
                    HomeSection(
                        section = section.homeSection,
                        itemsPadding = itemsPadding,
                        onAction = onAction,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            state.nextUpSection?.let { section ->
                item(key = section.id) {
                    HomeSection(
                        section = section.homeSection,
                        itemsPadding = itemsPadding,
                        onAction = onAction,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            items(state.views, key = { it.id }) { view ->
                HomeView(
                    view = view,
                    itemsPadding = itemsPadding,
                    onAction = onAction,
                    modifier = Modifier.animateItem(),
                    isOfflineSection = state.isOfflineDisplay,
                )
            }
        }
        if (
            state.isLoading &&
                state.suggestionsSection == null &&
                state.resumeSection == null &&
                state.favoritesSection == null &&
                state.nextUpSection == null &&
                state.views.isEmpty()
        ) {
            JellyxLoadingLogo(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HomeScreenLayoutPreview() {
    JellyxTheme {
        HomeScreenLayout(
            state =
                HomeState(
                    suggestionsSection = dummyHomeSuggestions,
                    resumeSection = dummyHomeSection,
                    views = listOf(dummyHomeView),
                ),
            onAction = {},
        )
    }
}
