# Resolve YouTube at Add Time — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move YouTube video selection from the first player open into the Add Song flow, so opening a freshly-added song plays immediately with no confirmation dialog.

**Architecture:** Extract YouTube candidate picking into a new `YouTubeCandidateResolver` helper. `AddSongViewModel` calls the resolver after the tab + points fetch, surfaces the candidate to the UI via a `pendingConfirm` state, and reuses the existing `YouTubeConfirmDialog`. The user must accept to persist; dismiss cancels the add. `PlayerViewModel`'s unsynced search delegates to the same resolver. The synced-cycling code (with its index state) stays put because it doesn't fit a stateless resolver.

**Tech Stack:** Kotlin 2.0, Jetpack Compose, Hilt (constructor-injected singletons), kotlinx.coroutines test, JUnit 4, Room (unchanged).

**Spec:** `docs/superpowers/specs/2026-05-24-resolve-youtube-at-add-time-design.md`

---

## File Structure

**Create:**
- `app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeCandidateResolver.kt`
- `app/src/test/java/ph/nextbank/drums/audio/youtube/YouTubeCandidateResolverTest.kt`

**Modify:**
- `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt` — add `PendingConfirm`, defer persistence, call resolver
- `app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt` — render dialog when `pendingConfirm != null`
- `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt` — `runSearch` delegates to resolver (constructor gains one param)
- `app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt` — add `pendingConfirm` tests; update existing happy-path assertions
- `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt` — pass resolver through `mkVm`

**Unchanged (read-only references):**
- `app/src/main/java/ph/nextbank/drums/ui/player/YouTubeConfirmDialog.kt`
- `app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeSearchService.kt`
- `app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt` and DAO — no schema change

---

## Task 1: Create `YouTubeCandidateResolver` with unit tests

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeCandidateResolver.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/youtube/YouTubeCandidateResolverTest.kt`

The resolver wraps `YouTubeSearchService` so both `AddSongViewModel` and `PlayerViewModel.runSearch` can call one place to pick a candidate. Synced songs walk `videoPoints` entries in order, skipping blocklisted IDs and skipping entries whose `fetchMeta` returns null. Unsynced songs fall back to `findFor`.

- [ ] **Step 1: Write the failing test file**

`app/src/test/java/ph/nextbank/drums/audio/youtube/YouTubeCandidateResolverTest.kt`:

```kotlin
package ph.nextbank.drums.audio.youtube

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ph.nextbank.drums.audio.songsterr.VideoPointEntry

class YouTubeCandidateResolverTest {

    private fun meta(id: String) = SearchResult(
        videoId = id,
        title = "Title $id",
        channelTitle = "Channel",
        durationSec = 200,
        thumbnailUrl = "thumb",
    )

    @Test
    fun `synced - first entry meta resolves - returns it`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf("aaaa1111111" to meta("aaaa1111111"))
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(VideoPointEntry("aaaa1111111", listOf(0.0, 2.0), null))

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet())

        assertEquals("aaaa1111111", out?.videoId)
    }

    @Test
    fun `synced - first entry meta null - falls through to second`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf("bbbb2222222" to meta("bbbb2222222"))
            // "aaaa1111111" intentionally missing => fetchMeta returns null
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
        )

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet())

        assertEquals("bbbb2222222", out?.videoId)
    }

    @Test
    fun `synced - all metas null - returns null`() = runTest {
        val search = FakeYouTubeSearchService()  // metaResults empty
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
        )

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet())

        assertNull(out)
    }

    @Test
    fun `synced - blocklisted entries are skipped`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf(
                "aaaa1111111" to meta("aaaa1111111"),
                "bbbb2222222" to meta("bbbb2222222"),
            )
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
        )

        val out = resolver.resolveInitial("T", "A", entries, blocklist = setOf("aaaa1111111"))

        assertEquals("bbbb2222222", out?.videoId)
    }

    @Test
    fun `unsynced - delegates to findFor`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(meta("xxxx9999999"))
        }
        val resolver = YouTubeCandidateResolver(search)

        val out = resolver.resolveInitial("Song", "Artist", videoPoints = null, blocklist = emptySet())

        assertEquals("xxxx9999999", out?.videoId)
        assertEquals("Song Artist", search.lastQuery)
    }

    @Test
    fun `unsynced - empty videoPoints list also routes to findFor`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(meta("yyyy8888888"))
        }
        val resolver = YouTubeCandidateResolver(search)

        val out = resolver.resolveInitial("S", "A", videoPoints = emptyList(), blocklist = emptySet())

        assertEquals("yyyy8888888", out?.videoId)
        assertEquals(1, search.findForCalls)
    }

    @Test
    fun `unsynced - search returns null - resolver returns null`() = runTest {
        val search = FakeYouTubeSearchService().apply { shouldReturnNull = true }
        val resolver = YouTubeCandidateResolver(search)

        val out = resolver.resolveInitial("S", "A", videoPoints = null, blocklist = emptySet())

        assertNull(out)
    }

    @Test
    fun `unsynced - passes blocklist through to findFor`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(meta("yyyy8888888"))
        }
        val resolver = YouTubeCandidateResolver(search)

        resolver.resolveInitial("S", "A", videoPoints = null, blocklist = setOf("zz"))

        assertEquals(setOf("zz"), search.lastBlocklist)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests ph.nextbank.drums.audio.youtube.YouTubeCandidateResolverTest`
Expected: FAIL with "Unresolved reference: YouTubeCandidateResolver".

- [ ] **Step 3: Write the resolver**

`app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeCandidateResolver.kt`:

```kotlin
package ph.nextbank.drums.audio.youtube

