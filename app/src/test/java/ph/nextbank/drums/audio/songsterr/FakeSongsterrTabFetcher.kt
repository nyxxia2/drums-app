package ph.nextbank.drums.audio.songsterr

class FakeSongsterrTabFetcher : SongsterrTabFetcher {
    var result: FetchResult = FetchResult.NoDrumTrack
    var lastSongId: Long? = null

    override suspend fun fetchDrumTrack(songId: Long): FetchResult {
        lastSongId = songId
        return result
    }
}
