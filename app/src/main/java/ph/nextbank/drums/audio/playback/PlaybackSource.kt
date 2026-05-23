package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState {
    Idle,        // not yet started anything
    Loading,     // YouTube player loading the video
    Ready,       // ready to play
    Playing,
    Paused,
    Finished,    // song ended (non-looping)
    Error,       // unrecoverable for this source — caller should fall back
}

interface PlaybackSource {
    val state: StateFlow<PlaybackState>
    /** Slot index (floating-point) currently under the playhead. */
    val currentSlot: StateFlow<Float>
    /** Drum tokens currently sounding — used by the chip strip. Always derivable from currentSlot + song, but exposed for convenience. */
    val activeSlotIndex: StateFlow<Int>

    fun play()
    fun pause()
    fun stop()
    /** Adjust YouTube/audio offset by [deltaMs]. No-op for sources that don't have an audio offset. */
    fun nudgeOffset(deltaMs: Int)
    /** Lifecycle cleanup. After this, the source is unusable. */
    fun release()
}
