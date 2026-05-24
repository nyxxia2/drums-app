package ph.nextbank.drums.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY (lastPlayedEpochMs IS NULL) ASC, lastPlayedEpochMs DESC, title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun findById(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    /** Insert songs that don't already exist by id. Used for the bundled-song seed so user-set fields (cached youtubeVideoId, youtubeOffsetMs, blocklist) survive across cold starts. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedNew(songs: List<SongEntity>)

    @Update
    suspend fun update(song: SongEntity)

    @Query("UPDATE songs SET bpm = :bpm WHERE id = :id")
    suspend fun updateBpm(id: String, bpm: Int)

    @Query("UPDATE songs SET youtubeVideoId = :videoId WHERE id = :id")
    suspend fun updateYoutubeVideoId(id: String, videoId: String?)

    @Query("UPDATE songs SET youtubeOffsetMs = :offsetMs WHERE id = :id")
    suspend fun updateYoutubeOffset(id: String, offsetMs: Int)

    @Query("UPDATE songs SET youtubeBlocklist = :blocklist WHERE id = :id")
    suspend fun updateYoutubeBlocklist(id: String, blocklist: String)

    @Query("UPDATE songs SET videoPointsJson = :json WHERE id = :id")
    suspend fun updateVideoPointsJson(id: String, json: String?)
}
