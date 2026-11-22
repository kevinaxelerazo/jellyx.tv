package dev.jdtech.jellyfin.film.presentation.show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItemPerson
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.Downloader
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind

@HiltViewModel
class ShowViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
) : ViewModel() {
    private val _state = MutableStateFlow(ShowState())
    val state = _state.asStateFlow()

    lateinit var showId: UUID

    fun loadShow(showId: UUID) {
        this.showId = showId
        viewModelScope.launch {
            try {
                runCatching { downloader.reconcileCompletedDownloads() }
                val show = repository.getShow(showId)
                val nextUp = getNextUp(showId)
                val seasons = repository.getSeasons(showId)
                val downloadedCountBySeason =
                    withContext(Dispatchers.IO) {
                        database
                            .getEpisodesByShowId(showId)
                            .filter { ep ->
                                database.getSources(ep.id).any { src ->
                                    !src.path.endsWith(".download") && File(src.path).length() > 0L
                                }
                            }
                            .groupingBy { it.seasonId }
                            .eachCount()
                    }
                val actors = getActors(show)
                val director = getDirector(show)
                val writers = getWriters(show)
                _state.emit(
                    _state.value.copy(
                        show = show,
                        nextUp = nextUp,
                        seasons = seasons,
                        downloadedCountBySeason = downloadedCountBySeason,
                        actors = actors,
                        director = director,
                        writers = writers,
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private suspend fun getNextUp(showId: UUID): JellyxEpisode? {
        val nextUpItems = repository.getNextUp(showId)
        return nextUpItems.getOrNull(0)
    }

    private suspend fun getActors(item: JellyxShow): List<JellyxItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people
                .filter { it.type == PersonKind.ACTOR }
                // Jellyfin 12+ returns a person once per role, keep one card per role
                .distinctBy { it.id to it.role }
        }
    }

    private suspend fun getDirector(item: JellyxShow): JellyxItemPerson? {
        return withContext(Dispatchers.Default) {
            item.people.firstOrNull { it.type == PersonKind.DIRECTOR }
        }
    }

    private suspend fun getWriters(item: JellyxShow): List<JellyxItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.WRITER }.distinctBy { it.id }
        }
    }

    fun onAction(action: ShowAction) {
        when (action) {
            is ShowAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(showId)
                    loadShow(showId)
                }
            }
            else -> Unit
        }
    }
}
