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
    val timeSig: Pair<Int, Int>,
    val bars: List<List<List<DrumToken>>>,
    val coverInitials: String,
    val importedFrom: ImportSource,
    val lastPlayed: Instant?,
    /** 11-char YouTube video ID. null = the app will search YouTube on first open. */
    val youtubeVideoId: String? = null,
    /** Sync nudge: positive = video plays earlier relative to staff bar 1. */
    val youtubeOffsetMs: Int = 0,
    /** YouTube video IDs the user said "Try another video" on. */
    val youtubeBlocklist: List<String> = emptyList(),
    /** Songsterr songId for songs added via the Add Song flow. null for legacy / bundled songs. */
    val songsterrId: Long? = null,
    /** Songsterr revisionId for re-fetching. null for legacy / bundled songs. */
    val songsterrRevisionId: String? = null,
    /**
     * Songsterr-curated YouTube videos with per-bar sync timestamps. null = no
     * sync data available; the player falls back to legacy YouTube search and
     * constant-BPM playback.
     */
    val videoPoints: List<ph.nextbank.drums.audio.songsterr.VideoPointEntry>? = null,
) {
    val totalBars: Int get() = bars.size
    val slotsPerBar: Int get() = bars.firstOrNull()?.size ?: 16
}
