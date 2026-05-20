package ph.nextbank.drums.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(repo: SongRepository) : ViewModel() {
    val songs: StateFlow<List<Song>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
