package ph.nextbank.drums.audio.youtube

interface YouTubeSearchService {
    /**
     * Search YouTube for [query] and return the first video result whose ID
     * isn't in [blocklist]. Returns null if no results or if the network/search
     * fails.
     */
    suspend fun findFor(query: String, blocklist: Set<String> = emptySet()): SearchResult?
}

data class SearchResult(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val durationSec: Int,
    val thumbnailUrl: String,
)
