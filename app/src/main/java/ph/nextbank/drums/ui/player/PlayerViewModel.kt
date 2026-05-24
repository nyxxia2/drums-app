package ph.nextbank.drums.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ph.nextbank.drums.audio.DrumSampleBankApi
import ph.nextbank.drums.audio.playback.PlaybackSource
import ph.nextbank.drums.audio.playback.PlaybackState
import ph.nextbank.drums.audio.playback.SyntheticPlaybackSource
import ph.nextbank.drums.audio.playback.YouTubeAdapterFactory
import ph.nextbank.drums.audio.playback.YouTubePlaybackSource
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.audio.youtube.YouTubeCandidateResolver
import ph.nextbank.drums.audio.youtube.YouTubeSearchService
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import javax.inject.Inject

sealed interface PlayerPhase {
    data object Loading : PlayerPhase
    data object Searching : PlayerPhase
    data class Confirming(val candidates: List<SearchResult>) : PlayerPhase
    data class YouTubeBuffering(val videoId: String) : PlayerPhase
    data class YouTubeReady(val videoId: String) : PlayerPhase
    data object SynthFallback : PlayerPhase
}

data class PlayerUiState(
    val song: Song? = null,
    val phase: PlayerPhase = PlayerPhase.Loading,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val currentSlot: Float = 0f,
    val activeSlotIndex: Int = 0,
    val youtubeOffsetMs: Int = 0,
    val metronomeOn: Boolean = false,
    val looping: Boolean = false,
)

sealed interface PlayerEvent {
    data class Toast(val message: String) : PlayerEvent
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: SongRepository,
    private val bank: DrumSampleBankApi,
    private val searchService: YouTubeSearchService,
    private val resolver: YouTubeCandidateResolver,
    private val adapterFactory: YouTubeAdapterFactory,
    handle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = handle.get<String>("songId")!!

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private var source: PlaybackSource? = null
    private val sourceJobs = mutableListOf<Job>()
    /** Counts how many videos we've tried for this song since open. Bounded to avoid loops. */
    private var extractionAttempts = 0

    init {
        viewModelScope.launch {
            val s = repo.findById(songId) ?: return@launch
            _state.value = _state.value.copy(
                song = s,
                youtubeOffsetMs = s.youtubeOffsetMs,
            )
            val cachedId = s.youtubeVideoId
            if (cachedId != null) {
                startYouTubePlayback(cachedId)
            } else {
                showCandidates(s, s.youtubeBlocklist.toSet())
            }
        }
    }

    /** Resolve candidates and either show them in the confirm dialog or fall back to synth. */
    private suspend fun showCandidates(song: Song, blocklist: Set<String>) {
        _state.value = _state.value.copy(phase = PlayerPhase.Searching)
        val candidates = resolver.resolveInitial(
            title = song.title,
            artist = song.artist,
            videoPoints = song.videoPoints,
            blocklist = blocklist,
        )
        if (candidates.isEmpty()) {
            _events.tryEmit(PlayerEvent.Toast("No playable YouTube result — playing synth drums"))
            switchToSynth()
        } else {
            _state.value = _state.value.copy(phase = PlayerPhase.Confirming(candidates))
        }
    }

    fun acceptCandidate(candidate: SearchResult) {
        viewModelScope.launch {
            startYouTubePlayback(candidate.videoId)
        }
    }

