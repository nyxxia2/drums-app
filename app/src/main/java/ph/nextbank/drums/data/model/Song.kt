package ph.nextbank.drums.data.model

import java.time.Instant

/**
 * A song is a sequence of bars; each bar is a sequence of subdivision slots;
 * each slot is a (possibly empty) list of drum tokens hit at that subdivision.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val timeSig: Pair<Int, Int>,        // (beatsPerBar, beatUnit) — Phase 1 always (4, 4)
    val bars: List<List<List<DrumToken>>>,
    val coverInitials: String,           // e.g. "NV", "RU"
    val importedFrom: ImportSource,
    val lastPlayed: Instant?,
    /** 11-char YouTube video ID, e.g. "hTWKbfoikeg". null = play with bundled samples. */
    val youtubeVideoId: String? = null,
) {
    val totalBars: Int get() = bars.size
    val slotsPerBar: Int get() = bars.firstOrNull()?.size ?: 16
}
