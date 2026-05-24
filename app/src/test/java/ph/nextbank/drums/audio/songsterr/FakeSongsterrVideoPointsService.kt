package ph.nextbank.drums.audio.songsterr

class FakeSongsterrVideoPointsService(
    private val response: List<VideoPointEntry> = emptyList(),
) : SongsterrVideoPointsService {

    var lastSongId: Long? = null
        private set
    var lastRevisionId: Long? = null
        private set

    override suspend fun fetch(songId: Long, revisionId: Long): List<VideoPointEntry> {
        lastSongId = songId
        lastRevisionId = revisionId
        return response
    }
}