import ph.nextbank.drums.audio.songsterr.VideoPointEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks an initial YouTube candidate for a song.
 *
 * Synced songs (videoPoints non-empty) walk the entries in order, skipping IDs in
 * [blocklist] and skipping entries whose metadata fetch returns null. Unsynced
 * songs (videoPoints null or empty) delegate to [YouTubeSearchService.findFor].
 *
 * Returns null when no candidate can be resolved.
 */
@Singleton
class YouTubeCandidateResolver @Inject constructor(
    private val searchService: YouTubeSearchService,
) {
    suspend fun resolveInitial(
        title: String,
        artist: String,
        videoPoints: List<VideoPointEntry>?,
        blocklist: Set<String> = emptySet(),
    ): SearchResult? {
        if (!videoPoints.isNullOrEmpty()) {
            for (entry in videoPoints) {
                if (entry.youtubeVideoId in blocklist) continue
                val meta = searchService.fetchMeta(entry.youtubeVideoId)
                if (meta != null) return meta
            }
            return null
        }
        return searchService.findFor("$title $artist", blocklist)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests ph.nextbank.drums.audio.youtube.YouTubeCandidateResolverTest`
Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeCandidateResolver.kt \
        app/src/test/java/ph/nextbank/drums/audio/youtube/YouTubeCandidateResolverTest.kt
git commit -m "Add YouTubeCandidateResolver for picking initial YouTube candidates

Wraps YouTubeSearchService with the synced-then-unsynced fallback logic
that currently lives inline in PlayerViewModel. Will be reused by the
Add Song flow so YouTube selection can happen at add time instead of
on first player open.
"
```

---

## Task 2: Use the resolver in `PlayerViewModel.runSearch`

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`
- Modify: `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`

Minimal refactor — `runSearch` is the unsynced search path and matches the resolver's signature exactly. The synced cycling in `showSyncedCandidate` keeps its inline logic because it needs the entry index for the player's "Try another video" state. Existing tests are the safety net; no behavior change.

- [ ] **Step 1: Update PlayerViewModelTest's `mkVm` to thread a resolver through**

In `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`, find the `mkVm` helper (around line 76) and update it:

```kotlin
import ph.nextbank.drums.audio.youtube.YouTubeCandidateResolver

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
```

- [ ] **Step 2: Run player tests to verify they fail with "no such parameter: resolver"**

Run: `./gradlew :app:testDebugUnitTest --tests ph.nextbank.drums.ui.player.PlayerViewModelTest`
Expected: COMPILE FAIL — `PlayerViewModel` doesn't have a `resolver` parameter yet.

- [ ] **Step 3: Add `resolver` to `PlayerViewModel`'s constructor and use it in `runSearch`**

In `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`:

Add the import at the top:

```kotlin
import ph.nextbank.drums.audio.youtube.YouTubeCandidateResolver
```

Update the constructor to add `resolver`:

```kotlin
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: SongRepository,
    private val bank: DrumSampleBankApi,
    private val searchService: YouTubeSearchService,
    private val resolver: YouTubeCandidateResolver,
    private val adapterFactory: YouTubeAdapterFactory,
    handle: SavedStateHandle,
) : ViewModel() {
```

Replace the body of `runSearch`. Find the current implementation (around line 114-132) and change the candidate-fetch line:

```kotlin
private suspend fun runSearch(song: Song, blocklist: Set<String>, autoAccept: Boolean = false) {
    _state.value = _state.value.copy(phase = PlayerPhase.Searching)
    val result = resolver.resolveInitial(
        title = song.title,
        artist = song.artist,
        videoPoints = null,
        blocklist = blocklist,
    )
    if (result == null) {
        _events.tryEmit(PlayerEvent.Toast("No playable YouTube result — playing synth drums"))
        switchToSynth()
    } else if (autoAccept) {
        repo.updateYoutubeVideoId(songId, result.videoId)
        _state.value = _state.value.copy(
            song = _state.value.song?.copy(youtubeVideoId = result.videoId),
        )
        startYouTubePlayback(result.videoId)
    } else {
        _state.value = _state.value.copy(phase = PlayerPhase.Confirming(result))
    }
}
```

Note: we pass `videoPoints = null` here because `runSearch` is only called from the *unsynced* path (or as a fallback once the synced list is exhausted). `showSyncedCandidate` continues to handle synced songs directly because of its index-tracking requirement.

- [ ] **Step 4: Run all player tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests ph.nextbank.drums.ui.player.PlayerViewModelTest`
Expected: All existing player tests pass (behavior preserved).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt \
        app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt
git commit -m "PlayerViewModel: delegate unsynced search to YouTubeCandidateResolver

Pure refactor — runSearch's findFor call moves behind the resolver.
showSyncedCandidate keeps its inline logic because it needs to track
which entry index it landed on for the Try-another-video cycling.
"
```

---

## Task 3: Pending-confirm flow in `AddSongViewModel`

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt`
- Modify: `app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt`

Restructure the Add flow so that after a successful tab + points fetch, a YouTube candidate is resolved, surfaced via `pendingConfirm`, and only persisted on `confirmPendingAdd()`. Dismiss cancels without writing.

- [ ] **Step 1: Update existing happy-path tests so they call `confirmPendingAdd()` before asserting persistence**

In `app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt`, add imports:

```kotlin
import ph.nextbank.drums.audio.youtube.FakeYouTubeSearchService
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.audio.youtube.YouTubeCandidateResolver
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
```

Update `mkVm` to accept and construct a resolver, with a default YouTube search fake that returns one result:

```kotlin
private fun mkVm(
    searchResults: List<SongsterrResult> = listOf(sampleResult),
    fetchResult: FetchResult = FetchResult.Success(RevisionJson(50420, 1, "drums_X", buildJsonObject {})),
    parseResult: ParseResult = successResult,
    pointsService: FakeSongsterrVideoPointsService = FakeSongsterrVideoPointsService(),
    youtubeSearch: FakeYouTubeSearchService = FakeYouTubeSearchService().apply {
        queue = listOf(
            SearchResult(
                videoId = "yt00aaaaaaa",
                title = "Default match",
                channelTitle = "Default channel",
                durationSec = 200,
                thumbnailUrl = "thumb",
            ),
        )
    },
): Quad<AddSongViewModel, FakeSongRepository, FakeSongsterrTabFetcher, FakeYouTubeSearchService> {
    val repo = FakeSongRepository()
    val fetcher = FakeSongsterrTabFetcher().apply { result = fetchResult }
    val search = FakeSongsterrSearchService().apply { results = searchResults }
    val parser = object : DrumTabParser {
        override fun parse(revision: RevisionJson): ParseResult = parseResult
    }
    val resolver = YouTubeCandidateResolver(youtubeSearch)
    val vm = AddSongViewModel(search, fetcher, parser, pointsService, resolver, repo)
    return Quad(vm, repo, fetcher, youtubeSearch)
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
```

Update the existing `clicking a result persists a Song with parsed bars` test to confirm before asserting:

```kotlin
@Test fun `clicking a result then confirming persists a Song with parsed bars`() = runTest {
    val (vm, repo, fetcher, _) = mkVm()
    vm.onResultClicked(sampleResult)
    advanceUntilIdle()
    // Resolver picked the default search hit; song should be pending, not yet persisted.
    assertTrue(repo.allSnapshot().isEmpty())
    val pending = vm.state.first().pendingConfirm
    assertNotNull(pending)
    assertEquals("yt00aaaaaaa", pending!!.candidate.videoId)
    vm.confirmPendingAdd()
    advanceUntilIdle()
    assertEquals(50420L, fetcher.lastSongId)
    val snap = repo.allSnapshot()
    assertEquals(1, snap.size)
    val saved = snap.first()
    assertEquals("Phil Collins", saved.artist)
    assertEquals(95, saved.bpm)
    assertEquals(1, saved.bars.size)
    assertEquals(DrumToken.KICK, saved.bars[0][0].first())
    assertEquals("yt00aaaaaaa", saved.youtubeVideoId)
}
```

Update the existing `synced add persists videoPoints on the new song` test to use confirm:

```kotlin
@Test fun `synced add persists videoPoints and uses first entry's video id`() = runTest {
    val sampleEntries = listOf(
        VideoPointEntry("abc12345678", listOf(0.0, 2.0, 4.0), "alternative"),
        VideoPointEntry("def12345678", listOf(1.0, 3.0, 5.0), null),
    )
    val fakePoints = FakeSongsterrVideoPointsService(sampleEntries)
    val youtubeSearch = FakeYouTubeSearchService().apply {
        metaResults = mapOf(
            "abc12345678" to SearchResult("abc12345678", "Synced 1", "C", 200, "t"),
        )
    }
    val (vm, repo, _, _) = mkVm(pointsService = fakePoints, youtubeSearch = youtubeSearch)

    vm.onResultClicked(sampleResult)
    advanceUntilIdle()
    vm.confirmPendingAdd()
    advanceUntilIdle()

    val saved = repo.allSnapshot().first()
    assertEquals(2, saved.videoPoints?.size)
    assertEquals("abc12345678", saved.videoPoints!![0].youtubeVideoId)
    assertEquals("abc12345678", saved.youtubeVideoId)
    assertEquals(50420L, fakePoints.lastSongId)
}
```

Update `unsynced add persists null videoPoints`:

```kotlin
@Test fun `unsynced add persists null videoPoints`() = runTest {
    val fakePoints = FakeSongsterrVideoPointsService(emptyList())
    val (vm, repo, _, _) = mkVm(pointsService = fakePoints)

    vm.onResultClicked(sampleResult)
    advanceUntilIdle()
    vm.confirmPendingAdd()
    advanceUntilIdle()

    val saved = repo.allSnapshot().first()
    assertEquals(null, saved.videoPoints)
}
```

Now add the new tests:

```kotlin
@Test fun `successful add surfaces pendingConfirm with isAdding still true`() = runTest {
    val (vm, repo, _, _) = mkVm()
    vm.onResultClicked(sampleResult)
    advanceUntilIdle()

    val s = vm.state.first()
    assertTrue(s.isAdding)
    assertNotNull(s.pendingConfirm)
    assertEquals("yt00aaaaaaa", s.pendingConfirm!!.candidate.videoId)
    assertTrue(repo.allSnapshot().isEmpty())
}

@Test fun `dismissPendingAdd clears state without persisting`() = runTest {
    val (vm, repo, _, _) = mkVm()
    vm.onResultClicked(sampleResult)
    advanceUntilIdle()
    assertNotNull(vm.state.first().pendingConfirm)

    vm.dismissPendingAdd()
    advanceUntilIdle()

    val s = vm.state.first()
    assertNull(s.pendingConfirm)
    assertEquals(false, s.isAdding)
    assertTrue(repo.allSnapshot().isEmpty())
}

@Test fun `resolver returning null toasts and does not persist`() = runTest {
    val youtubeSearch = FakeYouTubeSearchService().apply { shouldReturnNull = true }
    val (vm, repo, _, _) = mkVm(youtubeSearch = youtubeSearch)
    vm.onResultClicked(sampleResult)
    advanceUntilIdle()

    val s = vm.state.first()
    assertNull(s.pendingConfirm)
    assertEquals(false, s.isAdding)
    assertTrue(repo.allSnapshot().isEmpty())
    val ev = vm.events.replayCache.firstOrNull() ?: vm.events.first()
    assertTrue(ev is AddSongEvent.Toast)
    assertTrue(
        "toast: ${(ev as AddSongEvent.Toast).message}",
        ev.message.contains("YouTube", ignoreCase = true),
    )
}

@Test fun `confirmPendingAdd emits SongAdded with the new song id`() = runTest {
    val (vm, repo, _, _) = mkVm()
    vm.onResultClicked(sampleResult)
    advanceUntilIdle()
    vm.confirmPendingAdd()
    advanceUntilIdle()

    val savedId = repo.allSnapshot().first().id
    val events = vm.events.replayCache
    val added = events.filterIsInstance<AddSongEvent.SongAdded>().firstOrNull()
    assertNotNull(added)
    assertEquals(savedId, added!!.songId)
}
```

- [ ] **Step 2: Run AddSongViewModelTest to verify the new + updated tests fail**

Run: `./gradlew :app:testDebugUnitTest --tests ph.nextbank.drums.ui.add.AddSongViewModelTest`
Expected: COMPILE FAIL — `pendingConfirm`, `confirmPendingAdd`, `dismissPendingAdd`, and the resolver constructor parameter don't exist yet.

- [ ] **Step 3: Update `AddSongViewModel` to add the pending-confirm flow**

Replace the contents of `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt` with:

```kotlin
package ph.nextbank.drums.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.songsterr.DrumTabParser
import ph.nextbank.drums.audio.songsterr.FetchResult
import ph.nextbank.drums.audio.songsterr.ParseResult
import ph.nextbank.drums.audio.songsterr.SongsterrResult
import ph.nextbank.drums.audio.songsterr.SongsterrSearchService
import ph.nextbank.drums.audio.songsterr.SongsterrTabFetcher
import ph.nextbank.drums.audio.songsterr.SongsterrVideoPointsService
import ph.nextbank.drums.audio.songsterr.VideoPointEntry
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.audio.youtube.YouTubeCandidateResolver
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import java.util.UUID
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 400L

data class PendingConfirm(
    val songTemplate: Song,
    val candidate: SearchResult,
)

data class AddSongUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isAdding: Boolean = false,
    val results: List<SongsterrResult> = emptyList(),
    val pendingConfirm: PendingConfirm? = null,
)

