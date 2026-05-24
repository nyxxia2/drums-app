package ph.nextbank.drums.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.nextbank.drums.audio.songsterr.VideoPointEntry
import ph.nextbank.drums.data.db.SongDao
import ph.nextbank.drums.data.db.SongEntity
import ph.nextbank.drums.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

interface SongRepository {
    fun observeAll(): Flow<List<Song>>
    suspend fun findById(id: String): Song?
    suspend fun upsertAll(songs: List<Song>)
    /** Insert songs that don't already exist (by id). Existing rows — including user-set youtubeVideoId / offset / blocklist — are untouched. */
    suspend fun seedNew(songs: List<Song>)
    suspend fun updateBpm(id: String, bpm: Int)
    suspend fun updateYoutubeVideoId(id: String, videoId: String?)
    suspend fun updateYoutubeOffset(id: String, offsetMs: Int)
    suspend fun updateYoutubeBlocklist(id: String, blocklist: List<String>)
    suspend fun updateVideoPoints(id: String, entries: List<VideoPointEntry>?)
}

@Singleton
class RoomSongRepository @Inject constructor(private val dao: SongDao) : SongRepository {
    override fun observeAll(): Flow<List<Song>> = dao.observeAll().map { list -> list.map(SongEntity::toSong) }
    override suspend fun findById(id: String): Song? = dao.findById(id)?.toSong()
    override suspend fun upsertAll(songs: List<Song>) = dao.insertAll(songs.map(SongEntity::fromSong))
    override suspend fun seedNew(songs: List<Song>) = dao.seedNew(songs.map(SongEntity::fromSong))
    override suspend fun updateBpm(id: String, bpm: Int) = dao.updateBpm(id, bpm)
    override suspend fun updateYoutubeVideoId(id: String, videoId: String?) = dao.updateYoutubeVideoId(id, videoId)
    override suspend fun updateYoutubeOffset(id: String, offsetMs: Int) = dao.updateYoutubeOffset(id, offsetMs)
    override suspend fun updateYoutubeBlocklist(id: String, blocklist: List<String>) =
        dao.updateYoutubeBlocklist(id, blocklist.joinToString(","))
    override suspend fun updateVideoPoints(id: String, entries: List<VideoPointEntry>?) =
        dao.updateVideoPointsJson(id, SongEntity.encodeVideoPoints(entries))
}
