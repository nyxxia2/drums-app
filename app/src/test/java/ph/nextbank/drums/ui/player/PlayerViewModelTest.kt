package ph.nextbank.drums.ui.player

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.nextbank.drums.audio.FakeDrumSampleBank
import ph.nextbank.drums.audio.youtube.FakeYouTubeSearchService
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.FakeSongRepository

class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() { Dispatchers.setMain(dispatcher) }

    @After
    fun teardown() { Dispatchers.resetMain() }

    private fun songWithoutVideo(
        id: String = "test1",
        blocklist: List<String> = emptyList(),
    ) = Song(
        id = id,
        title = "Test Song",
        artist = "Tester",
        bpm = 120,
        timeSig = 4 to 4,
        bars = listOf(List(16) { emptyList<DrumToken>() }),
        coverInitials = "TT",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
        youtubeBlocklist = blocklist,
    )

    private fun mkVm(
        repo: FakeSongRepository,
        search: FakeYouTubeSearchService,
        songId: String = "test1",
    ) = PlayerViewModel(
        repo = repo,
        bank = FakeDrumSampleBank(),
        searchService = search,
        handle = SavedStateHandle(mapOf("songId" to songId)),
    )

    @Test
    fun `song without cached video triggers search`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(SearchResult("abc12345678", "Test Song - Tester", "Tester VEVO", 200, "thumb"))
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        val phase = vm.state.first().phase
        assertTrue(phase is PlayerPhase.Confirming)
        assertEquals("abc12345678", (phase as PlayerPhase.Confirming).candidate.videoId)
        assertEquals("Test Song Tester", search.lastQuery)
    }

    @Test
    fun `acceptCandidate caches videoId and moves to YouTubeBuffering`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(SearchResult("abc12345678", "T", "U", 100, ""))
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        vm.acceptCandidate()
        advanceUntilIdle()
        val phase = vm.state.first().phase
        assertTrue(phase is PlayerPhase.YouTubeBuffering)
        assertEquals("abc12345678", (phase as PlayerPhase.YouTubeBuffering).videoId)
        assertEquals("abc12345678", repo.snapshot("test1")!!.youtubeVideoId)
    }

    @Test
    fun `tryAnotherVideo blocklists current candidate and searches again`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(
                SearchResult("aaa11111111", "First", "U", 100, ""),
                SearchResult("bbb22222222", "Second", "U", 100, ""),
            )
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        vm.tryAnotherVideo()
        advanceUntilIdle()
        val phase = vm.state.first().phase
        assertTrue(phase is PlayerPhase.Confirming)
        assertEquals("bbb22222222", (phase as PlayerPhase.Confirming).candidate.videoId)
        assertTrue("aaa11111111" in repo.snapshot("test1")!!.youtubeBlocklist)
    }

    @Test
    fun `empty search result falls back to synth`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply { shouldReturnNull = true }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        assertEquals(PlayerPhase.SynthFallback, vm.state.first().phase)
    }

    @Test
    fun `dismissConfirmation falls back to synth`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(SearchResult("abc12345678", "T", "U", 100, ""))
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        vm.dismissConfirmation()
        advanceUntilIdle()
        assertEquals(PlayerPhase.SynthFallback, vm.state.first().phase)
    }

    @Test
    fun `song with cached video skips search and goes to YouTubeBuffering`() = runTest {
        val seed = songWithoutVideo().copy(youtubeVideoId = "ccc33333333")
        val repo = FakeSongRepository().apply { seed(seed) }
        val search = FakeYouTubeSearchService()
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        val phase = vm.state.first().phase
        assertTrue(phase is PlayerPhase.YouTubeBuffering)
        assertEquals("ccc33333333", (phase as PlayerPhase.YouTubeBuffering).videoId)
        assertEquals(null, search.lastQuery)
    }

    @Test
    fun `nudgeOffset persists to repo`() = runTest {
        val seed = songWithoutVideo().copy(youtubeVideoId = "ccc33333333", youtubeOffsetMs = 50)
        val repo = FakeSongRepository().apply { seed(seed) }
        val search = FakeYouTubeSearchService()
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        vm.nudgeOffset(50)
        advanceUntilIdle()
        assertEquals(100, repo.snapshot("test1")!!.youtubeOffsetMs)
    }
}