sealed interface AddSongEvent {
    data class Toast(val message: String) : AddSongEvent
    data class SongAdded(val songId: String) : AddSongEvent
}

@HiltViewModel
class AddSongViewModel @Inject constructor(
    private val searchService: SongsterrSearchService,
    private val tabFetcher: SongsterrTabFetcher,
    private val parser: DrumTabParser,
    private val pointsService: SongsterrVideoPointsService,
    private val resolver: YouTubeCandidateResolver,
    private val repo: SongRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddSongUiState())
    val state: StateFlow<AddSongUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AddSongEvent>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<AddSongEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.value = _state.value.copy(isSearching = false, results = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.value = _state.value.copy(isSearching = true)
            val res = searchService.search(q).take(10)
            _state.value = _state.value.copy(isSearching = false, results = res)
        }
    }

    fun onResultClicked(result: SongsterrResult) {
        if (_state.value.isAdding) return
        _state.value = _state.value.copy(isAdding = true)
        viewModelScope.launch {
            runCatching {
                val fetched = tabFetcher.fetchDrumTrack(result.songId)
                val points = when (fetched) {
                    is FetchResult.Success -> pointsService.fetch(result.songId, fetched.data.revisionId)
                    else -> emptyList()
                }
                handleFetchResult(result, fetched, points)
            }.getOrElse {
                _events.tryEmit(AddSongEvent.Toast("Couldn't load tab — try a different result"))
                _state.value = _state.value.copy(isAdding = false)
            }
        }
    }

    fun confirmPendingAdd() {
        val pending = _state.value.pendingConfirm ?: return
        val song = pending.songTemplate.copy(youtubeVideoId = pending.candidate.videoId)
        viewModelScope.launch {
            repo.upsertAll(listOf(song))
            _state.value = _state.value.copy(pendingConfirm = null, isAdding = false)
            _events.tryEmit(AddSongEvent.SongAdded(song.id))
        }
    }

    fun dismissPendingAdd() {
        _state.value = _state.value.copy(pendingConfirm = null, isAdding = false)
    }

    private suspend fun handleFetchResult(
        result: SongsterrResult,
        fetched: FetchResult,
        points: List<VideoPointEntry>,
    ) {
        when (fetched) {
            is FetchResult.Success -> {
                when (val parsed = parser.parse(fetched.data)) {
                    is ParseResult.Success -> stagePendingAdd(result, fetched.data.revisionId, parsed, points)
                    is ParseResult.NoDrumTrack -> {
                        _events.tryEmit(AddSongEvent.Toast("This song doesn't have a drum tab on Songsterr."))
                        _state.value = _state.value.copy(isAdding = false)
                    }
                    is ParseResult.ParseError -> {
                        _events.tryEmit(AddSongEvent.Toast("Couldn't read the tab data."))
                        _state.value = _state.value.copy(isAdding = false)
                    }
                }
            }
            FetchResult.NoDrumTrack -> {
                _events.tryEmit(AddSongEvent.Toast("This song doesn't have a drum tab on Songsterr."))
                _state.value = _state.value.copy(isAdding = false)
            }
            is FetchResult.ScrapeFailure -> {
                _events.tryEmit(AddSongEvent.Toast("Couldn't load tab from Songsterr — try a different result"))
                _state.value = _state.value.copy(isAdding = false)
            }
            FetchResult.NetworkError -> {
                _events.tryEmit(AddSongEvent.Toast("Check your connection."))
                _state.value = _state.value.copy(isAdding = false)
            }
        }
    }

    private suspend fun stagePendingAdd(
        result: SongsterrResult,
        revisionId: Long,
        parsed: ParseResult.Success,
        points: List<VideoPointEntry>,
    ) {
        val videoPoints = points.takeIf { it.isNotEmpty() }
        val candidate = resolver.resolveInitial(
            title = result.title,
            artist = result.artist,
            videoPoints = videoPoints,
            blocklist = emptySet(),
        )
        if (candidate == null) {
            _events.tryEmit(AddSongEvent.Toast("Couldn't find a YouTube match — try a different result"))
            _state.value = _state.value.copy(isAdding = false)
            return
        }
        val coverInitials = (result.artist.take(1) + result.title.take(1)).uppercase().ifEmpty { "??" }
        val songTemplate = Song(
            id = UUID.randomUUID().toString(),
            title = result.title,
            artist = result.artist,
            bpm = parsed.bpm,
            timeSig = parsed.timeSig,
            bars = parsed.bars,
            coverInitials = coverInitials,
            importedFrom = ImportSource.BUNDLED,
            lastPlayed = null,
            youtubeVideoId = null,  // filled in on confirm
            songsterrId = result.songId,
            songsterrRevisionId = revisionId.toString(),
            videoPoints = videoPoints,
        )
        _state.value = _state.value.copy(
            pendingConfirm = PendingConfirm(songTemplate, candidate),
            // isAdding stays true so the search list stays disabled while the dialog is up
        )
    }
}
```

- [ ] **Step 4: Run AddSongViewModelTest to verify all tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests ph.nextbank.drums.ui.add.AddSongViewModelTest`
Expected: All AddSongViewModel tests pass.

