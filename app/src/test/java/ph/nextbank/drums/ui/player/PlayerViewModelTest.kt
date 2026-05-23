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
import ph.nextbank.drums.audio.playback.FakeYouTubeAdapter
import ph.nextbank.drums.audio.playback.YouTubeAdapter
import ph.nextbank.drums.audio.playback.YouTubeAdapterFactory
import ph.nextbank.drums.audio.youtube.FakeYouTubeSearchService
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.FakeSongRepository

private class FakeYouTubeAdapterFactory : YouTubeAdapterFactory {
    val createdAdapters = mutableListOf<FakeYouTubeAdapter>()
    val streamUrls = mutableListOf<String>()
    override fun create(streamUrl: String): YouTubeAdapter {
        streamUrls += streamUrl
        return FakeYouTubeAdapter().also { createdAdapters += it }
    }
}

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
        adapterFactory: YouTubeAdapterFactory = FakeYouTubeAdapterFactory(),
        songId: String = "test1",
    ) = PlayerViewModel(
        repo = repo,
        bank = FakeDrumSampleBank(),
        searchService = search,
        adapterFactory = adapterFactory,
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
    fun `acceptCandidate caches videoId and starts YouTube playback`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(SearchResult("abc12345678", "T", "U", 100, ""))
            audioUrls = mapOf("abc12345678" to "https://example.com/audio.m4a")
        }
        val factory = FakeYouTubeAdapterFactory()
        val vm = mkVm(repo, search, factory)
        advanceUntilIdle()
        vm.acceptCandidate()
        advanceUntilIdle()
        // VideoId cached + adapter constructed with the audio stream URL.
        assertEquals("abc12345678", repo.snapshot("test1")!!.youtubeVideoId)
        assertEquals(listOf("https://example.com/audio.m4a"), factory.streamUrls)
        // Phase stays YouTubeBuffering until the adapter reports onReady.
        val phase = vm.state.first().phase
        assertTrue(phase is PlayerPhase.YouTubeBuffering)
        assertEquals("abc12345678", (phase as PlayerPhase.YouTubeBuffering).videoId)

        // Simulate ExoPlayer reporting ready — should transition to YouTubeReady.
        factory.createdAdapters.first().simulateReady()
        advanceUntilIdle()
        assertTrue(vm.state.first().phase is PlayerPhase.YouTubeReady)
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
    fun `song with cached video skips search and starts YouTube playback`() = runTest {
        val seed = songWithoutVideo().copy(youtubeVideoId = "ccc33333333")
        val repo = FakeSongRepository().apply { seed(seed) }
        val search = FakeYouTubeSearchService().apply {
            audioUrls = mapOf("ccc33333333" to "https://example.com/audio.m4a")
        }
        val factory = FakeYouTubeAdapterFactory()
        val vm = mkVm(repo, search, factory)
        advanceUntilIdle()
        // Search not called; audio extraction WAS called; adapter built.
        assertEquals(null, search.lastQuery)
        assertEquals(listOf("https://example.com/audio.m4a"), factory.streamUrls)
        assertTrue(vm.state.first().phase is PlayerPhase.YouTubeBuffering)
    }

    @Test
    fun `audio URL extraction failure retries with next search result then falls back when exhausted`() = runTest {
        val seed = songWithoutVideo().copy(youtubeVideoId = "ccc33333333")
        val repo = FakeSongRepository().apply { seed(seed) }
        // Empty audioUrls + empty search queue → first extraction fails, retry searches for
        // a fresh candidate, search returns null, falls back to synth. The failed video IS
        // blocklisted so re-opening the song skips it.
        val search = FakeYouTubeSearchService()
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        assertEquals(PlayerPhase.SynthFallback, vm.state.first().phase)
        assertEquals(listOf("ccc33333333"), repo.snapshot("test1")!!.youtubeBlocklist)
    }

    @Test
    fun `extraction failures hit cap of 3 then fall back to synth`() = runTest {
        val seed = songWithoutVideo().copy(youtubeVideoId = "aaa11111111")
        val repo = FakeSongRepository().apply { seed(seed) }
        // All extractions fail (audioUrls is empty). The first failure consumes the cached
        // videoId; auto-retry searches up to 2 more candidates; on the 3rd extraction
        // failure the cap is hit and we fall back to synth without searching again.
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(
                SearchResult("bbb22222222", "Second", "U", 100, ""),
                SearchResult("ccc33333333", "Third", "U", 100, ""),
                SearchResult("ddd44444444", "Fourth", "U", 100, ""),
            )
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        assertEquals(PlayerPhase.SynthFallback, vm.state.first().phase)
        // Videos that failed and triggered a retry are blocklisted. The cap-hit video
        // (the 3rd attempt) is NOT blocklisted — we can't be sure NewPipe wouldn't have
        // succeeded on it given another chance, so we leave the door open.
        val blocklist = repo.snapshot("test1")!!.youtubeBlocklist
        assertEquals(listOf("aaa11111111", "bbb22222222"), blocklist)
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
