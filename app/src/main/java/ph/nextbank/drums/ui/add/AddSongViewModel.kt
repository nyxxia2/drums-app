package ph.nextbank.drums.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.songsterr.DrumTabParser
import ph.nextbank.drums.audio.songsterr.FetchResult
import ph.nextbank.drums.audio.songsterr.ParseResult
import ph.nextbank.drums.audio.songsterr.SongsterrResult
import ph.nextbank.drums.audio.songsterr.SongsterrSearchService
import ph.nextbank.drums.audio.songsterr.SongsterrTabFetcher
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import java.util.UUID
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 400L

data class AddSongUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isAdding: Boolean = false,
    val results: List<SongsterrResult> = emptyList(),
)

sealed interface AddSongEvent {
    data class Toast(val message: String) : AddSongEvent
    data class SongAdded(val songId: String) : AddSongEvent
}

@HiltViewModel
class AddSongViewModel @Inject constructor(
    private val searchService: SongsterrSearchService,
    private val tabFetcher: SongsterrTabFetcher,
    private val parser: DrumTabParser,
    private val repo: SongRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddSongUiState())
    val state: StateFlow<AddSongUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AddSongEvent>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<AddSongEvent> = _events.asSharedFlow()

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

    fun onResultClicked(result: SongsterrResult) {
        if (_state.value.isAdding) return
        _state.value = _state.value.copy(isAdding = true)
        viewModelScope.launch {
            runCatching {
                val fetched = tabFetcher.fetchDrumTrack(result.songId)
                handleFetchResult(result, fetched)
            }.getOrElse {
                _events.tryEmit(AddSongEvent.Toast("Couldn't load tab — try a different result"))
            }
            _state.value = _state.value.copy(isAdding = false)
        }
    }

    private suspend fun handleFetchResult(result: SongsterrResult, fetched: FetchResult) {
        when (fetched) {
            is FetchResult.Success -> {
                when (val parsed = parser.parse(fetched.data)) {
                    is ParseResult.Success -> persistSong(result, fetched.data.revisionId, parsed)
                    is ParseResult.NoDrumTrack ->
                        _events.tryEmit(AddSongEvent.Toast("This song doesn't have a drum tab on Songsterr."))
                    is ParseResult.ParseError ->
                        _events.tryEmit(AddSongEvent.Toast("Couldn't read the tab data."))
                }
            }
            FetchResult.NoDrumTrack ->
                _events.tryEmit(AddSongEvent.Toast("This song doesn't have a drum tab on Songsterr."))
            is FetchResult.ScrapeFailure ->
                _events.tryEmit(AddSongEvent.Toast("Couldn't load tab from Songsterr — try a different result"))
            FetchResult.NetworkError ->
                _events.tryEmit(AddSongEvent.Toast("Check your connection."))
        }
    }

    private suspend fun persistSong(
        result: SongsterrResult,
        revisionId: Long,
        parsed: ParseResult.Success,
    ) {
        val coverInitials = (result.artist.take(1) + result.title.take(1)).uppercase().ifEmpty { "??" }
        val song = Song(
            id = UUID.randomUUID().toString(),
            title = result.title,
            artist = result.artist,
            bpm = parsed.bpm,
            timeSig = parsed.timeSig,
            bars = parsed.bars,
            coverInitials = coverInitials,
            importedFrom = ImportSource.BUNDLED, // semantics: app-added; Phase 4 can introduce a SONGSTERR enum value if needed
            lastPlayed = null,
            youtubeVideoId = null,
            songsterrId = result.songId,
            songsterrRevisionId = revisionId.toString(),
        )
        repo.upsertAll(listOf(song))
        _events.tryEmit(AddSongEvent.SongAdded(song.id))
    }
}