- [ ] **Step 5: Run the full unit test suite to check nothing else regressed**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All ~95+ tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt \
        app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt
git commit -m "AddSongViewModel: resolve YouTube candidate at add time

After a successful tab + points fetch, the resolver picks a YouTube
candidate and the song is staged via PendingConfirm. The user must
confirm to persist; dismiss cancels the add. Songs added this way will
take the cached-id branch in PlayerViewModel.init on first open.
"
```

---

## Task 4: Wire the confirmation dialog into `AddSongScreen`

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt`

This is UI-only — no JVM unit test. We render the existing `YouTubeConfirmDialog` (in `ui/player/`) above the loading overlay whenever `pendingConfirm != null`. The dialog already lives in the `ui.player` package and is freely accessible from `ui.add`.

- [ ] **Step 1: Add the dialog rendering to `AddSongScreen`**

In `app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt`, add the import:

```kotlin
import ph.nextbank.drums.ui.player.YouTubeConfirmDialog
```

Replace the loading overlay block (currently `if (state.isAdding) { Box(...) { ... "Loading tab…" ... } }`, around lines 138-152) with the following, which shows the dialog when there's a pending confirm and otherwise keeps the existing "Loading tab…" overlay:

```kotlin
val pending = state.pendingConfirm
if (pending != null) {
    YouTubeConfirmDialog(
        candidate = pending.candidate,
        onUseThis = vm::confirmPendingAdd,
        onTryAnother = vm::dismissPendingAdd,  // no cycling at add time — same as dismiss
        onDismiss = vm::dismissPendingAdd,
    )
} else if (state.isAdding) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DrumsColors.Bg.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DrumsColors.Accent)
            Spacer(Modifier.height(12.dp))
            Text("Loading tab…", color = DrumsColors.Text, style = DrumsType.body)
        }
    }
}
```

