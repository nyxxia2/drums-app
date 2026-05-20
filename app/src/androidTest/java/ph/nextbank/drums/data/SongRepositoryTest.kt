package ph.nextbank.drums.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ph.nextbank.drums.data.db.AppDatabase
import ph.nextbank.drums.data.repo.SongRepository
import ph.nextbank.drums.data.samples.SAMPLE_SONGS

@RunWith(AndroidJUnit4::class)
class SongRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SongRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = SongRepository(db.songDao())
    }

    @After fun tearDown() = db.close()

    @Test fun upsert_and_observe_round_trips_sample_songs() = runTest {
        repo.upsertAll(SAMPLE_SONGS)
        val out = repo.observeAll().first()
        assertEquals(5, out.size)
        val nirvana = out.first { it.id == "smells-like-teen-spirit" }
        assertEquals("Nirvana", nirvana.artist)
        assertEquals(116, nirvana.bpm)
        assertEquals(8, nirvana.bars.size)
        assertEquals(16, nirvana.bars[0].size)
    }

    @Test fun findById_returns_null_for_unknown_id() = runTest {
        repo.upsertAll(SAMPLE_SONGS)
        assertNotNull(repo.findById("smells-like-teen-spirit"))
        assertNull(repo.findById("nope"))
    }

    @Test fun bars_round_trip_preserves_all_drum_tokens() = runTest {
        repo.upsertAll(SAMPLE_SONGS)
        val out = repo.findById("smells-like-teen-spirit")!!
        // First bar of CRASH pattern starts with [KICK, CRASH]
        assertEquals(2, out.bars[0][0].size)
    }
}
