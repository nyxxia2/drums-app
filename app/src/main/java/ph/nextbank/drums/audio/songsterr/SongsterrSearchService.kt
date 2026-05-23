package ph.nextbank.drums.audio.songsterr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface SongsterrSearchService {
    /**
     * Returns up to ~20 Songsterr songs matching [query] across all tabs.
     * Each result may include zero, one, or many drum tracks — drum extraction
     * is the caller's job.
     *
     * Returns an empty list on network failure (caller's UI handles the
     * "nothing found" / "search failed" UX as one path).
     */
    suspend fun search(query: String): List<SongsterrResult>
}

@Serializable
data class SongsterrResult(
    val songId: Long,
    val artistId: Long,
    val artist: String,
    val title: String,
    val tracks: List<SongsterrTrack> = emptyList(),
    /** Index into [tracks] of the most-popular drum track, or null if no drums. */
    val popularTrackDrum: Int? = null,
)

@Serializable
data class SongsterrTrack(
    val instrumentId: Int,
    val instrument: String,
    val name: String,
    val views: Int = 0,
    val difficulty: Int? = null,
    val hash: String,
) {
    /** Songsterr's instrumentId 1024 means "Drums" — verified against the live API. */
    val isDrums: Boolean get() = instrumentId == 1024
}
