package ph.nextbank.drums.audio

/**
 * Derives the playhead's `currentSlot` (a floating-point sixteenth-note index)
 * from monotonic wall-clock time. Pure, deterministic, no Android dependencies
 * — `nowMs` is passed in by callers (production callers pass
 * `SystemClock.elapsedRealtime()`).
 *
 * The slot is shared by the visual playhead and the audio scheduler so they
 * always agree about where in the song we are.
 */
class SongClock(
    private val bpmProvider: () -> Int,
    private val totalSlots: Int,
    private val slotsPerBeat: Int = 4,
) {
    @Volatile var isPlaying: Boolean = false
        private set

    /** Time (nowMs) at which the most-recent play() started. */
    private var playStartMs: Long = 0
    /** Slot value at the moment of the most-recent play(). */
    private var slotAtPlayStart: Float = 0f
    /** Slot value frozen during pause. */
    private var pausedSlot: Float = 0f
    /** Last integer slot that we reported as "entered". */
    private var lastReportedSlot: Int = -1

    private val msPerSlot: Float get() = (60_000f / bpmProvider()) / slotsPerBeat

    fun currentSlot(nowMs: Long): Float {
        if (!isPlaying) return pausedSlot
        val elapsed = nowMs - playStartMs
        val raw = slotAtPlayStart + (elapsed.toFloat() / msPerSlot)
        // Wrap inside [0, totalSlots)
        return ((raw % totalSlots) + totalSlots) % totalSlots
    }

    fun play(nowMs: Long) {
        if (isPlaying) return
        slotAtPlayStart = pausedSlot
        playStartMs = nowMs
        isPlaying = true
    }

    fun pause(nowMs: Long) {
        if (!isPlaying) return
        pausedSlot = currentSlot(nowMs)
        isPlaying = false
    }

    fun stop() {
        isPlaying = false
        pausedSlot = 0f
        slotAtPlayStart = 0f
        lastReportedSlot = -1
    }

    /**
     * Returns the integer slot that was just entered between the previous poll
     * and this one, or null if no integer-slot boundary was crossed.
     * Audio scheduling polls this each frame to fire drum hits.
     */
    fun slotJustEntered(nowMs: Long): Int? {
        val nowSlot = currentSlot(nowMs).toInt()
        if (nowSlot != lastReportedSlot) {
            lastReportedSlot = nowSlot
            return nowSlot
        }
        return null
    }

    /** Force the next `slotJustEntered` call to fire for the current slot. */
    fun resetSlotTracking() {
        lastReportedSlot = -1
    }
}
