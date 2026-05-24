package ph.nextbank.drums.audio.songsterr

import kotlinx.serialization.Serializable

interface SongsterrVideoPointsService {
    /**
     * Fetch the list of YouTube videos Songsterr has aligned to a given tab,
     * with per-bar timestamps in video-time (seconds).
     *
     * Returns an empty list on network failure, HTTP error, or malformed JSON.
     * Callers treat empty == "no sync available" and fall back to the legacy
     * unsynced playback path.
     */
    suspend fun fetch(songId: Long, revisionId: Long): List<VideoPointEntry>
}

/**
 * One aligned YouTube video for a given (songId, revisionId).
 *
 * @param youtubeVideoId 11-char YouTube video ID.
 * @param points per-bar timestamps in seconds of video time; `points[i]` is
 *   the video-time at which bar `i` begins. May start negative if the tab's
 *   bar 1 precedes a "0:00" reference inside the video.
 * @param feature Songsterr's editorial tag for this video: "alternative",
 *   "solo", "backing", or null. We don't use it for selection (raw list
 *   order is what the web player uses) but keep it for future ranking.
 */
@Serializable
data class VideoPointEntry(
    val youtubeVideoId: String,
    val points: List<Double>,
    val feature: String?,
)
