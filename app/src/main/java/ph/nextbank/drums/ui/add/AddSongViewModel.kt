package ph.nextbank.drums.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.songsterr.SongsterrResult
import ph.nextbank.drums.audio.songsterr.SongsterrSearchService
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 400L

data class AddSongUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SongsterrResult> = emptyList(),
)

@HiltViewModel
class AddSongViewModel @Inject constructor(
    private val searchService: SongsterrSearchService,
) : ViewModel() {

    private val _state = MutableStateFlow(AddSongUiState())
    val state: StateFlow<AddSongUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.value = _state.value.copy(isSearching = false, results = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.value = _state.value.copy(isSearching = true)
            val res = searchService.search(q).take(10)
            _state.value = _state.value.copy(isSearching = false, results = res)
        }
    }

    /** Stub — wired up in Task 14. */
    fun onResultClicked(result: SongsterrResult) { /* TODO Task 14 */ }
}
