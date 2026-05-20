package ph.nextbank.drums.ui.song_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import javax.inject.Inject

data class SongDetailUi(
    val song: Song? = null,
    val tempoOffset: Int = 0,
    val countInBars: Int = 1,
    val metronomeMode: String = "On · soft",
    val kit: String = "Acoustic — Studio",
    val mutedDrum: String = "Hi-hat",
)

@HiltViewModel
class SongDetailViewModel @Inject constructor(
    private val repo: SongRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = handle.get<String>("songId")!!
    private val _state = MutableStateFlow(SongDetailUi())
    val state: StateFlow<SongDetailUi> = _state.asStateFlow()

    init { viewModelScope.launch { _state.value = _state.value.copy(song = repo.findById(songId)) } }

    fun changeTempo(delta: Int) {
        viewModelScope.launch {
            val cur = _state.value.song ?: return@launch
            val newBpm = (cur.bpm + delta).coerceIn(40, 240)
            repo.updateBpm(cur.id, newBpm)
            _state.value = _state.value.copy(
                song = cur.copy(bpm = newBpm),
                tempoOffset = _state.value.tempoOffset + delta,
            )
        }
    }
}
