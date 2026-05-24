package ph.nextbank.drums.audio.youtube

class FakeYouTubeSearchService : YouTubeSearchService {
    /** Ordered list of results the fake will return. Each `findFor` call pops the next non-blocklisted item. */
    var queue: List<SearchResult> = emptyList()
    var lastQuery: String? = null
    var lastBlocklist: Set<String> = emptySet()
    var shouldReturnNull: Boolean = false

    /** Maps videoId -> audio URL the fake will return. */
    var audioUrls: Map<String, String> = emptyMap()

    /** Maps videoId -> SearchResult the fake will return from fetchMeta. */
    var metaResults: Map<String, SearchResult> = emptyMap()

    override suspend fun findFor(query: String, blocklist: Set<String>): SearchResult? {
        lastQuery = query
        lastBlocklist = blocklist
        if (shouldReturnNull) return null
        return queue.firstOrNull { it.videoId !in blocklist }
    }

    override suspend fun getAudioStreamUrl(videoId: String): String? = audioUrls[videoId]

    override suspend fun fetchMeta(videoId: String): SearchResult? = metaResults[videoId]
}
