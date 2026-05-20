package ph.nextbank.drums.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.DrumSampleBank
import ph.nextbank.drums.audio.SongClock
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import javax.inject.Inject

data class PlayerUiState(
    val song: Song? = null,
    val playing: Boolean = false,
    val currentSlot: Float = 0f,
    val metronomeOn: Boolean = false,
    val looping: Boolean = false,
    /** True when audio playback is driven by an embedded YouTube video, not SongClock. */
    val youtubeMode: Boolean = false,
    /** True when the YouTube player reports it has loaded the requested video. */
    val youtubeReady: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: SongRepository,
    val bank: DrumSampleBank,
    handle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = handle.get<String>("songId")!!

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    var clock: SongClock? = null
        private set

    private var youtubePlayer: YouTubePlayer? = null

    init {
        viewModelScope.launch {
            val s = repo.findById(songId) ?: return@launch
            clock = SongClock(
                bpmProvider = { _state.value.song?.bpm ?: s.bpm },
                totalSlots = s.totalBars * s.slotsPerBar,
            ).also { it.looping = _state.value.looping }
            _state.value = _state.value.copy(
                song = s,
                youtubeMode = s.youtubeVideoId != null,
            )
        }
    }

    fun setYoutubePlayer(p: YouTubePlayer?) {
        youtubePlayer = p
        _state.value = _state.value.copy(youtubeReady = p != null)
    }

    fun togglePlay(nowMs: Long) {
        if (_state.value.youtubeMode) {
            val yt = youtubePlayer ?: return
            if (_state.value.playing) yt.pause() else yt.play()
            // Don't optimistically flip 'playing' — wait for onYoutubeStateChange so we
            // reflect the player's actual state (e.g. buffering counts as not-playing).
        } else {
            val c = clock ?: return
            if (c.isPlaying) c.pause(nowMs) else c.play(nowMs)
            _state.value = _state.value.copy(playing = c.isPlaying)
        }
    }

    fun stop() {
        if (_state.value.youtubeMode) {
            youtubePlayer?.seekTo(0f)
            youtubePlayer?.pause()
            _state.value = _state.value.copy(playing = false, currentSlot = 0f)
        } else {
            clock?.stop()
            _state.value = _state.value.copy(playing = false, currentSlot = 0f)
        }
    }

    fun onFrame(nowMs: Long) {
        if (_state.value.youtubeMode) return // currentSlot is driven by YouTube callbacks
        val c = clock ?: return
        // Detect a clean end-of-song first so the visual playhead doesn't snap
        // back to bar 1 on the same frame the audio scheduler tries to wrap.
        if (c.isFinished(nowMs)) {
            c.pause(nowMs)
            _state.value = _state.value.copy(playing = false)
            return
        }
        val cur = c.currentSlot(nowMs)
        _state.value = _state.value.copy(currentSlot = cur)
        // Fire every slot crossed since last frame, not just the latest one —
        // a jittery frame at fast tempo could otherwise drop drum hits.
        val s = _state.value.song ?: return
        c.slotsJustEntered(nowMs).forEach { idx ->
            val barIdx = idx / s.slotsPerBar
            val slotIdx = idx % s.slotsPerBar
            s.bars.getOrNull(barIdx)?.get(slotIdx)?.forEach(bank::play)
        }
    }

    /** YouTube player progress callback — maps elapsed seconds to a slot index. */
    fun onYoutubeSecond(seconds: Float) {
        val s = _state.value.song ?: return
        // slot = seconds * (BPM / 60) * slotsPerBeat
        // slotsPerBeat is implied 4 (sixteenth-notes) per the SongClock default.
        val slot = seconds * (s.bpm / 60f) * 4f
        val totalSlots = s.totalBars * s.slotsPerBar
        val clamped = slot.coerceIn(0f, totalSlots.toFloat() - 0.001f)
        _state.value = _state.value.copy(currentSlot = clamped)
    }

    fun onYoutubeStateChange(state: PlayerConstants.PlayerState) {
        val playing = state == PlayerConstants.PlayerState.PLAYING
        _state.value = _state.value.copy(playing = playing)
    }

    fun toggleMetronome() {
        _state.value = _state.value.copy(metronomeOn = !_state.value.metronomeOn)
    }

    fun toggleLoop() {
        if (_state.value.youtubeMode) return // looping is YouTube's responsibility there
        val c = clock ?: return
        val newLoop = !_state.value.looping
        c.looping = newLoop
        _state.value = _state.value.copy(looping = newLoop)
    }
}
