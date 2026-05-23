package ph.nextbank.drums.audio.songsterr

class FakeSongsterrSearchService : SongsterrSearchService {
    var results: List<SongsterrResult> = emptyList()
    var lastQuery: String? = null
    var shouldThrow: Throwable? = null

    override suspend fun search(query: String): List<SongsterrResult> {
        lastQuery = query
        shouldThrow?.let { throw it }
        return results
    }
}
