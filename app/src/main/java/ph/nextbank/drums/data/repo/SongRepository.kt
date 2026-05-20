package ph.nextbank.drums.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.nextbank.drums.data.db.SongDao
import ph.nextbank.drums.data.db.SongEntity
import ph.nextbank.drums.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(private val dao: SongDao) {
    fun observeAll(): Flow<List<Song>> = dao.observeAll().map { list -> list.map(SongEntity::toSong) }
    suspend fun findById(id: String): Song? = dao.findById(id)?.toSong()
    suspend fun upsertAll(songs: List<Song>) = dao.insertAll(songs.map(SongEntity::fromSong))
    suspend fun updateBpm(id: String, bpm: Int) = dao.updateBpm(id, bpm)
}
