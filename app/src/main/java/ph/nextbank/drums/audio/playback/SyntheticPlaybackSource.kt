package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.DrumSampleBank
import ph.nextbank.drums.audio.SongClock
import ph.nextbank.drums.data.model.Song

class SyntheticPlaybackSource(
    private val song: Song,
    private val bank: DrumSampleBank,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) : PlaybackSource {

    private val clock = SongClock(
        bpmProvider = { song.bpm },
        totalSlots = song.totalBars * song.slotsPerBar,
    )

    private val _state = MutableStateFlow(PlaybackState.Ready)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentSlot = MutableStateFlow(0f)
    override val currentSlot: StateFlow<Float> = _currentSlot.asStateFlow()

    private val _activeSlotIndex = MutableStateFlow(0)
    override val activeSlotIndex: StateFlow<Int> = _activeSlotIndex.asStateFlow()

    private var frameJob: Job? = null

    override fun play() {
        clock.play(nowMs())
        _state.value = PlaybackState.Playing
        startFrameLoop()
    }

    override fun pause() {
        clock.pause(nowMs())
        _state.value = PlaybackState.Paused
        frameJob?.cancel()
        frameJob = null
    }

    override fun stop() {
        clock.stop()
        _state.value = PlaybackState.Ready
        _currentSlot.value = 0f
        _activeSlotIndex.value = 0
        frameJob?.cancel()
        frameJob = null
    }

    override fun nudgeOffset(deltaMs: Int) { /* no-op for synth */ }

    override fun release() {
        frameJob?.cancel()
        frameJob = null
    }

    private fun startFrameLoop() {
        frameJob?.cancel()
        frameJob = scope.launch {
            while (true) {
                val now = nowMs()
                if (clock.isFinished(now)) {
                    clock.pause(now)
                    _state.value = PlaybackState.Finished
                    break
                }
                val cur = clock.currentSlot(now)
                _currentSlot.value = cur
                _activeSlotIndex.value = cur.toInt()
                clock.slotsJustEntered(now).forEach { idx ->
                    val barIdx = idx / song.slotsPerBar
                    val slotIdx = idx % song.slotsPerBar
                    song.bars.getOrNull(barIdx)?.get(slotIdx)?.forEach(bank::play)
                }
                delay(16)
            }
        }
    }
}
