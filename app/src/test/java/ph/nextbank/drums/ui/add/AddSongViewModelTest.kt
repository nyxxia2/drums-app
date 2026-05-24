package ph.nextbank.drums.ui.add

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.nextbank.drums.audio.songsterr.DrumTabParser
import ph.nextbank.drums.audio.songsterr.FakeSongsterrSearchService
import ph.nextbank.drums.audio.songsterr.FakeSongsterrTabFetcher
import ph.nextbank.drums.audio.songsterr.FakeSongsterrVideoPointsService
import ph.nextbank.drums.audio.songsterr.FetchResult
import ph.nextbank.drums.audio.songsterr.ParseResult
import ph.nextbank.drums.audio.songsterr.RevisionJson
import ph.nextbank.drums.audio.songsterr.SongsterrResult
import ph.nextbank.drums.audio.songsterr.SongsterrTrack
import ph.nextbank.drums.audio.songsterr.VideoPointEntry
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.repo.FakeSongRepository

class AddSongViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private val sampleResult = SongsterrResult(
        songId = 50420,
        artistId = 1583,
        artist = "Phil Collins",
        title = "In The Air Tonight",
        tracks = listOf(SongsterrTrack(1024, "Drums", "Drums", 1000, null, "drums_X")),
        popularTrackDrum = 0,
    )

    private val successResult = ParseResult.Success(
        bpm = 95,
        timeSig = 4 to 4,
        slotsPerBar = 16,
        bars = listOf(List(16) { idx -> if (idx == 0) listOf(DrumToken.KICK) else emptyList() }),
        warnings = emptyList(),
    )

    private fun mkVm(
        searchResults: List<SongsterrResult> = listOf(sampleResult),
        fetchResult: FetchResult = FetchResult.Success(RevisionJson(50420, 1, "drums_X", buildJsonObject {})),
        parseResult: ParseResult = successResult,
        pointsService: FakeSongsterrVideoPointsService = FakeSongsterrVideoPointsService(),
    ): Triple<AddSongViewModel, FakeSongRepository, FakeSongsterrTabFetcher> {
        val repo = FakeSongRepository()
        val fetcher = FakeSongsterrTabFetcher().apply { result = fetchResult }
        val search = FakeSongsterrSearchService().apply { results = searchResults }
        val parser = object : DrumTabParser {
            override fun parse(revision: RevisionJson): ParseResult = parseResult
        }
        val vm = AddSongViewModel(search, fetcher, parser, pointsService, repo)
        return Triple(vm, repo, fetcher)
    }

    @Test fun `debounced search populates results`() = runTest {
        val (vm, _, _) = mkVm()
        vm.onQueryChanged("in the air")
        advanceUntilIdle()
        assertEquals(1, vm.state.first().results.size)
    }

    @Test fun `clicking a result persists a Song with parsed bars`() = runTest {
        val (vm, repo, fetcher) = mkVm()
        vm.onResultClicked(sampleResult)
        advanceUntilIdle()
        assertEquals(50420L, fetcher.lastSongId)
        val snap = repo.allSnapshot()
        assertEquals(1, snap.size)
        val saved = snap.first()
        assertEquals("Phil Collins", saved.artist)
        assertEquals(95, saved.bpm)
        assertEquals(1, saved.bars.size)
        assertEquals(DrumToken.KICK, saved.bars[0][0].first())
    }

    @Test fun `NoDrumTrack from fetcher does not persist a song`() = runTest {
        val (vm, repo, _) = mkVm(fetchResult = FetchResult.NoDrumTrack)
        vm.onResultClicked(sampleResult)
        advanceUntilIdle()
        assertTrue(repo.allSnapshot().isEmpty())
        // ViewModel should emit a user-facing error/event
        val ev = vm.events.replayCache.firstOrNull() ?: vm.events.first()
        assertTrue(ev is AddSongEvent.Toast)
        assertTrue("toast: ${(ev as AddSongEvent.Toast).message}", ev.message.contains("drum tab", ignoreCase = true))
    }

    @Test fun `NetworkError from fetcher surfaces a connection toast`() = runTest {
        val (vm, repo, _) = mkVm(fetchResult = FetchResult.NetworkError)
        vm.onResultClicked(sampleResult)
        advanceUntilIdle()
        assertTrue(repo.allSnapshot().isEmpty())
        val ev = vm.events.replayCache.firstOrNull() ?: vm.events.first()
        assertTrue(ev is AddSongEvent.Toast)
        assertTrue("toast: ${(ev as AddSongEvent.Toast).message}", ev.message.contains("connection", ignoreCase = true))
    }

    @Test fun `ParseError surfaces a generic parse toast`() = runTest {
        val (vm, repo, _) = mkVm(parseResult = ParseResult.ParseError("bad shape"))
        vm.onResultClicked(sampleResult)
        advanceUntilIdle()
        assertTrue(repo.allSnapshot().isEmpty())
        val ev = vm.events.replayCache.firstOrNull() ?: vm.events.first()
        assertTrue(ev is AddSongEvent.Toast)
    }

    @Test fun `synced add persists videoPoints on the new song`() = runTest {
        val sampleEntries = listOf(
            VideoPointEntry(
                youtubeVideoId = "abc12345678",
                points = listOf(0.0, 2.0, 4.0),
                feature = "alternative",
            ),
            VideoPointEntry(
                youtubeVideoId = "def12345678",
                points = listOf(1.0, 3.0, 5.0),
                feature = null,
            ),
        )
        val fakePoints = FakeSongsterrVideoPointsService(sampleEntries)
        val (vm, repo, _) = mkVm(pointsService = fakePoints)

        vm.onResultClicked(sampleResult)
        advanceUntilIdle()

        val saved = repo.allSnapshot().first()
        assertEquals(2, saved.videoPoints?.size)
        assertEquals("abc12345678", saved.videoPoints!![0].youtubeVideoId)
        assertEquals(50420L, fakePoints.lastSongId)
    }

    @Test fun `unsynced add persists null videoPoints`() = runTest {
        val fakePoints = FakeSongsterrVideoPointsService(emptyList())
        val (vm, repo, _) = mkVm(pointsService = fakePoints)

        vm.onResultClicked(sampleResult)
        advanceUntilIdle()

        val saved = repo.allSnapshot().first()
        assertEquals(null, saved.videoPoints)
    }
}
