package ph.nextbank.drums.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        viewModelScope.launch {
            val s = repo.findById(songId) ?: return@launch
            clock = SongClock(
                bpmProvider = { _state.value.song?.bpm ?: s.bpm },
                totalSlots = s.totalBars * s.slotsPerBar,
            ).also { it.looping = _state.value.looping }
            _state.value = _state.value.copy(song = s)
        }
    }

    fun togglePlay(nowMs: Long) {
        val c = clock ?: return
        if (c.isPlaying) c.pause(nowMs) else c.play(nowMs)
        _state.value = _state.value.copy(playing = c.isPlaying)
    }

    fun stop() {
        clock?.stop()
        _state.value = _state.value.copy(playing = false, currentSlot = 0f)
    }

    fun onFrame(nowMs: Long) {
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

    fun toggleMetronome() {
        _state.value = _state.value.copy(metronomeOn = !_state.value.metronomeOn)
    }

    fun toggleLoop() {
        val c = clock ?: return
        val newLoop = !_state.value.looping
        c.looping = newLoop
        _state.value = _state.value.copy(looping = newLoop)
    }
}
