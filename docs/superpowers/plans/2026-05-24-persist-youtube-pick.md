# Persist YouTube Pick Across Song Opens — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop wiping `Song.youtubeVideoId` on transient NewPipe extraction failures, so a successfully-played video is remembered forever.

**Architecture:** Move persistence of `youtubeVideoId` from "user clicked it in the picker" to "video produced a real audio stream URL." `acceptCandidate` becomes session-local intent; `startYouTubePlayback` writes to the repo after `streamUrl != null`. Failure branch keeps the blocklist update and drops the videoId-clear.

**Tech Stack:** Kotlin, Hilt ViewModel, Room (already wired), JUnit + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-05-24-persist-youtube-pick-design.md`

---

### Task 1: Add failing test — extraction failure preserves cached videoId

**Files:**
- Modify: `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`

- [ ] **Step 1: Add the failing test**

Add this test at the end of the `PlayerViewModelTest` class, just before the closing brace (after `accepting a synced candidate uses PointsBasedTimeMap`):

```kotlin
@Test
fun `extraction failure preserves cached videoId`() = runTest {
    val seed = songWithoutVideo().copy(youtubeVideoId = "ccc33333333")
    val repo = FakeSongRepository().apply { seed(seed) }
    // No audioUrls + no search queue → extraction fails for "ccc33333333",
    // resolver returns empty, falls back to synth.
    val search = FakeYouTubeSearchService()
    val vm = mkVm(repo, search)
    advanceUntilIdle()
    // Synth fallback is the end state — the failed video had no alternatives.
    assertEquals(PlayerPhase.SynthFallback, vm.state.first().phase)
    // Cache stays intact; only the blocklist records the failure.
    assertEquals("ccc33333333", repo.snapshot("test1")!!.youtubeVideoId)
    assertEquals(listOf("ccc33333333"), repo.snapshot("test1")!!.youtubeBlocklist)
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.ui.player.PlayerViewModelTest.extraction failure preserves cached videoId"`

Expected: FAIL with `expected:<ccc33333333> but was:<null>`. The current code on `PlayerViewModel.kt:143` calls `repo.updateYoutubeVideoId(songId, null)`, which is exactly what this test forbids.

---

### Task 2: Add failing test — acceptCandidate defers persistence until extraction succeeds

**Files:**
- Modify: `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`

- [ ] **Step 1: Add the failing test**

Add this test directly after the test from Task 1:

```kotlin
@Test
fun `acceptCandidate defers persistence until extraction succeeds`() = runTest {
    val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
    val pick = SearchResult("abc12345678", "T", "U", 100, "")
    // Pick is in the queue so the picker can show it, but streamUrlForVideoId maps it to null
    // → extraction fails. We expect: no persisted videoId (the candidate never proved playable).
    val search = FakeYouTubeSearchService().apply {
        queue = listOf(pick)
        streamUrlForVideoId["abc12345678"] = null
    }
    val vm = mkVm(repo, search)
    advanceUntilIdle()
    vm.acceptCandidate(pick)
    advanceUntilIdle()
    assertEquals(null, repo.snapshot("test1")!!.youtubeVideoId)
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.ui.player.PlayerViewModelTest.acceptCandidate defers persistence until extraction succeeds"`

Expected: FAIL with `expected:<null> but was:<abc12345678>`. Current `acceptCandidate` writes to the repo immediately, before extraction proves the videoId is playable.

---

### Task 3: Refactor persistence to "proven extractable" semantics

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt` (three edits)

- [ ] **Step 1: Strip immediate persistence out of `acceptCandidate`**

Find the current `acceptCandidate` (around line 108):

```kotlin
    fun acceptCandidate(candidate: SearchResult) {
        viewModelScope.launch {
            repo.updateYoutubeVideoId(songId, candidate.videoId)
            _state.value = _state.value.copy(
                song = _state.value.song?.copy(youtubeVideoId = candidate.videoId),
            )
            startYouTubePlayback(candidate.videoId)
        }
    }
```

Replace it with:

```kotlin
    fun acceptCandidate(candidate: SearchResult) {
        viewModelScope.launch {
            startYouTubePlayback(candidate.videoId)
        }
    }
```

The persistence now happens inside `startYouTubePlayback` after extraction succeeds (Step 2).

- [ ] **Step 2: Persist videoId in `startYouTubePlayback` after the stream URL resolves**

Find the success path of `startYouTubePlayback` — the line `val s = _state.value.song ?: return` (around line 155, immediately after the failure-branch `return`).

The current code looks like:

```kotlin
        if (streamUrl == null) {
            if (extractionAttempts >= MAX_EXTRACTION_ATTEMPTS) {
                _events.tryEmit(PlayerEvent.Toast("YouTube audio unavailable — playing synth drums"))
                switchToSynth()
                return
            }
            // Blocklist the failed videoId so re-opening the song skips it.
            val failedBlocklist = (_state.value.song?.youtubeBlocklist.orEmpty() + videoId).distinct()
            repo.updateYoutubeBlocklist(songId, failedBlocklist)
            repo.updateYoutubeVideoId(songId, null)
            _state.value = _state.value.copy(
                song = _state.value.song?.copy(
                    youtubeBlocklist = failedBlocklist,
                    youtubeVideoId = null,
                ),
            )
            _events.tryEmit(PlayerEvent.Toast("Audio unavailable for this video — trying another"))
            val currentSong = _state.value.song ?: return
            showCandidates(currentSong, failedBlocklist.toSet())
            return
        }
        val s = _state.value.song ?: return
        val adapter = adapterFactory.create(streamUrl)
```

Replace with (failure branch loses the videoId clear; success path gains the persistence):

```kotlin
        if (streamUrl == null) {
            if (extractionAttempts >= MAX_EXTRACTION_ATTEMPTS) {
                _events.tryEmit(PlayerEvent.Toast("YouTube audio unavailable — playing synth drums"))
                switchToSynth()
                return
            }
            // Blocklist the failed videoId so re-opening the song skips it.
            // Do NOT clear youtubeVideoId — the user's prior choice should survive a transient
            // NewPipe extraction failure. If this song had no prior cache, there's nothing to keep.
            val failedBlocklist = (_state.value.song?.youtubeBlocklist.orEmpty() + videoId).distinct()
            repo.updateYoutubeBlocklist(songId, failedBlocklist)
            _state.value = _state.value.copy(
                song = _state.value.song?.copy(youtubeBlocklist = failedBlocklist),
            )
            _events.tryEmit(PlayerEvent.Toast("Audio unavailable for this video — trying another"))
            val currentSong = _state.value.song ?: return
            showCandidates(currentSong, failedBlocklist.toSet())
            return
        }
        // Stream URL resolved → this videoId is proven playable. Persist it so future opens
        // skip the picker. Idempotent for the cold-open-from-cache case.
        if (_state.value.song?.youtubeVideoId != videoId) {
            repo.updateYoutubeVideoId(songId, videoId)
            _state.value = _state.value.copy(
                song = _state.value.song?.copy(youtubeVideoId = videoId),
            )
        }
        val s = _state.value.song ?: return
        val adapter = adapterFactory.create(streamUrl)
```

- [ ] **Step 3: Run the two new tests and verify they now pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.ui.player.PlayerViewModelTest.extraction failure preserves cached videoId" --tests "ph.nextbank.drums.ui.player.PlayerViewModelTest.acceptCandidate defers persistence until extraction succeeds"`

Expected: BUILD SUCCESSFUL, 2 tests run, 0 failures.

---

### Task 4: Verify the full PlayerViewModel test suite still passes

**Files:** none (verification only).

- [ ] **Step 1: Run the full PlayerViewModel test class**

Run: `./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.ui.player.PlayerViewModelTest"`

Expected: BUILD SUCCESSFUL, 15 tests run (13 existing + 2 new), 0 failures.

If any existing test fails, common culprits to check:
- `acceptCandidate caches videoId and starts YouTube playback` (line 108) — relies on extraction succeeding (`audioUrls` is mapped), so the new success-path persistence should set the videoId. End-state assertion at `repo.snapshot("test1")!!.youtubeVideoId == "abc12345678"` must still hold.
- `user can pick any candidate from the list` (line 132) — same shape; extraction succeeds for `bbb22222222`, persistence happens, assertion holds.
- `tryAnotherVideo from playback blocklists current and reopens list` (line 151) — `tryAnotherVideo` still explicitly clears the cache, so `youtubeVideoId == null` at the end holds.
- `audio URL extraction failure triggers reopen with blocklist updated` (line 215) — only asserts on the blocklist, not the cache. Should still pass.
- `extraction failures hit cap of 3 then fall back to synth` (line 228) — only asserts on phases, not the cache. Should still pass.

- [ ] **Step 2: Run the broader unit-test suite to catch unintended fallout**

Run: `./gradlew :app:testDebugUnitTest`

Expected: BUILD SUCCESSFUL, all tests pass.

---

### Task 5: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt \
        app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt
git commit -m "Persist YouTube pick only after stream extraction proves it works

Move the youtubeVideoId persistence from acceptCandidate (moment of pick)
to startYouTubePlayback (moment a real audio stream URL is returned).
Stop wiping the cache on transient NewPipe extraction failures so the
user's previous successful choice survives flaky extraction.

acceptCandidate becomes session-local intent; the failure branch keeps
the blocklist update but no longer touches youtubeVideoId.
tryAnotherVideo is unchanged — explicit rejection still clears."
```

- [ ] **Step 2: Confirm clean status**

Run: `git status`

Expected: `nothing to commit, working tree clean`.
