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
            )
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
        _state.value = _state.value.copy(currentSlot = c.currentSlot(nowMs))
        c.slotJustEntered(nowMs)?.let { idx ->
            val s = _state.value.song ?: return
            val barIdx = idx / s.slotsPerBar
            val slotIdx = idx % s.slotsPerBar
            s.bars.getOrNull(barIdx)?.get(slotIdx)?.forEach(bank::play)
        }
    }

    fun toggleMetronome() {
        _state.value = _state.value.copy(metronomeOn = !_state.value.metronomeOn)
    }
}
