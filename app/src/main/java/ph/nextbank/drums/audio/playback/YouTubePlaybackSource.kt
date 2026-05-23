package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface YouTubeAdapter {
    fun play()
    fun pause()
    fun stop()
    fun seekToSeconds(seconds: Float)
    fun setListener(listener: YouTubeAdapterListener)
    /** Release any underlying resources (player instance, network observer, etc.). */
    fun release() {}
}

interface YouTubeAdapterListener {
    fun onReady()
    fun onPlay()
    fun onPause()
    fun onEnded()
    fun onError(message: String)
    fun onCurrentSecond(seconds: Float)
}

class YouTubePlaybackSource(
    private val songBpm: Int,
    private val slotsPerBeat: Int,
    private val totalSlots: Int,
    private val adapter: YouTubeAdapter,
    initialOffsetMs: Int,
) : PlaybackSource {

    private val _state = MutableStateFlow(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentSlot = MutableStateFlow(0f)
    override val currentSlot: StateFlow<Float> = _currentSlot.asStateFlow()

    private val _activeSlotIndex = MutableStateFlow(0)
    override val activeSlotIndex: StateFlow<Int> = _activeSlotIndex.asStateFlow()

    var offsetMs: Int = initialOffsetMs
        private set

    /** Last error message reported by the YouTube player (e.g. "VIDEO_NOT_PLAYABLE_IN_CONTAINER"). */
    var lastErrorMessage: String? = null
        private set

    private val msPerSlot: Float = (60_000f / songBpm) / slotsPerBeat

    init {
        adapter.setListener(object : YouTubeAdapterListener {
            override fun onReady() {
                if (_state.value == PlaybackState.Idle) _state.value = PlaybackState.Ready
            }
            override fun onPlay() { _state.value = PlaybackState.Playing }
            override fun onPause() {
                if (_state.value == PlaybackState.Playing) _state.value = PlaybackState.Paused
            }
            override fun onEnded() { _state.value = PlaybackState.Finished }
            override fun onError(message: String) {
                lastErrorMessage = message
                _state.value = PlaybackState.Error
            }
            override fun onCurrentSecond(seconds: Float) {
                val effectiveMs = seconds * 1000f + offsetMs
                val slot = effectiveMs / msPerSlot
                val clamped = slot.coerceIn(0f, totalSlots.toFloat() - 0.001f)
                _currentSlot.value = clamped
                _activeSlotIndex.value = clamped.toInt()
            }
        })
    }

    override fun play() { adapter.play() }
    override fun pause() { adapter.pause() }
    override fun stop() {
        adapter.stop()
        _state.value = PlaybackState.Ready
        _currentSlot.value = 0f
        _activeSlotIndex.value = 0
    }
    override fun nudgeOffset(deltaMs: Int) { offsetMs += deltaMs }
    override fun release() { adapter.release() }
}
