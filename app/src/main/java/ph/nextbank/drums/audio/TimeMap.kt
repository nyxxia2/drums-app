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

/**
 * Maps video-time to slot using a list of per-bar timestamps. The points list
 * has one entry per bar — `points[i]` is the video-time (seconds) at which
 * bar `i` starts. Within a bar, slot position is linearly interpolated.
 *
 * For `t < points[0]` returns 0 (clamped).
 * For `t >= points.last()` returns `totalSlots - 1` (clamped).
 */
class PointsBasedTimeMap(
    private val points: List<Double>,
    private val slotsPerBar: Int,
    override val totalSlots: Int,
) : TimeMap {

    init {
        require(points.size >= 2) { "PointsBasedTimeMap needs at least 2 points; got ${points.size}" }
        require(slotsPerBar > 0)
        require(totalSlots > 0)
    }

    override fun slotAt(videoSec: Float): Float {
        val t = videoSec.toDouble()
        if (t <= points[0]) return 0f
        if (t >= points.last()) return (totalSlots - 1).toFloat()

        // Binary search for bar i where points[i] <= t < points[i+1].
        var lo = 0
        var hi = points.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (points[mid] <= t) lo = mid else hi = mid
        }
        val i = lo
        val fraction = (t - points[i]) / (points[i + 1] - points[i])
        val slot = (i + fraction) * slotsPerBar
        return slot.toFloat().coerceIn(0f, (totalSlots - 1).toFloat())
    }

    override fun videoSecAt(slot: Float): Float {
        if (slot <= 0f) return points[0].toFloat()
        val maxSlot = (points.size - 1) * slotsPerBar
        if (slot >= maxSlot) return points.last().toFloat()

        val bar = (slot / slotsPerBar).toInt().coerceIn(0, points.size - 2)
        val fraction = (slot - bar * slotsPerBar) / slotsPerBar.toFloat()
        val sec = points[bar] + fraction * (points[bar + 1] - points[bar])
        return sec.toFloat()
    }
}
