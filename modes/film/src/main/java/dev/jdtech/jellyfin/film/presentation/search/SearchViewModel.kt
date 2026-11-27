package dev.jdtech.jellyfin.film.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxRatings
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.models.StreamingPlatformCatalog
import dev.jdtech.jellyfin.models.toNativeRatings
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.RatingsRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber

@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val ratingsRepository: RatingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    var currentJob: Job? = null
    private var ratingsJob: Job? = null

    private fun cancelActiveJobs() {
        currentJob?.cancel()
        ratingsJob?.cancel()
    }

    private fun search(query: String) {
        cancelActiveJobs()
        currentJob = viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    _state.value = SearchState()
                    return@launch
                }

                val matchedPlatform = StreamingPlatformCatalog.findByQuery(query)
                _state.update {
                    it.copy(loading = true, activePlatformId = matchedPlatform?.id)
                }

                val items =
                    if (matchedPlatform != null) {
                        repository.getItemsByStreamingPlatform(matchedPlatform.id)
                    } else {
                        repository.getSearchItems(query)
                    }
                val nativeRatings = items.associate { it.id to it.toNativeRatings() }

                _state.value =
                    SearchState(
                        items = items,
                        loading = false,
                        activePlatformId = matchedPlatform?.id,
                        ratingsByItemId = nativeRatings,
                    )
                launchRatingsEnrichment(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun selectPlatform(platformId: String) {
        cancelActiveJobs()
        currentJob = viewModelScope.launch {
            try {
                _state.update { it.copy(loading = true, activePlatformId = platformId) }
                val items = repository.getItemsByStreamingPlatform(platformId)
                val nativeRatings = items.associate { it.id to it.toNativeRatings() }

                _state.value =
                    SearchState(
                        items = items,
                        loading = false,
                        activePlatformId = platformId,
                        ratingsByItemId = nativeRatings,
                    )
                launchRatingsEnrichment(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun launchRatingsEnrichment(items: List<JellyxItem>) {
        if (items.isEmpty()) return
        ratingsJob?.cancel()
        ratingsJob = viewModelScope.launch {
            try {
                enrichRatingsAsync(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to enrich search ratings")
            }
        }
    }

    private suspend fun enrichRatingsAsync(items: List<JellyxItem>) {
        val semaphore = Semaphore(3)
        val bufferMutex = Mutex()
        val pendingUpdates = mutableMapOf<UUID, JellyxRatings>()

        fun flushUpdates(batch: Map<UUID, JellyxRatings>) {
            if (batch.isEmpty()) return
            _state.update { current ->
                var changed = false
                for ((id, ratings) in batch) {
                    if (current.ratingsByItemId[id] != ratings) {
                        changed = true
                        break
                    }
                }
                if (!changed) {
                    current
                } else {
                    current.copy(ratingsByItemId = current.ratingsByItemId + batch)
                }
            }
        }

        coroutineScope {
            items.take(60).forEach { item ->
                launch {
                    semaphore.withPermit {
                        val nativeCommunity =
                            when (item) {
                                is JellyxMovie -> item.communityRating
                                is JellyxShow -> item.communityRating
                                is JellyxEpisode -> item.communityRating
                                else -> null
                            }
                        val enriched =
                            runCatching {
                                    ratingsRepository.getEnrichedRatings(
                                        itemId = item.id,
                                        communityRating = nativeCommunity,
                                        criticRating = item.criticRating,
                                        providerIds = item.providerIds,
                                        isSeries = item is JellyxShow,
                                    )
                                }
                                .getOrNull() ?: return@withPermit

                        val batchToFlush =
                            bufferMutex.withLock {
                                pendingUpdates[item.id] = enriched
                                if (pendingUpdates.size >= RATINGS_BATCH_SIZE) {
                                    pendingUpdates.toMap().also { pendingUpdates.clear() }
                                } else {
                                    null
                                }
                            }
                        if (batchToFlush != null) {
                            flushUpdates(batchToFlush)
                        }
                    }
                }
            }
        }

        val remaining =
            bufferMutex.withLock {
                pendingUpdates.toMap().also { pendingUpdates.clear() }
            }
        flushUpdates(remaining)
    }

    companion object {
        private const val RATINGS_BATCH_SIZE = 4
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.Search -> search(query = action.query)
            is SearchAction.SelectPlatform -> selectPlatform(platformId = action.platformId)
            else -> Unit
        }
    }
}
