package dev.jdtech.jellyfin.film.presentation.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GenresState(
    val genres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val items: List<JellyxItem> = emptyList(),
    val isLoadingGenres: Boolean = true,
    val isLoadingItems: Boolean = false,
)

@HiltViewModel
class GenresViewModel @Inject constructor(
    private val repository: JellyfinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GenresState())
    val state = _state.asStateFlow()

    init {
        loadGenres()
    }

    fun loadGenres() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingGenres = true) }
            val genres = repository.getGenres()
            val selectedGenre = _state.value.selectedGenre
            _state.update {
                it.copy(
                    genres = genres,
                    selectedGenre = selectedGenre,
                    isLoadingGenres = false,
                )
            }
            if (selectedGenre != null) {
                loadItemsForGenre(selectedGenre)
            }
        }
    }

    fun ensureInitialGenreSelected() {
        if (_state.value.selectedGenre == null) {
            _state.value.genres.firstOrNull()?.let { selectGenre(it) }
        }
    }

    fun clearSelectedGenre() {
        _state.update { it.copy(selectedGenre = null, items = emptyList(), isLoadingItems = false) }
    }

    fun selectGenre(genre: String) {
        if (_state.value.selectedGenre == genre && _state.value.items.isNotEmpty()) return
        _state.update { it.copy(selectedGenre = genre) }
        loadItemsForGenre(genre)
    }

    private fun loadItemsForGenre(genre: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingItems = true) }
            val items = repository.getItemsByGenre(genre)
            _state.update { it.copy(items = items, isLoadingItems = false) }
        }
    }
}

