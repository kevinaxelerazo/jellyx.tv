package dev.jdtech.jellyfin.film.presentation.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxProgram
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

data class LiveTvCategory(
    val id: String,
    val label: String,
    val count: Int,
)

data class LiveTvState(
    val allChannels: List<JellyxChannel> = emptyList(),
    val filteredChannels: List<JellyxChannel> = emptyList(),
    val categories: List<LiveTvCategory> = emptyList(),
    val selectedCategoryId: String = LiveTvViewModel.CATEGORY_ALL,
    val selectedChannel: JellyxChannel? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: Throwable? = null,
    val currentTimeEpochSec: Long = System.currentTimeMillis() / 1000L,
)

@HiltViewModel
class LiveTvViewModel
@Inject
constructor(private val repository: JellyfinRepository) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvState())
    val state = _state.asStateFlow()

    init {
        startEpgTicker()
    }

    private fun startEpgTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                _state.update { it.copy(currentTimeEpochSec = System.currentTimeMillis() / 1000L) }
            }
        }
    }

    fun loadChannels(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            try {
                val channels = repository.getLiveTvChannels()
                _state.update { current ->
                    val categories = buildCategories(channels)
                    val activeCategory =
                        if (categories.any { it.id == current.selectedCategoryId }) {
                            current.selectedCategoryId
                        } else {
                            CATEGORY_ALL
                        }
                    val filtered = filterChannels(channels, activeCategory, current.searchQuery)
                    val selected =
                        current.selectedChannel?.let { prev ->
                            filtered.firstOrNull { it.id == prev.id }
                        } ?: filtered.firstOrNull()
                    current.copy(
                        allChannels = channels,
                        filteredChannels = filtered,
                        categories = categories,
                        selectedCategoryId = activeCategory,
                        selectedChannel = selected,
                        isLoading = false,
                        error = null,
                        currentTimeEpochSec = System.currentTimeMillis() / 1000L,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load Live TV channels")
                _state.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    fun selectCategory(categoryId: String) {
        val current = _state.value
        if (current.selectedCategoryId == categoryId) return
        val filtered = filterChannels(current.allChannels, categoryId, current.searchQuery)
        val selected =
            current.selectedChannel?.let { prev -> filtered.firstOrNull { it.id == prev.id } }
                ?: filtered.firstOrNull()
        _state.update {
            it.copy(
                selectedCategoryId = categoryId,
                filteredChannels = filtered,
                selectedChannel = selected,
            )
        }
    }

    fun selectChannel(channel: JellyxChannel) {
        if (_state.value.selectedChannel?.id == channel.id) return
        _state.update { it.copy(selectedChannel = channel) }
    }

    fun updateSearchQuery(query: String) {
        val current = _state.value
        if (current.searchQuery == query) return
        val filtered = filterChannels(current.allChannels, current.selectedCategoryId, query)
        val selected =
            current.selectedChannel?.let { prev -> filtered.firstOrNull { it.id == prev.id } }
                ?: filtered.firstOrNull()
        _state.update {
            it.copy(
                searchQuery = query,
                filteredChannels = filtered,
                selectedChannel = selected,
            )
        }
    }

    fun toggleFavorite(channel: JellyxChannel) {
        val newFavorite = !channel.favorite
        _state.update { current ->
            val updatedAll =
                current.allChannels.map { item ->
                    if (item.id == channel.id) item.copy(favorite = newFavorite) else item
                }
            val updatedCategories = buildCategories(updatedAll)
            val updatedFiltered =
                filterChannels(updatedAll, current.selectedCategoryId, current.searchQuery)
            val updatedSelected =
                current.selectedChannel?.let { selected ->
                    if (selected.id == channel.id) selected.copy(favorite = newFavorite)
                    else updatedFiltered.firstOrNull { it.id == selected.id }
                }
            current.copy(
                allChannels = updatedAll,
                filteredChannels = updatedFiltered,
                categories = updatedCategories,
                selectedChannel = updatedSelected,
            )
        }

        viewModelScope.launch {
            runCatching {
                    if (newFavorite) {
                        repository.markAsFavorite(channel.id)
                    } else {
                        repository.unmarkAsFavorite(channel.id)
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to update favorite state for channel %s", channel.id)
                    _state.update { current ->
                        val revertedAll =
                            current.allChannels.map { item ->
                                if (item.id == channel.id) item.copy(favorite = !newFavorite)
                                else item
                            }
                        val revertedCategories = buildCategories(revertedAll)
                        val revertedFiltered =
                            filterChannels(
                                revertedAll,
                                current.selectedCategoryId,
                                current.searchQuery,
                            )
                        val revertedSelected =
                            current.selectedChannel?.let { selected ->
                                if (selected.id == channel.id)
                                    selected.copy(favorite = !newFavorite)
                                else revertedFiltered.firstOrNull { it.id == selected.id }
                            }
                        current.copy(
                            allChannels = revertedAll,
                            filteredChannels = revertedFiltered,
                            categories = revertedCategories,
                            selectedChannel = revertedSelected,
                        )
                    }
                }
        }
    }

    private fun buildCategories(channels: List<JellyxChannel>): List<LiveTvCategory> {
        val groups =
            channels
                .groupBy { it.group.ifBlank { DEFAULT_GROUP } }
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, List<JellyxChannel>>> { it.value.size }
                        .thenBy { it.key.lowercase() }
                )
                .map { (groupName, groupChannels) ->
                    LiveTvCategory(id = groupName, label = groupName, count = groupChannels.size)
                }

        return buildList {
            add(LiveTvCategory(id = CATEGORY_ALL, label = "📡 Todos", count = channels.size))
            add(
                LiveTvCategory(
                    id = CATEGORY_FAVORITES,
                    label = "⭐ Favoritos",
                    count = channels.count { it.favorite },
                )
            )
            addAll(groups)
        }
    }

    private fun filterChannels(
        channels: List<JellyxChannel>,
        categoryId: String,
        query: String,
    ): List<JellyxChannel> {
        val categoryFiltered =
            when (categoryId) {
                CATEGORY_ALL -> channels
                CATEGORY_FAVORITES -> channels.filter { it.favorite }
                else -> channels.filter { it.group == categoryId }
            }

        val cleanQuery = query.trim().lowercase()
        val filtered =
            if (cleanQuery.isEmpty()) {
                categoryFiltered
            } else {
                categoryFiltered.filter { channel ->
                    channel.displayName.lowercase().contains(cleanQuery) ||
                        channel.name.lowercase().contains(cleanQuery) ||
                        channel.channelNumber?.lowercase()?.contains(cleanQuery) == true ||
                        channel.currentProgram?.name?.lowercase()?.contains(cleanQuery) == true ||
                        channel.group.lowercase().contains(cleanQuery)
                }
            }

        return filtered.sortedWith(
            compareByDescending<JellyxChannel> { it.favorite }
                .thenBy { it.channelNumber?.toDoubleOrNull() ?: Double.MAX_VALUE }
                .thenBy { it.displayName.lowercase() }
        )
    }

    companion object {
        const val CATEGORY_ALL = "ALL"
        const val CATEGORY_FAVORITES = "FAV"
        private const val DEFAULT_GROUP = "📡 General"
    }
}

fun computeProgramProgress(
    start: LocalDateTime?,
    end: LocalDateTime?,
    now: LocalDateTime = LocalDateTime.now(),
): Float? {
    if (start == null || end == null) return null
    val totalSeconds = Duration.between(start, end).seconds
    if (totalSeconds <= 0L) return null
    val elapsedSeconds = Duration.between(start, now).seconds
    return (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
}

fun formatDurationMinutes(totalMinutes: Long): String {
    val safeMin = totalMinutes.coerceAtLeast(0L)
    if (safeMin >= 60L) {
        val hours = safeMin / 60L
        val mins = safeMin % 60L
        return if (mins == 0L) "${hours}h" else "${hours}h ${mins}m"
    }
    return "$safeMin min"
}

fun formatLiveEpgSummary(
    start: LocalDateTime?,
    end: LocalDateTime?,
    now: LocalDateTime = LocalDateTime.now(),
    timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm"),
): String? {
    if (start != null && end != null) {
        val totalMin = Duration.between(start, end).toMinutes().coerceAtLeast(1L)
        val remainingMin = Duration.between(now, end).toMinutes().coerceAtLeast(0L)
        return "Duración: ${formatDurationMinutes(totalMin)} · Termina aprox. ${end.format(timeFormatter)} · Faltan ${formatDurationMinutes(remainingMin)}"
    }
    if (end != null) {
        val remainingMin = Duration.between(now, end).toMinutes().coerceAtLeast(0L)
        return "Termina aprox. ${end.format(timeFormatter)} · Faltan ${formatDurationMinutes(remainingMin)}"
    }
    return null
}

fun formatNextProgramLabel(
    nextProgram: JellyxProgram?,
    timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm"),
): String? {
    if (nextProgram == null || nextProgram.name.isBlank()) return null
    val startPrefix = nextProgram.startDate?.format(timeFormatter)?.let { "$it — " } ?: ""
    return "$startPrefix${nextProgram.name}"
}

