package ph.nextbank.drums.audio.youtube

class FakeYouTubeSearchService : YouTubeSearchService {
    /** Ordered list of results the fake will return. Each `findFor` call pops the next non-blocklisted item. */
    var queue: List<SearchResult> = emptyList()
    var lastQuery: String? = null
    var lastBlocklist: Set<String> = emptySet()
    var shouldReturnNull: Boolean = false

    override suspend fun findFor(query: String, blocklist: Set<String>): SearchResult? {
        lastQuery = query
        lastBlocklist = blocklist
        if (shouldReturnNull) return null
        return queue.firstOrNull { it.videoId !in blocklist }
    }
}
