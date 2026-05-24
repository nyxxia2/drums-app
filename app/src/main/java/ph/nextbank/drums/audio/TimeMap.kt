package ph.nextbank.drums.audio

/**
 * Maps between video playback time (seconds) and song slot position.
 *
 * Two implementations: [ConstantBpmTimeMap] uses a fixed BPM (legacy / unsynced
 * songs); [PointsBasedTimeMap] uses per-bar timestamps from Songsterr's
 * /api/video-points endpoint (synced songs).
 */
interface TimeMap {
    val totalSlots: Int
    fun slotAt(videoSec: Float): Float
    fun videoSecAt(slot: Float): Float
}

class ConstantBpmTimeMap(
    bpm: Int,
    override val totalSlots: Int,
    slotsPerBeat: Int,
) : TimeMap {
    private val secPerSlot: Float = (60f / bpm) / slotsPerBeat
    override fun slotAt(videoSec: Float): Float = videoSec / secPerSlot
    override fun videoSecAt(slot: Float): Float = slot * secPerSlot
}
