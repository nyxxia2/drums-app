package ph.nextbank.drums.ui.practice

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

data class PracticeUi(
    val song: Song? = null,
    val startBpm: Int = 72,
    val targetBpm: Int = 116,
    val loops: Int = 8,
    val stepBpm: Int = 6,
    val currentLoop: Int = 3,
) {
    val currentBpm: Int get() = startBpm + (currentLoop - 1) * stepBpm
}

@HiltViewModel
class PracticeViewModel @Inject constructor(
    repo: SongRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = handle.get<String>("songId")!!
    private val _state = MutableStateFlow(PracticeUi())
    val state: StateFlow<PracticeUi> = _state.asStateFlow()

    init { viewModelScope.launch { _state.value = _state.value.copy(song = repo.findById(songId)) } }
}