    /**
     * Fetch the audio stream URL for [videoId] and wire up an ExoPlayer-backed
     * YouTubePlaybackSource.
     *
     * NewPipe Extractor sometimes returns 0 streams for specific videos (YouTube applies
     * per-video player-JS obfuscation that NewPipe can't always decode). Up to
     * [MAX_EXTRACTION_ATTEMPTS] different videos are tried per song open before falling
     * back to synth. Failed videoIds are added to the persistent blocklist so re-opening
     * the song skips them too.
     */
    private suspend fun startYouTubePlayback(videoId: String) {
        _state.value = _state.value.copy(phase = PlayerPhase.YouTubeBuffering(videoId))
        extractionAttempts++
        val streamUrl = withTimeoutOrNull(STREAM_EXTRACT_TIMEOUT_MS) {
            searchService.getAudioStreamUrl(videoId)
        }
        if (streamUrl == null) {
            if (extractionAttempts >= MAX_EXTRACTION_ATTEMPTS) {
                _events.tryEmit(PlayerEvent.Toast("YouTube audio unavailable — playing synth drums"))
                switchToSynth()
                return
            }
            // Blocklist the failed videoId so re-opening the song skips it.
            // Do NOT clear youtubeVideoId — the user's prior choice should survive a transient
            // NewPipe extraction failure. If this song had no prior cache, there's nothing to keep.
            val failedBlocklist = (_state.value.song?.youtubeBlocklist.orEmpty() + videoId).distinct()
            repo.updateYoutubeBlocklist(songId, failedBlocklist)
            _state.value = _state.value.copy(
                song = _state.value.song?.copy(youtubeBlocklist = failedBlocklist),
            )
            _events.tryEmit(PlayerEvent.Toast("Audio unavailable for this video — trying another"))
            val currentSong = _state.value.song ?: return
            showCandidates(currentSong, failedBlocklist.toSet())
            return
        }
        // Stream URL resolved → this videoId is proven playable. Persist it so future opens
        // skip the picker. Idempotent for the cold-open-from-cache case.
        if (_state.value.song?.youtubeVideoId != videoId) {
            repo.updateYoutubeVideoId(songId, videoId)
            _state.value = _state.value.copy(
                song = _state.value.song?.copy(youtubeVideoId = videoId),
            )
        }
        val s = _state.value.song ?: return
        val adapter = adapterFactory.create(streamUrl)

        val syncedEntry = s.videoPoints?.firstOrNull { it.youtubeVideoId == videoId }
        val timeMap: ph.nextbank.drums.audio.TimeMap = if (syncedEntry != null) {
            ph.nextbank.drums.audio.PointsBasedTimeMap(
                points = syncedEntry.points,
                slotsPerBar = s.slotsPerBar,
                totalSlots = s.totalBars * s.slotsPerBar,
            )
        } else {
            ph.nextbank.drums.audio.ConstantBpmTimeMap(
                bpm = s.bpm,
                totalSlots = s.totalBars * s.slotsPerBar,
                slotsPerBeat = s.slotsPerBar / s.timeSig.first,
            )
        }

        val src = YouTubePlaybackSource(
            timeMap = timeMap,
            adapter = adapter,
            initialOffsetMs = s.youtubeOffsetMs,
        )
        source = src
        wireSource(src)
        sourceJobs += viewModelScope.launch {
            src.state.collect { st ->
                if (st == PlaybackState.Ready &&
                    _state.value.phase is PlayerPhase.YouTubeBuffering
                ) {
                    _state.value = _state.value.copy(phase = PlayerPhase.YouTubeReady(videoId))
                }
                if (st == PlaybackState.Error) {
                    val reason = src.lastErrorMessage ?: "unknown"
                    _events.tryEmit(PlayerEvent.Toast("Audio error ($reason) — playing synth drums"))
                    switchToSynth()
                }
            }
        }
    }

    /** Reopen the confirm dialog with the original candidate list minus the current selection. */
    fun tryAnotherVideo() {
        val cur = _state.value
        val song = cur.song ?: return
        val currentVideoId: String? = when (val p = cur.phase) {
            is PlayerPhase.Confirming -> null  // already in confirm; no current to blocklist
            is PlayerPhase.YouTubeReady -> p.videoId
            is PlayerPhase.YouTubeBuffering -> p.videoId
            else -> null
        }
        viewModelScope.launch {
            val newBlocklist = (song.youtubeBlocklist + listOfNotNull(currentVideoId)).distinct()
            if (currentVideoId != null) {
                repo.updateYoutubeBlocklist(songId, newBlocklist)
                repo.updateYoutubeVideoId(songId, null)
            }
            source?.release(); source = null
            val updatedSong = song.copy(
                youtubeBlocklist = newBlocklist,
                youtubeVideoId = null,
            )
            _state.value = cur.copy(song = updatedSong)
            showCandidates(updatedSong, newBlocklist.toSet())
        }
    }

    fun dismissConfirmation() {
        if (_state.value.phase is PlayerPhase.Confirming) switchToSynth()
    }

    private fun switchToSynth() {
        source?.release()
        val s = _state.value.song ?: return
        val synth = SyntheticPlaybackSource(s, bank, viewModelScope)
        source = synth
        wireSource(synth)
        _state.value = _state.value.copy(phase = PlayerPhase.SynthFallback)
    }

    private fun wireSource(src: PlaybackSource) {
        sourceJobs.forEach { it.cancel() }
        sourceJobs.clear()
        sourceJobs += viewModelScope.launch {
            src.state.collect { _state.value = _state.value.copy(playbackState = it) }
        }
        sourceJobs += viewModelScope.launch {
            src.currentSlot.collect { _state.value = _state.value.copy(currentSlot = it) }
        }
        sourceJobs += viewModelScope.launch {
            src.activeSlotIndex.collect { _state.value = _state.value.copy(activeSlotIndex = it) }
        }
    }

    fun togglePlay() {
        val src = source ?: return
        when (src.state.value) {
            PlaybackState.Playing -> src.pause()
            PlaybackState.Ready, PlaybackState.Paused, PlaybackState.Finished -> src.play()
            else -> { }
        }
    }

    fun stop() { source?.stop() }

    fun nudgeOffset(deltaMs: Int) {
        source?.nudgeOffset(deltaMs)
        val newOffset = _state.value.youtubeOffsetMs + deltaMs
        _state.value = _state.value.copy(youtubeOffsetMs = newOffset)
        viewModelScope.launch { repo.updateYoutubeOffset(songId, newOffset) }
    }

    fun toggleMetronome() {
        _state.value = _state.value.copy(metronomeOn = !_state.value.metronomeOn)
    }

    fun toggleLoop() {
        _state.value = _state.value.copy(looping = !_state.value.looping)
    }

    override fun onCleared() {
        source?.release()
        super.onCleared()
    }

    companion object {
        /** Max time we wait for NewPipe to extract an audio stream URL before giving up. */
        private const val STREAM_EXTRACT_TIMEOUT_MS = 25_000L
        /** Max videos to try for a single song before falling back to synth. */
        private const val MAX_EXTRACTION_ATTEMPTS = 3
    }
}
