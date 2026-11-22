package dev.jdtech.jellyfin.film.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.models.JellyxSources
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.View
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.JellyfinRepositoryOfflineImpl
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.NetworkConnectivityMonitor
import dev.jdtech.jellyfin.utils.toView
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val repository: JellyfinRepository,
    private val offlineRepository: JellyfinRepositoryOfflineImpl,
    val appPreferences: AppPreferences,
    val database: ServerDatabaseDao,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val downloader: Downloader,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val uuidSuggestions = UUID.fromString("31e47044-9b79-4bb0-99d0-0e477ed65420")
    private val uuidContinueWatching =
        UUID(4937169328197226115, -4704919157662094443) // 44845958-8326-4e83-beb4-c4f42e9eeb95
    private val uuidFavorites = UUID.fromString("9e7c3f12-4b1a-4d8e-9a21-6c5f8d0e3b11")
    private val uuidNextUp =
        UUID(1783371395749072194, -6164625418200444295) // 18bfced5-f237-4d42-aa72-d9d7fed19279
    private val uuidOfflineShows = UUID.fromString("4d2e7c10-9f11-4f88-9b34-1234567890aa")
    private val uuidOfflineMovies = UUID.fromString("4d2e7c10-9f11-4f88-9b34-1234567890ab")

    private val uiTextContinueWatching = UiText.StringResource(FilmR.string.continue_watching)
    private val uiTextFavorites = UiText.StringResource(FilmR.string.favorites)
    private val uiTextNextUp = UiText.StringResource(FilmR.string.next_up)

    init {
        viewModelScope.launch {
            networkMonitor.isOnlineFlow.drop(1).collect {
                loadData()
            }
        }
    }

    fun loadData() {
        Timber.i("Loading data")
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { downloader.reconcileCompletedDownloads() }

            appPreferences.getValue(appPreferences.currentServer)?.let { serverId ->
                loadServerName(serverId)
            }

            val shouldUseOffline =
                appPreferences.getValue(appPreferences.offlineMode) || !networkMonitor.isOnline()
            if (shouldUseOffline) {
                loadOfflineHomeData()
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            var anyOnlineNetworkAttemptSucceeded = false
            var lastError: Exception? = null

            suspend fun runOnlineSection(block: suspend () -> Boolean) {
                try {
                    if (block()) anyOnlineNetworkAttemptSucceeded = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }

            runOnlineSection { loadSuggestions() }
            runOnlineSection { loadResumeItems() }
            runOnlineSection { loadFavoriteItems() }
            runOnlineSection { loadNextUpItems() }
            runOnlineSection { loadViews() }

            if (!anyOnlineNetworkAttemptSucceeded && lastError != null) {
                loadOfflineHomeData()
            } else {
                _state.update { it.copy(isOfflineDisplay = false) }
            }

            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadServerName(serverId: String) {
        val server = database.getServer(serverId)
        if (server != null) {
            _state.update { it.copy(server = server) }
        }
    }

    private suspend fun loadSuggestions(): Boolean {
        Timber.i("Loading suggestions")
        if (!appPreferences.getValue(appPreferences.homeSuggestions)) {
            _state.update { it.copy(suggestionsSection = null) }
            return false
        }

        val items = repository.getSuggestions()

        val section =
            if (items.isEmpty()) {
                null
            } else {
                HomeItem.Suggestions(id = uuidSuggestions, items = items)
            }

        _state.update { it.copy(suggestionsSection = section) }
        return true
    }

    private suspend fun loadResumeItems(): Boolean {
        Timber.i("Loading resume items")
        if (!appPreferences.getValue(appPreferences.homeContinueWatching)) {
            _state.update { it.copy(resumeSection = null) }
            return false
        }

        val resumeItems = repository.getResumeItems()

        val section =
            if (resumeItems.isEmpty()) {
                null
            } else {
                HomeItem.Section(
                    HomeSection(uuidContinueWatching, uiTextContinueWatching, resumeItems)
                )
            }

        _state.update { it.copy(resumeSection = section) }
        return true
    }

    private suspend fun loadFavoriteItems(): Boolean {
        Timber.i("Loading favorite items")
        return try {
            val favoriteItems = repository.getFavoriteItems()

            val section =
                if (favoriteItems.isEmpty()) {
                    null
                } else {
                    HomeItem.Section(
                        HomeSection(uuidFavorites, uiTextFavorites, favoriteItems)
                    )
                }

            _state.update { it.copy(favoritesSection = section) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to load favorite items")
            throw e
        }
    }

    private suspend fun loadNextUpItems(): Boolean {
        Timber.i("Loading next up items")
        if (!appPreferences.getValue(appPreferences.homeNextUp)) {
            _state.update { it.copy(nextUpSection = null) }
            return false
        }

        val nextUpItems = repository.getNextUp()

        val section =
            if (nextUpItems.isEmpty()) {
                null
            } else {
                HomeItem.Section(HomeSection(uuidNextUp, uiTextNextUp, nextUpItems))
            }

        _state.update { it.copy(nextUpSection = section) }
        return true
    }

    private suspend fun loadViews(): Boolean {
        Timber.i("Loading views")
        if (!appPreferences.getValue(appPreferences.homeLatest)) {
            _state.update { it.copy(views = emptyList()) }
            return false
        }

        val items =
            repository
                .getUserViews()
                .filter { view ->
                    CollectionType.fromString(view.collectionType?.serialName) in
                        CollectionType.supported
                }
                .map { view ->
                    val latest =
                        if (
                            CollectionType.fromString(view.collectionType?.serialName) ==
                                CollectionType.LiveTv
                        ) {
                            repository.getLiveTvChannels().take(16)
                        } else {
                            repository.getLatestMedia(view.id)
                        }
                    view to latest
                }
                .filter { (_, latest) -> latest.isNotEmpty() }
                .map { (view, latest) -> view.toView(latest) }
                .map { HomeItem.ViewItem(it) }

        _state.update { it.copy(views = items) }
        return true
    }

    private suspend fun loadOfflineHomeData() =
        withContext(Dispatchers.IO) {
            val downloads = offlineRepository.getDownloads()
            val downloadedShowIds =
                downloads
                    .filterIsInstance<JellyxShow>()
                    .filter { show ->
                        database.getEpisodesByShowId(show.id).any { ep ->
                            database.getSources(ep.id).any { src ->
                                !src.path.endsWith(".download") && File(src.path).length() > 0L
                            }
                        }
                    }
                    .map { it.id }
                    .toSet()

            val resumeItems =
                offlineRepository.getResumeItems().filter { it !is JellyxSources || it.isDownloaded() }
            val favoriteItems =
                offlineRepository.getFavoriteItems().filter { item ->
                    when (item) {
                        is JellyxEpisode,
                        is JellyxMovie -> item.isDownloaded()
                        is JellyxShow -> item.id in downloadedShowIds
                        else -> false
                    }
                }
            val nextUpItems = offlineRepository.getNextUp().filter { it.isDownloaded() }

            val downloadedShows =
                downloads.filterIsInstance<JellyxShow>().filter { it.id in downloadedShowIds }
            val downloadedMovies =
                downloads.filterIsInstance<JellyxMovie>().filter { it.isDownloaded() }

        val offlineViews = buildList {
            if (downloadedShows.isNotEmpty()) {
                add(
                    HomeItem.ViewItem(
                        View(
                            id = uuidOfflineShows,
                            name = context.getString(FilmR.string.downloaded_shows),
                            items = downloadedShows,
                            type = CollectionType.TvShows,
                        )
                    )
                )
            }
            if (downloadedMovies.isNotEmpty()) {
                add(
                    HomeItem.ViewItem(
                        View(
                            id = uuidOfflineMovies,
                            name = context.getString(FilmR.string.downloaded_movies),
                            items = downloadedMovies,
                            type = CollectionType.Movies,
                        )
                    )
                )
            }
        }

            _state.update {
                it.copy(
                    suggestionsSection = null,
                    resumeSection =
                        if (resumeItems.isEmpty()) {
                            null
                        } else {
                            HomeItem.Section(
                                HomeSection(uuidContinueWatching, uiTextContinueWatching, resumeItems)
                            )
                        },
                    favoritesSection =
                        if (favoriteItems.isEmpty()) {
                            null
                        } else {
                            HomeItem.Section(
                                HomeSection(uuidFavorites, uiTextFavorites, favoriteItems)
                            )
                        },
                    nextUpSection =
                        if (nextUpItems.isEmpty()) {
                            null
                        } else {
                            HomeItem.Section(HomeSection(uuidNextUp, uiTextNextUp, nextUpItems))
                        },
                    views = offlineViews,
                    isOfflineDisplay = true,
                    error = null,
                )
            }
        }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OnRetryClick -> {
                loadData()
            }
            else -> Unit
        }
    }
}
