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

    /** When false (default), playback stops at the end of the song; when true, it wraps. */
    @Volatile var looping: Boolean = false

    /** Time (nowMs) at which the most-recent play() started. */
    private var playStartMs: Long = 0
    /** Slot value at the moment of the most-recent play(). */
    private var slotAtPlayStart: Float = 0f
    /** Slot value frozen during pause. */
    private var pausedSlot: Float = 0f
    /** Last raw (un-wrapped) integer slot that we reported as "entered" (-1 means "none yet"). */
    private var lastReportedRawSlot: Int = -1

    private val msPerSlot: Float get() = (60_000f / bpmProvider()) / slotsPerBeat

    /** Raw, un-wrapped slot position since play started — used internally to detect end. */
    private fun rawSlot(nowMs: Long): Float {
        val elapsed = nowMs - playStartMs
        return slotAtPlayStart + (elapsed.toFloat() / msPerSlot)
    }

    fun currentSlot(nowMs: Long): Float {
        if (!isPlaying) return pausedSlot
        val raw = rawSlot(nowMs)
        return if (looping) {
            ((raw % totalSlots) + totalSlots) % totalSlots
        } else {
            // Clamp to [0, totalSlots) so currentSlot.toInt() is always a valid slot.
            raw.coerceIn(0f, totalSlots.toFloat() - 0.001f)
        }
    }

    /**
     * True when, with looping=false, playback has reached the end of the song.
     * The PlayerViewModel polls this each frame and pauses so we get a clean
     * stop instead of a jarring wrap-around.
     */
    fun isFinished(nowMs: Long): Boolean {
        if (looping || !isPlaying) return false
        return rawSlot(nowMs) >= totalSlots.toFloat()
    }

    fun play(nowMs: Long) {
        if (isPlaying) return
        // If we'd stopped at the end, rewind to 0 on next play.
        if (!looping && pausedSlot >= totalSlots - 1f) {
            pausedSlot = 0f
            lastReportedRawSlot = -1
        }
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
        lastReportedRawSlot = -1
    }

    /**
     * Returns every integer slot crossed since the previous call, in order.
     * Audio scheduling iterates this each frame to fire drum hits — using a
     * list (rather than just the latest slot) means a jittery frame that
     * spans multiple slots won't silently drop the ones in the middle.
     *
     * Returns empty list when we haven't crossed any new slot boundary or
     * when the clock isn't playing.
     */
    fun slotsJustEntered(nowMs: Long): List<Int> {
        if (!isPlaying) return emptyList()
        // Track raw (un-wrapped) slot so we can detect full-cycle wraps
        // and any number of skipped slots between frames.
        val nowRaw = rawSlot(nowMs).toInt()
        if (nowRaw == lastReportedRawSlot) return emptyList()
        val out = mutableListOf<Int>()
        for (raw in (lastReportedRawSlot + 1)..nowRaw) {
            if (raw < 0) continue
            val mapped = if (looping) {
                ((raw % totalSlots) + totalSlots) % totalSlots
            } else {
                raw
            }
            if (mapped in 0 until totalSlots) out += mapped
        }
        lastReportedRawSlot = nowRaw
        return out
    }

    /** Force the next slots-entered call to fire for the current slot. */
    fun resetSlotTracking() {
        lastReportedRawSlot = -1
    }
}
