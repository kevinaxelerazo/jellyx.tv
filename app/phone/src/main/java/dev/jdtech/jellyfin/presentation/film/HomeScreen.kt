package dev.jdtech.jellyfin.presentation.film

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSuggestions
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.core.presentation.dummy.dummyServer
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.film.presentation.home.HomeViewModel
import dev.jdtech.jellyfin.film.presentation.search.DEFAULT_STREAMING_PLATFORM_SHORTCUTS
import dev.jdtech.jellyfin.models.JellyxCollection
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.HomeCarousel
import dev.jdtech.jellyfin.presentation.film.components.HomeHeader
import dev.jdtech.jellyfin.presentation.film.components.HomeSection
import dev.jdtech.jellyfin.presentation.film.components.HomeView
import dev.jdtech.jellyfin.presentation.film.components.ServerSelectionBottomSheet
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onLibraryClick: (library: JellyxCollection) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageServers: () -> Unit,
    onItemClick: (item: JellyxItem) -> Unit,
    onPlatformClick: (query: String) -> Unit = {},
    showFloatingPlatformsBar: Boolean = true,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasResumedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(true) { viewModel.loadData() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasResumedOnce) {
                    viewModel.loadData()
                } else {
                    hasResumedOnce = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeScreenLayout(
        state = state,
        onPlatformClick = onPlatformClick,
        showFloatingPlatformsBar = showFloatingPlatformsBar,
        onAction = { action ->
            when (action) {
                is HomeAction.OnItemClick -> onItemClick(action.item)
                is HomeAction.OnLibraryClick -> onLibraryClick(action.library)
                is HomeAction.OnSearchClick -> onSearchClick()
                is HomeAction.OnSettingsClick -> onSettingsClick()
                is HomeAction.OnManageServers -> onManageServers()
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenLayout(
    state: HomeState,
    onPlatformClick: (query: String) -> Unit = {},
    showFloatingPlatformsBar: Boolean = true,
    onAction: (HomeAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val safePadding = rememberSafePadding(handleStartInsets = false)
    val isOfflineMode = LocalOfflineMode.current
    val listState = rememberLazyListState()
    var showFloatingPlatforms by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                if (index == 0 && offset <= 24) {
                    showFloatingPlatforms = true
                } else {
                    val scrollingDown =
                        index > prevIndex || (index == prevIndex && offset > prevOffset + 12)
                    val scrollingUp =
                        index < prevIndex || (index == prevIndex && offset < prevOffset - 12)
                    if (scrollingDown) showFloatingPlatforms = false
                    else if (scrollingUp) showFloatingPlatforms = true
                }
                prevIndex = index
                prevOffset = offset
            }
    }

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingTop = safePadding.top + MaterialTheme.spacings.small
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val itemsPadding = PaddingValues(start = paddingStart, end = paddingEnd)

    val contentPaddingTop = safePadding.top + 88.dp

    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    val showServerSelectionSheetState = rememberModalBottomSheetState()
    var showServerSelectionBottomSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        PullToRefreshBox(isRefreshing = false, onRefresh = { onAction(HomeAction.OnRetryClick) }) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().semantics { traversalIndex = 1f },
                contentPadding = PaddingValues(top = contentPaddingTop, bottom = paddingBottom),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            ) {
                if (state.isOfflineDisplay) {
                    item(key = "offline_banner") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth().padding(itemsPadding),
                        ) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(FilmR.string.offline_mode_banner),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onAction(HomeAction.OnRetryClick) }) {
                                    Text(text = stringResource(FilmR.string.retry))
                                }
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
                            Column(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(itemsPadding)
                                        .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(FilmR.string.offline_empty_downloads),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Button(onClick = { onAction(HomeAction.OnRetryClick) }) {
                                    Text(text = stringResource(FilmR.string.retry))
                                }
                            }
                        }
                    }
                }
                state.suggestionsSection?.let { section ->
                    item(key = section.id) {
                        HomeCarousel(
                            items = section.items,
                            itemsPadding = itemsPadding,
                            onAction = onAction,
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

        if (state.error != null && showErrorDialog) {
            ErrorDialog(exception = state.error!!, onDismissRequest = { showErrorDialog = false })
        }

        if (!isOfflineMode && !state.isOfflineDisplay && showFloatingPlatformsBar) {
            AnimatedVisibility(
                visible = showFloatingPlatforms,
                enter = fadeIn() + slideInHorizontally { -it },
                exit = fadeOut() + slideOutHorizontally { -it },
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .padding(start = safePadding.start),
            ) {
                val edgeShape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                Column(
                    modifier =
                        Modifier.clip(edgeShape)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                edgeShape,
                            )
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DEFAULT_STREAMING_PLATFORM_SHORTCUTS.forEach { platform ->
                        Box(
                            modifier =
                                Modifier.size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(platform.brandColorHex))
                                    .clickable { onPlatformClick(platform.searchQuery) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = platform.shortBadge,
                                color = Color.White,
                                fontSize = if (platform.shortBadge.length > 2) 8.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    HomeHeader(
        serverName = state.server?.name ?: "",
        isLoading = state.isLoading,
        isError = state.error != null,
        onServerClick = { showServerSelectionBottomSheet = true },
        onErrorClick = { showErrorDialog = true },
        onRetryClick = { onAction(HomeAction.OnRetryClick) },
        onSearchClick = { onAction(HomeAction.OnSearchClick) },
        onUserClick = { onAction(HomeAction.OnSettingsClick) },
        modifier = Modifier.padding(start = paddingStart, top = paddingTop, end = paddingEnd),
    )

    if (showServerSelectionBottomSheet) {
        ServerSelectionBottomSheet(
            currentServerId = state.server?.id ?: "",
            onUpdate = {
                onAction(HomeAction.OnRetryClick)
                scope
                    .launch { showServerSelectionSheetState.hide() }
                    .invokeOnCompletion {
                        if (!showServerSelectionSheetState.isVisible) {
                            showServerSelectionBottomSheet = false
                        }
                    }
            },
            onManage = {
                onAction(HomeAction.OnManageServers)
                scope.launch { showServerSelectionSheetState.hide() }
            },
            onDismissRequest = { showServerSelectionBottomSheet = false },
            sheetState = showServerSelectionSheetState,
        )
    }
}

@PreviewScreenSizes
@Composable
private fun HomeScreenLayoutPreview() {
    JellyxTheme {
        HomeScreenLayout(
            state =
                HomeState(
                    server = dummyServer,
                    suggestionsSection = dummyHomeSuggestions,
                    resumeSection = dummyHomeSection,
                    views = listOf(dummyHomeView),
                    error = Exception("Failed to load data"),
                ),
            onAction = {},
        )
    }
}
