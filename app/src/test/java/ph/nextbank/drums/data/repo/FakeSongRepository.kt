package ph.nextbank.drums.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ph.nextbank.drums.audio.songsterr.VideoPointEntry
import ph.nextbank.drums.data.model.Song

class FakeSongRepository : SongRepository {
    private val songs = MutableStateFlow<Map<String, Song>>(emptyMap())
    fun seed(song: Song) { songs.value = songs.value + (song.id to song) }
    fun snapshot(id: String): Song? = songs.value[id]
    /** Test helper — returns all currently-stored songs. */
    fun allSnapshot(): List<Song> = songs.value.values.toList()

    override fun observeAll(): Flow<List<Song>> = songs.map { it.values.toList() }
    override suspend fun findById(id: String): Song? = songs.value[id]
    override suspend fun upsertAll(s: List<Song>) {
        songs.value = songs.value + s.associateBy { it.id }
    }
    override suspend fun seedNew(s: List<Song>) {
        val existing = songs.value
        songs.value = existing + s.filter { it.id !in existing }.associateBy { it.id }
    }
    override suspend fun updateBpm(id: String, bpm: Int) {
        songs.value = songs.value + (id to songs.value[id]!!.copy(bpm = bpm))
    }
    override suspend fun updateYoutubeVideoId(id: String, videoId: String?) {
        songs.value = songs.value + (id to songs.value[id]!!.copy(youtubeVideoId = videoId))
    }
    override suspend fun updateYoutubeOffset(id: String, offsetMs: Int) {
        songs.value = songs.value + (id to songs.value[id]!!.copy(youtubeOffsetMs = offsetMs))
    }
    override suspend fun updateYoutubeBlocklist(id: String, blocklist: List<String>) {
        songs.value = songs.value + (id to songs.value[id]!!.copy(youtubeBlocklist = blocklist))
    }
    override suspend fun updateVideoPoints(id: String, entries: List<VideoPointEntry>?) {
        songs.value = songs.value + (id to songs.value[id]!!.copy(videoPoints = entries))
    }
}
