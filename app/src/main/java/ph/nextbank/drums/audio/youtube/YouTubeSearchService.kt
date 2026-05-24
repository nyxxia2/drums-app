package ph.nextbank.drums.audio.youtube

interface YouTubeSearchService {
    /**
     * Search YouTube for [query] and return the first video result whose ID
     * isn't in [blocklist]. Returns null if no results or if the network/search
     * fails.
     */
    suspend fun findFor(query: String, blocklist: Set<String> = emptySet()): SearchResult?

    /**
     * Extract a directly-playable audio stream URL for [videoId]. The URL is
     * short-lived (typically expires within ~6 hours) so callers should fetch
     * fresh each time. Returns null on extraction failure.
     */
    suspend fun getAudioStreamUrl(videoId: String): String?

    /**
     * Look up title / channel / duration / thumbnail for [videoId]. Used when
     * the videoId came from somewhere other than search (e.g. Songsterr's
     * video-points list) but we still want to render the Confirming dialog
     * with familiar metadata. Returns null on extraction failure.
     */
    suspend fun fetchMeta(videoId: String): SearchResult?
}

data class SearchResult(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val durationSec: Int,
    val thumbnailUrl: String,
)
