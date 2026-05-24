package ph.nextbank.drums.audio.youtube

import ph.nextbank.drums.audio.songsterr.VideoPointEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks an initial YouTube candidate for a song.
 *
 * Synced songs (videoPoints non-empty) walk the entries in order, skipping IDs in
 * [blocklist] and skipping entries whose metadata fetch returns null. Unsynced
 * songs (videoPoints null or empty) delegate to [YouTubeSearchService.findFor].
 *
 * Returns null when no candidate can be resolved.
 */
@Singleton
class YouTubeCandidateResolver @Inject constructor(
    private val searchService: YouTubeSearchService,
) {
    suspend fun resolveInitial(
        title: String,
        artist: String,
        videoPoints: List<VideoPointEntry>?,
        blocklist: Set<String> = emptySet(),
    ): SearchResult? {
        if (!videoPoints.isNullOrEmpty()) {
            for (entry in videoPoints) {
                if (entry.youtubeVideoId in blocklist) continue
                val meta = searchService.fetchMeta(entry.youtubeVideoId)
                if (meta != null) return meta
            }
            return null
        }
        return searchService.findFor("$title $artist", blocklist)
    }
}
