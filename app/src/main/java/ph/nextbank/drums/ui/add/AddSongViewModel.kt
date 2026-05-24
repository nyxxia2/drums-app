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
import ph.nextbank.drums.audio.songsterr.SongsterrVideoPointsService
import ph.nextbank.drums.audio.songsterr.VideoPointEntry
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.audio.youtube.YouTubeCandidateResolver
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import java.util.UUID
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 400L

data class PendingConfirm(
    val songTemplate: Song,
    val candidate: SearchResult,
)

data class AddSongUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isAdding: Boolean = false,
    val results: List<SongsterrResult> = emptyList(),
    val pendingConfirm: PendingConfirm? = null,
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
    private val pointsService: SongsterrVideoPointsService,
    private val resolver: YouTubeCandidateResolver,
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
                val points = when (fetched) {
                    is FetchResult.Success -> pointsService.fetch(result.songId, fetched.data.revisionId)
                    else -> emptyList()
                }
                handleFetchResult(result, fetched, points)
            }.getOrElse {
                _events.tryEmit(AddSongEvent.Toast("Couldn't load tab — try a different result"))
                _state.value = _state.value.copy(isAdding = false)
            }
        }
    }

    fun confirmPendingAdd() {
        val pending = _state.value.pendingConfirm ?: return
        val song = pending.songTemplate.copy(youtubeVideoId = pending.candidate.videoId)
        viewModelScope.launch {
            repo.upsertAll(listOf(song))
            _state.value = _state.value.copy(pendingConfirm = null, isAdding = false)
            _events.tryEmit(AddSongEvent.SongAdded(song.id))
        }
    }

    fun dismissPendingAdd() {
        _state.value = _state.value.copy(pendingConfirm = null, isAdding = false)
    }

    private suspend fun handleFetchResult(
        result: SongsterrResult,
        fetched: FetchResult,
        points: List<VideoPointEntry>,
    ) {
        when (fetched) {
            is FetchResult.Success -> {
                when (val parsed = parser.parse(fetched.data)) {
                    is ParseResult.Success -> stagePendingAdd(result, fetched.data.revisionId, parsed, points)
                    is ParseResult.NoDrumTrack -> {
                        _events.tryEmit(AddSongEvent.Toast("This song doesn't have a drum tab on Songsterr."))
                        _state.value = _state.value.copy(isAdding = false)
                    }
                    is ParseResult.ParseError -> {
                        _events.tryEmit(AddSongEvent.Toast("Couldn't read the tab data."))
                        _state.value = _state.value.copy(isAdding = false)
                    }
                }
            }
            FetchResult.NoDrumTrack -> {
                _events.tryEmit(AddSongEvent.Toast("This song doesn't have a drum tab on Songsterr."))
                _state.value = _state.value.copy(isAdding = false)
            }
            is FetchResult.ScrapeFailure -> {
                _events.tryEmit(AddSongEvent.Toast("Couldn't load tab from Songsterr — try a different result"))
                _state.value = _state.value.copy(isAdding = false)
            }
            FetchResult.NetworkError -> {
                _events.tryEmit(AddSongEvent.Toast("Check your connection."))
                _state.value = _state.value.copy(isAdding = false)
            }
        }
    }

    private suspend fun stagePendingAdd(
        result: SongsterrResult,
        revisionId: Long,
        parsed: ParseResult.Success,
        points: List<VideoPointEntry>,
    ) {
        val videoPoints = points.takeIf { it.isNotEmpty() }
        val candidate = resolver.resolveInitial(
            title = result.title,
            artist = result.artist,
            videoPoints = videoPoints,
            blocklist = emptySet(),
        )
        if (candidate == null) {
            _events.tryEmit(AddSongEvent.Toast("Couldn't find a YouTube match — try a different result"))
            _state.value = _state.value.copy(isAdding = false)
            return
        }
        val coverInitials = (result.artist.take(1) + result.title.take(1)).uppercase().ifEmpty { "??" }
        val songTemplate = Song(
            id = UUID.randomUUID().toString(),
            title = result.title,
            artist = result.artist,
            bpm = parsed.bpm,
            timeSig = parsed.timeSig,
            bars = parsed.bars,
            coverInitials = coverInitials,
            importedFrom = ImportSource.BUNDLED,
            lastPlayed = null,
            youtubeVideoId = null,  // filled in on confirm
            songsterrId = result.songId,
            songsterrRevisionId = revisionId.toString(),
            videoPoints = videoPoints,
        )
        _state.value = _state.value.copy(
            pendingConfirm = PendingConfirm(songTemplate, candidate),
            // isAdding stays true so the search list stays disabled while the dialog is up
        )
    }
}