Note: `onTryAnother` and `onDismiss` both call `dismissPendingAdd`. Per spec, no Add-time cycling. Both effectively cancel the add. (The dialog still shows the "Try another" button for visual consistency with the player; tapping it just dismisses.)

- [ ] **Step 2: Build the app to verify the Compose code compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt
git commit -m "AddSongScreen: show YouTubeConfirmDialog for pending adds

Reuses the player's existing confirm dialog. Both Try-another and
dismiss cancel the add (per spec — no Add-time cycling). The Loading
tab… overlay only shows while the tab fetch is in flight, not while
the dialog is up.
"
```

---

## Task 5: End-to-end smoke test + final verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests pass.

- [ ] **Step 2: Install on a connected device or emulator**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL, app installed.

- [ ] **Step 3: Manual smoke test — happy path**

Open the app and verify:

1. Tap the Add Song FAB.
2. Type a search query (e.g. "in the air tonight").
3. Pick a search result.
4. The "Loading tab…" overlay appears briefly, then the YouTube confirm dialog opens.
5. Tap "Use this video" — the dialog closes and you land on the player.
6. The player starts buffering YouTube audio immediately, **with no confirmation dialog**.
7. Back out, then re-open the same song from the library — still no dialog, plays straight through.

Expected: all of the above. If the dialog re-appears in step 6, the youtubeVideoId did not persist — verify the song row in Room (use `adb shell`'s sqlite or Android Studio's Database Inspector).

- [ ] **Step 4: Manual smoke test — dismiss path**

1. Tap Add Song FAB → search → pick a result.
2. When the YouTube confirm dialog appears, tap outside it (or hit back) to dismiss.
3. Expected: you return to the search list. No new entry appears in the library.
4. Verify the library count is unchanged by backing out to the library screen.

- [ ] **Step 5: Manual smoke test — Try another button**

1. Tap Add Song FAB → search → pick a result.
2. When the dialog appears, tap "Try another".
3. Expected: dialog dismisses and the song is NOT added (same as the dismiss path). This matches the spec — there's no Add-time cycling.

- [ ] **Step 6: Manual smoke test — old songs still work**

1. Pick a song that was added *before* this change (one of the bundled songs, or a pre-existing library entry).
2. Open it from the library.
3. Expected: existing behavior — the player either shows the confirm dialog (because youtubeVideoId is null on legacy rows) or plays directly (if a cached id is already present from a prior accept).

- [ ] **Step 7: No final commit needed**

The four prior commits cover all code changes. This task is verification only.

---

## Self-Review Notes

**Spec coverage:**
- Resolver helper → Task 1 ✓
- AddSongViewModel pendingConfirm + confirm/dismiss → Task 3 ✓
- AddSongScreen dialog wiring → Task 4 ✓
- PlayerViewModel delegation → Task 2 (unsynced path only; synced cycling kept inline because the resolver's stateless interface doesn't expose the entry index the player needs)
- Error handling table (resolver null, network error, dismiss) → tests in Task 3
- No DB schema change → confirmed; `SongEntity.youtubeVideoId` already exists
- Test plan from spec → matches Tasks 1 & 3

**Deviation from spec:** the spec said `showSyncedCandidate` would also delegate to the resolver. The plan keeps `showSyncedCandidate` as-is because its `syncedCandidateIdx` state can't be reconstructed from the resolver's stateless return. The DRY benefit is partial (the unsynced path is unified), which is enough for the user-facing goal of the change.

**Type consistency check:**
- `PendingConfirm` defined in Task 3, used in Task 4 ✓
- `YouTubeCandidateResolver.resolveInitial` signature consistent across Task 1, 2, 3 ✓
- `AddSongViewModel` constructor: search, fetcher, parser, pointsService, resolver, repo — same order in Task 3 source and `mkVm` in tests ✓
