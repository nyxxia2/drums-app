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
import ph.nextbank.drums.audio.songsterr.VideoPointEntry
import ph.nextbank.drums.audio.youtube.FakeYouTubeSearchService
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.audio.youtube.YouTubeCandidateResolver
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.FakeSongRepository

private class FakeYouTubeAdapterFactory : YouTubeAdapterFactory {
    val createdAdapters = mutableListOf<FakeYouTubeAdapter>()
    val streamUrls = mutableListOf<String>()
    var lastListener: ph.nextbank.drums.audio.playback.YouTubeAdapterListener? = null
    override fun create(streamUrl: String): YouTubeAdapter {
        streamUrls += streamUrl
        val adapter = object : FakeYouTubeAdapter() {
            override fun setListener(listener: ph.nextbank.drums.audio.playback.YouTubeAdapterListener) {
                super.setListener(listener)
                lastListener = listener
            }
        }
        createdAdapters += adapter
        return adapter
    }
}

class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private fun songWithoutVideo(
        id: String = "test1",
        blocklist: List<String> = emptyList(),
        videoPoints: List<VideoPointEntry>? = null,
        bpm: Int = 120,
        totalBars: Int = 1,
    ) = Song(
        id = id,
        title = "Test Song",
        artist = "Tester",
        bpm = bpm,
        timeSig = 4 to 4,
        bars = List(totalBars) { List(16) { emptyList<DrumToken>() } },
        coverInitials = "TT",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
        youtubeBlocklist = blocklist,
        videoPoints = videoPoints,
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
        resolver = YouTubeCandidateResolver(search),
        adapterFactory = adapterFactory,
        handle = SavedStateHandle(mapOf("songId" to songId)),
    )

    @Test
    fun `song without cached video shows candidate list`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(
                SearchResult("abc12345678", "Test Song - Tester", "Tester", 200, "thumb"),
                SearchResult("def00000000", "Test Song", "Other", 200, "thumb"),
            )
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        val phase = vm.state.first().phase
        assertTrue(phase is PlayerPhase.Confirming)
        assertEquals(
            listOf("abc12345678", "def00000000"),
            (phase as PlayerPhase.Confirming).candidates.map { it.videoId },
        )
        assertEquals("Test Song Tester", search.lastQuery)
    }

    @Test
    fun `acceptCandidate caches videoId and starts YouTube playback`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val pick = SearchResult("abc12345678", "T", "U", 100, "")
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(pick)
            audioUrls = mapOf("abc12345678" to "https://example.com/audio.m4a")
        }
        val factory = FakeYouTubeAdapterFactory()
        val vm = mkVm(repo, search, factory)
        advanceUntilIdle()
        vm.acceptCandidate(pick)
        advanceUntilIdle()
        assertEquals("abc12345678", repo.snapshot("test1")!!.youtubeVideoId)
        assertEquals(listOf("https://example.com/audio.m4a"), factory.streamUrls)
        val phase = vm.state.first().phase
        assertTrue(phase is PlayerPhase.YouTubeBuffering)
        assertEquals("abc12345678", (phase as PlayerPhase.YouTubeBuffering).videoId)

        factory.createdAdapters.first().simulateReady()
        advanceUntilIdle()
        assertTrue(vm.state.first().phase is PlayerPhase.YouTubeReady)
    }

    @Test
    fun `user can pick any candidate from the list`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val second = SearchResult("bbb22222222", "Second", "U", 100, "")
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(
                SearchResult("aaa11111111", "First", "U", 100, ""),
                second,
            )
            audioUrls = mapOf("bbb22222222" to "stream-url")
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        // List is shown; user picks the second one directly.
        vm.acceptCandidate(second)
        advanceUntilIdle()
        assertEquals("bbb22222222", repo.snapshot("test1")!!.youtubeVideoId)
    }

    @Test
    fun `tryAnotherVideo from playback blocklists current and reopens list`() = runTest {
        val repo = FakeSongRepository().apply {
            seed(songWithoutVideo().copy(youtubeVideoId = "aaa11111111"))
        }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(
                SearchResult("bbb22222222", "Second", "U", 100, ""),
                SearchResult("ccc33333333", "Third", "U", 100, ""),
            )
            audioUrls = mapOf("aaa11111111" to "stream-url-a")
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        // Cached video started buffering.
        assertTrue(vm.state.first().phase is PlayerPhase.YouTubeBuffering)
        vm.tryAnotherVideo()
        advanceUntilIdle()
        // Current videoId blocklisted, dialog reopens with the fresh search results.
        val phase = vm.state.first().phase
        assertTrue("expected Confirming, got $phase", phase is PlayerPhase.Confirming)
        val ids = (phase as PlayerPhase.Confirming).candidates.map { it.videoId }
        assertEquals(listOf("bbb22222222", "ccc33333333"), ids)
        assertTrue("aaa11111111" in repo.snapshot("test1")!!.youtubeBlocklist)
        assertEquals(null, repo.snapshot("test1")!!.youtubeVideoId)
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
        assertEquals(null, search.lastQuery)
        assertEquals(listOf("https://example.com/audio.m4a"), factory.streamUrls)
        assertTrue(vm.state.first().phase is PlayerPhase.YouTubeBuffering)
    }

    @Test
    fun `audio URL extraction failure triggers reopen with blocklist updated`() = runTest {
        val seed = songWithoutVideo().copy(youtubeVideoId = "ccc33333333")
        val repo = FakeSongRepository().apply { seed(seed) }
        // Empty audioUrls + empty search queue → first extraction fails, reopens with blocklist,
        // resolver returns empty, falls back to synth. Failed videoId stays blocklisted.
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
        // All extractions fail. First failure consumes the cached id; the candidate list from
        // a fresh search has two more options; after the 3rd extraction failure we hit the cap.
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(
                SearchResult("bbb22222222", "Second", "U", 100, ""),
                SearchResult("ccc33333333", "Third", "U", 100, ""),
                SearchResult("ddd44444444", "Fourth", "U", 100, ""),
            )
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        // The cached id failed; the dialog reopens with the fresh results.
        val phase = vm.state.first().phase
        assertTrue("expected Confirming after first extraction failure, got $phase", phase is PlayerPhase.Confirming)
        // Simulate accepting each candidate until cap is hit.
        val candidates = (phase as PlayerPhase.Confirming).candidates
        vm.acceptCandidate(candidates[0])
        advanceUntilIdle()
        val phase2 = vm.state.first().phase
        assertTrue("second extraction failure should also reopen, got $phase2", phase2 is PlayerPhase.Confirming)
        val candidates2 = (phase2 as PlayerPhase.Confirming).candidates
        vm.acceptCandidate(candidates2.first())
        advanceUntilIdle()
        // Third extraction failure hits the cap; switch to synth.
        assertEquals(PlayerPhase.SynthFallback, vm.state.first().phase)
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

    @Test
    fun `synced song shows Confirming with all entries without searching`() = runTest {
        // Both entries use the same feature, so input order is preserved by the stable sort.
        val entries = listOf(
            VideoPointEntry("syncedAbc12", listOf(0.0, 2.0, 4.0, 6.0), null),
            VideoPointEntry("syncedDef34", listOf(0.0, 2.0, 4.0, 6.0), null),
        )
        val song = songWithoutVideo(id = "song1", videoPoints = entries)
        val repo = FakeSongRepository().apply { seed(song) }
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf(
                "syncedAbc12" to SearchResult("syncedAbc12", "Synced 1", "Channel", 240, ""),
                "syncedDef34" to SearchResult("syncedDef34", "Synced 2", "Channel", 240, ""),
            )
        }
        val vm = mkVm(repo, search, songId = "song1")
        advanceUntilIdle()

        val phase = vm.state.value.phase
        assertTrue("expected Confirming, got $phase", phase is PlayerPhase.Confirming)
        assertEquals(
            listOf("syncedAbc12", "syncedDef34"),
            (phase as PlayerPhase.Confirming).candidates.map { it.videoId },
        )
        assertEquals(0, search.findForCalls)  // synced path doesn't search.
    }

    @Test
    fun `synced song demotes backing and karaoke entries`() = runTest {
        val entries = listOf(
            VideoPointEntry("karaokeId01", listOf(0.0), "alternative"),
            VideoPointEntry("backingId01", listOf(0.0), "backing"),
            VideoPointEntry("officialId1", listOf(0.0), null),
        )
        val song = songWithoutVideo(id = "song1", videoPoints = entries)
        val repo = FakeSongRepository().apply { seed(song) }
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf(
                "karaokeId01" to SearchResult("karaokeId01", "Title - Karaoke Version", "K", 200, ""),
                "backingId01" to SearchResult("backingId01", "Title (Backing Track)", "B", 200, ""),
                "officialId1" to SearchResult("officialId1", "Title (Official)", "O", 200, ""),
            )
        }
        val vm = mkVm(repo, search, songId = "song1")
        advanceUntilIdle()

        val phase = vm.state.value.phase
        assertTrue(phase is PlayerPhase.Confirming)
        val ids = (phase as PlayerPhase.Confirming).candidates.map { it.videoId }
        // officialId1 (null feature, no bad keywords) ranks best; karaokeId01 ranks worst.
        assertEquals("officialId1", ids.first())
        assertEquals("backingId01", ids.last().also { _ -> })  // sanity: backing should be last or near-last
        // Karaoke (alternative + karaoke title) should not be first.
        assertTrue("karaoke should not lead", ids.indexOf("karaokeId01") > 0)
    }

    @Test
    fun `accepting a synced candidate uses PointsBasedTimeMap`() = runTest {
        val entries = listOf(
            VideoPointEntry(
                youtubeVideoId = "syncedV1234",
                points = listOf(0.0, 2.0, 4.0, 6.0),
                feature = null,
            ),
        )
        val song = songWithoutVideo(
            id = "song4",
            videoPoints = entries,
            bpm = 60,
            totalBars = 4,
        )
        val repo = FakeSongRepository().apply { seed(song) }
        val syncedMeta = SearchResult("syncedV1234", "Synced", "Ch", 240, "")
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf("syncedV1234" to syncedMeta)
            audioUrls = mapOf("syncedV1234" to "fake-stream-url")
        }
        val factory = FakeYouTubeAdapterFactory()
        val vm = mkVm(repo, search, adapterFactory = factory, songId = "song4")
        advanceUntilIdle()
        vm.acceptCandidate(syncedMeta)
        advanceUntilIdle()

        factory.lastListener?.onCurrentSecond(1.0f)
        advanceUntilIdle()
        assertEquals(8, vm.state.value.activeSlotIndex)
    }
}
