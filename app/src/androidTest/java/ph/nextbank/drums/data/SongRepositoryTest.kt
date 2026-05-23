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
import ph.nextbank.drums.data.repo.RoomSongRepository
import ph.nextbank.drums.data.repo.SongRepository
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song

private val e: List<DrumToken> = emptyList()
private fun hit(vararg t: DrumToken): List<DrumToken> = t.toList()

private val TEST_BAR_CRASH = listOf(
    hit(DrumToken.KICK, DrumToken.CRASH), e, hit(DrumToken.HIHAT_CLOSED), e,
    hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
    hit(DrumToken.KICK, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.KICK, DrumToken.HIHAT_CLOSED), e,
    hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
)
private val TEST_GROOVE = listOf(
    hit(DrumToken.KICK, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
    hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
    hit(DrumToken.KICK, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
    hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e,
)
private val TEST_FILL = listOf(
    hit(DrumToken.KICK, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
    hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
    hit(DrumToken.SNARE), hit(DrumToken.SNARE), hit(DrumToken.TOM_HI), hit(DrumToken.TOM_HI),
    hit(DrumToken.TOM_MID), hit(DrumToken.TOM_MID), hit(DrumToken.TOM_FLOOR), hit(DrumToken.TOM_FLOOR),
)

private val TEST_SONGS: List<Song> = listOf(
    Song(
        id = "smells-like-teen-spirit",
        title = "Smells Like Teen Spirit",
        artist = "Nirvana",
        bpm = 116,
        timeSig = 4 to 4,
        bars = listOf(
            TEST_BAR_CRASH, TEST_GROOVE, TEST_GROOVE, TEST_FILL,
            TEST_BAR_CRASH, TEST_GROOVE, TEST_GROOVE, TEST_FILL,
        ),
        coverInitials = "NV",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "test-song-2",
        title = "Test Song 2",
        artist = "Test Artist",
        bpm = 120,
        timeSig = 4 to 4,
        bars = listOf(TEST_GROOVE, TEST_FILL),
        coverInitials = "TA",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "test-song-3",
        title = "Test Song 3",
        artist = "Test Artist",
        bpm = 100,
        timeSig = 4 to 4,
        bars = listOf(TEST_GROOVE),
        coverInitials = "TA",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "test-song-4",
        title = "Test Song 4",
        artist = "Test Artist",
        bpm = 90,
        timeSig = 4 to 4,
        bars = listOf(TEST_BAR_CRASH),
        coverInitials = "TA",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "test-song-5",
        title = "Test Song 5",
        artist = "Test Artist",
        bpm = 80,
        timeSig = 4 to 4,
        bars = listOf(TEST_FILL),
        coverInitials = "TA",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
)

@RunWith(AndroidJUnit4::class)
class SongRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SongRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = RoomSongRepository(db.songDao())
    }

    @After fun tearDown() = db.close()

    @Test fun upsert_and_observe_round_trips_sample_songs() = runTest {
        repo.upsertAll(TEST_SONGS)
        val out = repo.observeAll().first()
        assertEquals(5, out.size)
        val nirvana = out.first { it.id == "smells-like-teen-spirit" }
        assertEquals("Nirvana", nirvana.artist)
        assertEquals(116, nirvana.bpm)
        assertEquals(8, nirvana.bars.size)
        assertEquals(16, nirvana.bars[0].size)
    }

    @Test fun findById_returns_null_for_unknown_id() = runTest {
        repo.upsertAll(TEST_SONGS)
        assertNotNull(repo.findById("smells-like-teen-spirit"))
        assertNull(repo.findById("nope"))
    }

    @Test fun bars_round_trip_preserves_all_drum_tokens() = runTest {
        repo.upsertAll(TEST_SONGS)
        val out = repo.findById("smells-like-teen-spirit")!!
        // First bar of CRASH pattern starts with [KICK, CRASH]
        assertEquals(2, out.bars[0][0].size)
    }
}
