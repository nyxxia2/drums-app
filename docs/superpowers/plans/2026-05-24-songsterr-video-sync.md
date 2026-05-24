# Drums App — Songsterr Video Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-sync the drum tab to the YouTube audio by consuming Songsterr's `/api/video-points` endpoint, which gives bar-start timestamps in video time for every YouTube video they've aligned.

**Architecture:** Three new pieces — a `TimeMap` abstraction (today's BPM math + a new points-based interpolation), an `OkHttpSongsterrVideoPointsService` that fetches the per-video timestamps, and a synced branch in `PlayerViewModel`'s init that shows the existing Use-this/Try-another dialog using a Songsterr-curated video instead of the Phase 2 YouTube search. The clock change is contained inside `YouTubePlaybackSource`; everything else is plumbing. Legacy songs with no Songsterr sync data keep today's behavior unchanged.

**Tech Stack:** Kotlin + Jetpack Compose + Hilt + Room + OkHttp + kotlinx.serialization (all already in the project, no new dependencies).

**Spec:** [`docs/superpowers/specs/2026-05-24-songsterr-video-sync-design.md`](../specs/2026-05-24-songsterr-video-sync-design.md).

---

## File structure

**Created:**
- `app/src/main/java/ph/nextbank/drums/audio/TimeMap.kt` — interface + `ConstantBpmTimeMap` (today's math) + `PointsBasedTimeMap` (new).
- `app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrVideoPointsService.kt` — interface + `VideoPointEntry` data class.
- `app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrVideoPointsService.kt` — OkHttp implementation.
- `app/src/test/java/ph/nextbank/drums/audio/ConstantBpmTimeMapTest.kt`
- `app/src/test/java/ph/nextbank/drums/audio/PointsBasedTimeMapTest.kt`
- `app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrVideoPointsServiceTest.kt`
- `app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrVideoPointsService.kt`
- `app/src/test/resources/songsterr/video-points-teen-spirit.json` — trimmed fixture (3 entries × 5 points each).

**Modified:**
- `app/src/main/java/ph/nextbank/drums/data/model/Song.kt` — add `videoPoints: List<VideoPointEntry>?`.
- `app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt` — add `videoPointsJson: String?` column + mappers.
- `app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt` — version 4 → 5 + `MIGRATION_4_5`.
- `app/src/main/java/ph/nextbank/drums/data/db/SongDao.kt` — add `updateVideoPoints` method.
- `app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt` — add `updateVideoPoints` method.
- `app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeSearchService.kt` — add `fetchMeta(videoId)` method.
- `app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeYouTubeSearchService.kt` — implement `fetchMeta`.
- `app/src/main/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSource.kt` — constructor takes `TimeMap`.
- `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt` — fetch points in parallel + persist.
- `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt` — synced branch in `init` + `tryAnother` cycling.
- `app/src/main/java/ph/nextbank/drums/di/AppModule.kt` — provide `SongsterrVideoPointsService`.
- `app/src/test/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSourceTest.kt` — constructor adaptation.
- `app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt` — new synced/unsynced cases.
- `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt` — synced path tests.

---

## Verified prerequisites

These were probed live during brainstorming. Tasks reference them as concrete starting points:

- **Endpoint:** `GET https://www.songsterr.com/api/video-points/{songId}/{revisionId}/list` returns an array of entries. Each entry has fields `id, revisionToVideoId, songId, revisionId, videoId (YouTube ID), points (List<Double>), feature ("alternative"/"solo"/"backing"/null), countries, status, problematic, alternativeVideos, trackHashes, tracks`. The `points` field is per-bar timestamps in seconds of video time. We only need `videoId`, `points`, and `feature`.
- **Sample**: for songId 269 (Teen Spirit), revisionId 6953431, the endpoint returns 111 entries. First entry: videoId `zYxkezUr8MQ`, feature `alternative`, points `[-0.15, 2.5, 4.68, 6.86, …]` (143 points).
- **CDN**: not relevant — this endpoint is on `www.songsterr.com`, not the cloudfront CDN. Same host as `SongsterrSearchService` and the meta endpoint.
- **No auth, no special headers** — same client config as existing services works.

---

# Milestone 0 — TimeMap foundation (Tasks 1–4)

Refactor `YouTubePlaybackSource`'s constant-BPM math into a `TimeMap` interface. Today's behavior is preserved by wrapping in a `ConstantBpmTimeMap`. Add the new `PointsBasedTimeMap` and prove it works via tests. No user-visible change yet.

### Task 1: TimeMap interface + ConstantBpmTimeMap

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/TimeMap.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/ConstantBpmTimeMapTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/ph/nextbank/drums/audio/ConstantBpmTimeMapTest.kt`:

```kotlin
package ph.nextbank.drums.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstantBpmTimeMapTest {

    @Test fun `slotAt at t=0 returns 0`() {
        val tm = ConstantBpmTimeMap(bpm = 120, totalSlots = 64, slotsPerBeat = 4)
        assertEquals(0f, tm.slotAt(0f), 0.001f)
    }

    @Test fun `slotAt at one second at 120 BPM 4 slots-per-beat returns 8`() {
        // 120 BPM = 2 beats per second. 4 slots per beat = 8 slots per second.
        val tm = ConstantBpmTimeMap(bpm = 120, totalSlots = 64, slotsPerBeat = 4)
        assertEquals(8f, tm.slotAt(1f), 0.001f)
    }

    @Test fun `videoSecAt is inverse of slotAt`() {
        val tm = ConstantBpmTimeMap(bpm = 95, totalSlots = 256, slotsPerBeat = 4)
        val sec = 12.345f
        val slot = tm.slotAt(sec)
        assertEquals(sec, tm.videoSecAt(slot), 0.001f)
    }

    @Test fun `totalSlots is returned verbatim`() {
        val tm = ConstantBpmTimeMap(bpm = 100, totalSlots = 999, slotsPerBeat = 4)
        assertEquals(999, tm.totalSlots)
    }
}
```

- [ ] **Step 2: Run the test — expect failure (class missing)**

```bash
export ANDROID_HOME=/home/sara/Android/Sdk JAVA_HOME=/home/sara/Downloads/android-studio-panda4-patch1-linux/android-studio/jbr
./gradlew :app:testDebugUnitTest --tests "*ConstantBpmTimeMapTest*" 2>&1 | tail -5
```

Expected: compile error — `ConstantBpmTimeMap` not defined.

- [ ] **Step 3: Implement TimeMap + ConstantBpmTimeMap**

Create `app/src/main/java/ph/nextbank/drums/audio/TimeMap.kt`:

```kotlin
package ph.nextbank.drums.audio

/**
 * Maps between video playback time (seconds) and song slot position.
 *
 * Two implementations: [ConstantBpmTimeMap] uses a fixed BPM (legacy / unsynced
 * songs); [PointsBasedTimeMap] uses per-bar timestamps from Songsterr's
 * /api/video-points endpoint (synced songs).
 */
interface TimeMap {
    val totalSlots: Int
    fun slotAt(videoSec: Float): Float
    fun videoSecAt(slot: Float): Float
}

class ConstantBpmTimeMap(
    bpm: Int,
    override val totalSlots: Int,
    slotsPerBeat: Int,
) : TimeMap {
    private val secPerSlot: Float = (60f / bpm) / slotsPerBeat
    override fun slotAt(videoSec: Float): Float = videoSec / secPerSlot
    override fun videoSecAt(slot: Float): Float = slot * secPerSlot
}
```

- [ ] **Step 4: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*ConstantBpmTimeMapTest*" 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/TimeMap.kt app/src/test/java/ph/nextbank/drums/audio/ConstantBpmTimeMapTest.kt
git commit -m "Add TimeMap interface + ConstantBpmTimeMap"
```

---

### Task 2: PointsBasedTimeMap

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/audio/TimeMap.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/PointsBasedTimeMapTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/ph/nextbank/drums/audio/PointsBasedTimeMapTest.kt`:

```kotlin
package ph.nextbank.drums.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PointsBasedTimeMapTest {

    private fun map(
        points: List<Double>,
        slotsPerBar: Int = 16,
    ): PointsBasedTimeMap = PointsBasedTimeMap(
        points = points,
        slotsPerBar = slotsPerBar,
        totalSlots = (points.size) * slotsPerBar,
    )

    @Test fun `slot at exactly points i equals i times slotsPerBar`() {
        val tm = map(listOf(0.0, 2.0, 4.0, 6.0))
        assertEquals(0f, tm.slotAt(0f), 0.001f)
        assertEquals(16f, tm.slotAt(2f), 0.001f)
        assertEquals(32f, tm.slotAt(4f), 0.001f)
    }

    @Test fun `slot halfway between points is interpolated`() {
        val tm = map(listOf(0.0, 2.0, 4.0))
        // Halfway between points[0] and points[1] → halfway through bar 0 → slot 8.
        assertEquals(8f, tm.slotAt(1f), 0.001f)
        // Halfway between points[1] and points[2] → halfway through bar 1 → slot 24.
        assertEquals(24f, tm.slotAt(3f), 0.001f)
    }

    @Test fun `slot before first point is clamped to 0`() {
        val tm = map(listOf(2.0, 4.0, 6.0))
        assertEquals(0f, tm.slotAt(-1f), 0.001f)
        assertEquals(0f, tm.slotAt(0f), 0.001f)
        assertEquals(0f, tm.slotAt(1.99f), 0.001f)
    }

    @Test fun `slot at or past last point is clamped to totalSlots minus 1`() {
        val tm = map(listOf(0.0, 2.0, 4.0))  // 3 points × 16 slots/bar = 48 totalSlots
        assertEquals(47f, tm.slotAt(4f), 0.001f)
        assertEquals(47f, tm.slotAt(100f), 0.001f)
    }

    @Test fun `negative first point like Teen Spirit's minus point-15 is handled`() {
        val tm = map(listOf(-0.15, 2.5, 4.68))
        // t = 0 is between points[0]=-0.15 and points[1]=2.5.
        // fraction = (0 - (-0.15)) / (2.5 - (-0.15)) = 0.15 / 2.65 ≈ 0.0566
        // slot = (0 + 0.0566) * 16 ≈ 0.906
        assertEquals(0.906f, tm.slotAt(0f), 0.01f)
    }

    @Test fun `videoSecAt is inverse of slotAt within rounding tolerance`() {
        val tm = map(listOf(0.0, 2.0, 4.0, 6.0, 8.0))
        for (sec in listOf(0.5f, 1.0f, 3.7f, 5.25f, 7.9f)) {
            val slot = tm.slotAt(sec)
            assertEquals("sec=$sec round-trip", sec, tm.videoSecAt(slot), 0.001f)
        }
    }

    @Test fun `two-point map handles the single-bar case without crashing`() {
        val tm = PointsBasedTimeMap(points = listOf(0.0, 2.0), slotsPerBar = 16, totalSlots = 16)
        assertEquals(0f, tm.slotAt(0f), 0.001f)
        assertEquals(8f, tm.slotAt(1f), 0.001f)
        assertEquals(15f, tm.slotAt(2f), 0.001f)
    }

    @Test fun `totalSlots is returned verbatim`() {
        val tm = PointsBasedTimeMap(points = listOf(0.0, 1.0), slotsPerBar = 16, totalSlots = 999)
        assertEquals(999, tm.totalSlots)
    }
}
```

- [ ] **Step 2: Run the tests — expect failure (class missing)**

```bash
./gradlew :app:testDebugUnitTest --tests "*PointsBasedTimeMapTest*" 2>&1 | tail -5
```

Expected: compile error — `PointsBasedTimeMap` not defined.

- [ ] **Step 3: Implement PointsBasedTimeMap**

Append to `app/src/main/java/ph/nextbank/drums/audio/TimeMap.kt`:

```kotlin
/**
 * Maps video-time to slot using a list of per-bar timestamps. The points list
 * has one entry per bar — `points[i]` is the video-time (seconds) at which
 * bar `i` starts. Within a bar, slot position is linearly interpolated.
 *
 * For `t < points[0]` returns 0 (clamped).
 * For `t >= points.last()` returns `totalSlots - 1` (clamped).
 */
class PointsBasedTimeMap(
    private val points: List<Double>,
    private val slotsPerBar: Int,
    override val totalSlots: Int,
) : TimeMap {

    init {
        require(points.size >= 2) { "PointsBasedTimeMap needs at least 2 points; got ${points.size}" }
        require(slotsPerBar > 0)
        require(totalSlots > 0)
    }

    override fun slotAt(videoSec: Float): Float {
        val t = videoSec.toDouble()
        if (t <= points[0]) return 0f
        if (t >= points.last()) return (totalSlots - 1).toFloat()

        // Binary search for bar i where points[i] <= t < points[i+1].
        var lo = 0
        var hi = points.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (points[mid] <= t) lo = mid else hi = mid
        }
        val i = lo
        val fraction = (t - points[i]) / (points[i + 1] - points[i])
        val slot = (i + fraction) * slotsPerBar
        return slot.toFloat().coerceIn(0f, (totalSlots - 1).toFloat())
    }

    override fun videoSecAt(slot: Float): Float {
        if (slot <= 0f) return points[0].toFloat()
        val maxSlot = (points.size - 1) * slotsPerBar
        if (slot >= maxSlot) return points.last().toFloat()

        val bar = (slot / slotsPerBar).toInt().coerceIn(0, points.size - 2)
        val fraction = (slot - bar * slotsPerBar) / slotsPerBar.toFloat()
        val sec = points[bar] + fraction * (points[bar + 1] - points[bar])
        return sec.toFloat()
    }
}
```

- [ ] **Step 4: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*PointsBasedTimeMapTest*" 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/TimeMap.kt app/src/test/java/ph/nextbank/drums/audio/PointsBasedTimeMapTest.kt
git commit -m "Add PointsBasedTimeMap for per-bar video sync"
```

---

### Task 3: Refactor YouTubePlaybackSource to take TimeMap

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSource.kt`
- Modify: `app/src/test/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSourceTest.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt:148-156`

This is a constructor signature change. Today's behavior is preserved 1:1 by wrapping the existing `songBpm/slotsPerBeat/totalSlots` triplet in a `ConstantBpmTimeMap` at the only call site (`PlayerViewModel.startYouTubePlayback`).

- [ ] **Step 1: Update YouTubePlaybackSource to accept TimeMap**

Open `app/src/main/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSource.kt`. Replace lines 26–32 (the class declaration + `songBpm/slotsPerBeat/totalSlots` constructor params):

```kotlin
class YouTubePlaybackSource(
    private val timeMap: ph.nextbank.drums.audio.TimeMap,
    private val adapter: YouTubeAdapter,
    initialOffsetMs: Int,
) : PlaybackSource {
```

Remove line 50 (the `private val msPerSlot` field).

Replace the body of `onCurrentSecond` (lines 66–72) with:

```kotlin
            override fun onCurrentSecond(seconds: Float) {
                val effectiveSec = seconds + offsetMs / 1000f
                val slot = timeMap.slotAt(effectiveSec)
                val clamped = slot.coerceIn(0f, timeMap.totalSlots.toFloat() - 0.001f)
                _currentSlot.value = clamped
                _activeSlotIndex.value = clamped.toInt()
            }
```

- [ ] **Step 2: Update the PlayerViewModel call site**

Open `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`. Find the existing block (around lines 148–156):

```kotlin
        val adapter = adapterFactory.create(streamUrl)
        val src = YouTubePlaybackSource(
            songBpm = s.bpm,
            slotsPerBeat = s.slotsPerBar / s.timeSig.first,
            totalSlots = s.totalBars * s.slotsPerBar,
            adapter = adapter,
            initialOffsetMs = s.youtubeOffsetMs,
        )
```

Replace with:

```kotlin
        val adapter = adapterFactory.create(streamUrl)
        val timeMap = ph.nextbank.drums.audio.ConstantBpmTimeMap(
            bpm = s.bpm,
            totalSlots = s.totalBars * s.slotsPerBar,
            slotsPerBeat = s.slotsPerBar / s.timeSig.first,
        )
        val src = YouTubePlaybackSource(
            timeMap = timeMap,
            adapter = adapter,
            initialOffsetMs = s.youtubeOffsetMs,
        )
```

(The fully-qualified `ph.nextbank.drums.audio.ConstantBpmTimeMap` avoids touching the top-of-file imports; you can add `import ph.nextbank.drums.audio.ConstantBpmTimeMap` if you prefer.)

- [ ] **Step 3: Update YouTubePlaybackSourceTest constructor calls**

Open `app/src/test/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSourceTest.kt`. Find every construction of `YouTubePlaybackSource(...)`. Replace each with the new signature, wrapping the previous BPM/slots arguments in a `ConstantBpmTimeMap`. Example pattern:

Before:
```kotlin
val source = YouTubePlaybackSource(
    songBpm = 120,
    slotsPerBeat = 4,
    totalSlots = 64,
    adapter = fakeAdapter,
    initialOffsetMs = 0,
)
```

After:
```kotlin
val source = YouTubePlaybackSource(
    timeMap = ConstantBpmTimeMap(bpm = 120, totalSlots = 64, slotsPerBeat = 4),
    adapter = fakeAdapter,
    initialOffsetMs = 0,
)
```

Add `import ph.nextbank.drums.audio.ConstantBpmTimeMap` at the top of the test file.

- [ ] **Step 4: Run the full suite — expect PASS**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL. All 63 existing tests + 12 new TimeMap tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSource.kt \
        app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt \
        app/src/test/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSourceTest.kt
git commit -m "Inject TimeMap into YouTubePlaybackSource (no behavior change)"
```

---

# Milestone 1 — Songsterr video-points service (Tasks 4–7)

Fetch the per-video timestamps from Songsterr. Pure-IO, no DB or UI yet.

### Task 4: SongsterrVideoPointsService interface + VideoPointEntry

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrVideoPointsService.kt`

- [ ] **Step 1: Write the interface + data class**

Create `app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrVideoPointsService.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import kotlinx.serialization.Serializable

interface SongsterrVideoPointsService {
    /**
     * Fetch the list of YouTube videos Songsterr has aligned to a given tab,
     * with per-bar timestamps in video-time (seconds).
     *
     * Returns an empty list on network failure, HTTP error, or malformed JSON.
     * Callers treat empty == "no sync available" and fall back to the legacy
     * unsynced playback path.
     */
    suspend fun fetch(songId: Long, revisionId: Long): List<VideoPointEntry>
}

/**
 * One aligned YouTube video for a given (songId, revisionId).
 *
 * @param youtubeVideoId 11-char YouTube video ID.
 * @param points per-bar timestamps in seconds of video time; `points[i]` is
 *   the video-time at which bar `i` begins. May start negative if the tab's
 *   bar 1 precedes a "0:00" reference inside the video.
 * @param feature Songsterr's editorial tag for this video: "alternative",
 *   "solo", "backing", or null. We don't use it for selection (raw list
 *   order is what the web player uses) but keep it for future ranking.
 */
@Serializable
data class VideoPointEntry(
    val youtubeVideoId: String,
    val points: List<Double>,
    val feature: String?,
)
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrVideoPointsService.kt
git commit -m "Add SongsterrVideoPointsService interface + VideoPointEntry"
```

---

### Task 5: Test fixture for video-points response

**Files:**
- Create: `app/src/test/resources/songsterr/video-points-teen-spirit.json`

A trimmed real response — 3 entries × 5 points each. Mirrors the production shape so the parser is exercised end-to-end.

- [ ] **Step 1: Create the fixture**

Create `app/src/test/resources/songsterr/video-points-teen-spirit.json` with the following content:

```json
[
  {
    "id": 3469811,
    "revisionToVideoId": 9535937,
    "songId": 269,
    "revisionId": 6953431,
    "videoId": "zYxkezUr8MQ",
    "feature": "alternative",
    "points": [-0.15, 2.5, 4.68, 6.86, 8.95],
    "status": "done",
    "problematic": false,
    "countries": [],
    "alternativeVideos": [],
    "trackHashes": [],
    "tracks": []
  },
  {
    "id": 3470000,
    "revisionToVideoId": 9536000,
    "songId": 269,
    "revisionId": 6953431,
    "videoId": "y_z4ycssv54",
    "feature": "solo",
    "points": [0.56, 3.2, 5.39, 7.5, 9.6],
    "status": "done",
    "problematic": false,
    "countries": [],
    "alternativeVideos": [],
    "trackHashes": [],
    "tracks": []
  },
  {
    "id": 3470001,
    "revisionToVideoId": 9536001,
    "songId": 269,
    "revisionId": 6953431,
    "videoId": "H6mdnPOpcaU",
    "feature": null,
    "points": [40.34, 42.99, 45.17, 47.3, 49.4],
    "status": "done",
    "problematic": false,
    "countries": [],
    "alternativeVideos": [],
    "trackHashes": [],
    "tracks": []
  }
]
```

- [ ] **Step 2: Verify it parses**

Quick sanity check:

```bash
python3 -c "import json; d = json.load(open('app/src/test/resources/songsterr/video-points-teen-spirit.json')); print(len(d), 'entries; videoIds:', [e['videoId'] for e in d])"
```

Expected output: `3 entries; videoIds: ['zYxkezUr8MQ', 'y_z4ycssv54', 'H6mdnPOpcaU']`.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/resources/songsterr/video-points-teen-spirit.json
git commit -m "Add video-points fixture (trimmed Teen Spirit)"
```

---

### Task 6: OkHttpSongsterrVideoPointsService + tests (TDD)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrVideoPointsService.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrVideoPointsServiceTest.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrVideoPointsService.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrVideoPointsServiceTest.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class OkHttpSongsterrVideoPointsServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: OkHttpSongsterrVideoPointsService

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        service = OkHttpSongsterrVideoPointsService(
            client = OkHttpClient(),
            baseUrl = base,
        )
    }

    @After fun tearDown() { server.shutdown() }

    private fun fixture(name: String): String =
        File("src/test/resources/songsterr/$name").readText()

    @Test fun `happy path returns parsed entries`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("video-points-teen-spirit.json")))
        val result = service.fetch(songId = 269L, revisionId = 6953431L)
        assertEquals(3, result.size)
        assertEquals("zYxkezUr8MQ", result[0].youtubeVideoId)
        assertEquals(listOf(-0.15, 2.5, 4.68, 6.86, 8.95), result[0].points)
        assertEquals("alternative", result[0].feature)
        assertEquals(null, result[2].feature)
    }

    @Test fun `HTTP 404 returns empty list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = service.fetch(songId = 1L, revisionId = 2L)
        assertTrue("expected empty list, got $result", result.isEmpty())
    }

    @Test fun `malformed JSON returns empty list`() = runTest {
        server.enqueue(MockResponse().setBody("not json"))
        val result = service.fetch(songId = 1L, revisionId = 2L)
        assertTrue(result.isEmpty())
    }

    @Test fun `connection failure returns empty list`() = runTest {
        server.shutdown()
        val result = service.fetch(songId = 1L, revisionId = 2L)
        assertTrue(result.isEmpty())
    }

    @Test fun `URL is constructed correctly`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        service.fetch(songId = 269L, revisionId = 6953431L)
        val req = server.takeRequest()
        assertEquals("/api/video-points/269/6953431/list", req.path)
    }

    @Test fun `entry with missing optional fields still parses`() = runTest {
        // Songsterr sometimes omits feature; ignoreUnknownKeys also matters.
        server.enqueue(
            MockResponse().setBody(
                """[{"videoId":"abc","points":[0.0,1.0,2.0]}]""",
            ),
        )
        val result = service.fetch(songId = 1L, revisionId = 2L)
        assertEquals(1, result.size)
        assertEquals("abc", result[0].youtubeVideoId)
        assertEquals(listOf(0.0, 1.0, 2.0), result[0].points)
        assertEquals(null, result[0].feature)
    }
}
```

- [ ] **Step 2: Run the test — expect failure (class missing)**

```bash
./gradlew :app:testDebugUnitTest --tests "*OkHttpSongsterrVideoPointsServiceTest*" 2>&1 | tail -5
```

Expected: compile error — `OkHttpSongsterrVideoPointsService` not defined.

- [ ] **Step 3: Implement the service**

Create `app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrVideoPointsService.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG = "DrumsSgs"
private const val DEFAULT_BASE_URL = "https://www.songsterr.com"

class OkHttpSongsterrVideoPointsService(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : SongsterrVideoPointsService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(songId: Long, revisionId: Long): List<VideoPointEntry> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/video-points/$songId/$revisionId/list"
            val body = try {
                getOrNull(url)
            } catch (e: IOException) {
                Log.w(TAG, "video-points IOException for ($songId, $revisionId)", e)
                return@withContext emptyList()
            } ?: return@withContext emptyList()

            try {
                json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
                    parseEntry(el.jsonObject)
                }
            } catch (e: Exception) {
                Log.w(TAG, "video-points parse error", e)
                emptyList()
            }
        }

    private fun parseEntry(obj: JsonObject): VideoPointEntry? {
        val videoId = obj["videoId"]?.jsonPrimitive?.contentOrNull ?: return null
        val pointsArr = obj["points"]?.jsonArray ?: return null
        val points = pointsArr.mapNotNull { it.jsonPrimitive.doubleOrNull }
        if (points.size < 2) return null  // PointsBasedTimeMap requires at least 2.
        val feature = obj["feature"]?.jsonPrimitive?.contentOrNull
        return VideoPointEntry(youtubeVideoId = videoId, points = points, feature = feature)
    }

    private fun getOrNull(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "DrumsApp/0.1 (Android)")
            .header("Referer", "https://www.songsterr.com/")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "GET $url → HTTP ${resp.code}")
                null
            } else resp.body?.string()
        }
    }
}
```

- [ ] **Step 4: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*OkHttpSongsterrVideoPointsServiceTest*" 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL, 6 tests pass.

- [ ] **Step 5: Add a fake for downstream tests**

Create `app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrVideoPointsService.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

class FakeSongsterrVideoPointsService(
    private val response: List<VideoPointEntry> = emptyList(),
) : SongsterrVideoPointsService {

    var lastSongId: Long? = null
        private set
    var lastRevisionId: Long? = null
        private set

    override suspend fun fetch(songId: Long, revisionId: Long): List<VideoPointEntry> {
        lastSongId = songId
        lastRevisionId = revisionId
        return response
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrVideoPointsService.kt \
        app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrVideoPointsServiceTest.kt \
        app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrVideoPointsService.kt
git commit -m "Implement OkHttpSongsterrVideoPointsService + tests"
```

---

### Task 7: Provide SongsterrVideoPointsService via Hilt

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/di/AppModule.kt`

- [ ] **Step 1: Add the provider**

Open `app/src/main/java/ph/nextbank/drums/di/AppModule.kt`. Near the existing `provideSongsterrTabFetcher` provider (around line 55–57), add the import (top of file, alongside other songsterr imports):

```kotlin
import ph.nextbank.drums.audio.songsterr.OkHttpSongsterrVideoPointsService
import ph.nextbank.drums.audio.songsterr.SongsterrVideoPointsService
```

And add the provider method inside the module object:

```kotlin
    @Provides
    @Singleton
    fun provideSongsterrVideoPointsService(): SongsterrVideoPointsService =
        OkHttpSongsterrVideoPointsService()
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/di/AppModule.kt
git commit -m "Provide SongsterrVideoPointsService via Hilt"
```

---

# Milestone 2 — Persistence (Tasks 8–10)

Add `videoPoints` to the Song domain and storage. Migration v5 adds the column. Don't wire it into the add-flow yet — that's Milestone 3.

### Task 8: Add videoPoints field to Song domain model

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/data/model/Song.kt`

- [ ] **Step 1: Add the field**

Open `app/src/main/java/ph/nextbank/drums/data/model/Song.kt`. After the existing `songsterrRevisionId` field (around line 28), add:

```kotlin
    /**
     * Songsterr-curated YouTube videos with per-bar sync timestamps. null = no
     * sync data available; the player falls back to legacy YouTube search and
     * constant-BPM playback.
     */
    val videoPoints: List<ph.nextbank.drums.audio.songsterr.VideoPointEntry>? = null,
```

(Add `import ph.nextbank.drums.audio.songsterr.VideoPointEntry` at the top if you prefer; the qualified name above works either way.)

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL (existing call sites use named args and won't break since the new field has a default).

- [ ] **Step 3: Do not commit yet** — Task 9 completes the persistence layer.

---

### Task 9: Add videoPointsJson column + DB migration v5

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt`

- [ ] **Step 1: Add the column to SongEntity**

Open `app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt`. After the existing `songsterrRevisionId` column (line 27), add:

```kotlin
    val videoPointsJson: String?,
```

Add imports at the top of the file:

```kotlin
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ph.nextbank.drums.audio.songsterr.VideoPointEntry
```

Update the `toSong()` method — after the existing `songsterrRevisionId = …` line, add:

```kotlin
        videoPoints = decodeVideoPoints(videoPointsJson),
```

Update the `fromSong()` method — after the existing `songsterrRevisionId = …` line, add:

```kotlin
            videoPointsJson = encodeVideoPoints(s.videoPoints),
```

Add the encoder/decoder helpers inside the `companion object`, after `decodeBlocklist`:

```kotlin
        private val videoPointsJson = Json { ignoreUnknownKeys = true }

        internal fun encodeVideoPoints(entries: List<VideoPointEntry>?): String? =
            entries?.let { videoPointsJson.encodeToString(it) }

        internal fun decodeVideoPoints(s: String?): List<VideoPointEntry>? =
            s?.let { runCatching { videoPointsJson.decodeFromString<List<VideoPointEntry>>(it) }.getOrNull() }
```

- [ ] **Step 2: Bump DB version + add MIGRATION_4_5**

Open `app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt`. Change line 14:

```kotlin
@Database(entities = [SongEntity::class], version = 5, exportSchema = false)
```

After the existing `MIGRATION_3_4` block (line 41), add:

```kotlin
        /** v4 → v5: added videoPointsJson column for Songsterr-curated YouTube sync. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN videoPointsJson TEXT")
            }
        }
```

Update the `addMigrations(...)` call (line 45) to include the new migration:

```kotlin
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the full unit-test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests still pass (the new field defaults to null for existing Song constructions in tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/model/Song.kt \
        app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt \
        app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt
git commit -m "Persist videoPoints on Song (DB migration v5)"
```

---

### Task 10: Add repository / DAO method for updating videoPoints

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/data/db/SongDao.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt`

We don't strictly need this for the add-flow (it persists via the existing `insert` path), but the Player-side "Try another video" cycling will need to clear/update videoPoints on the persisted row when behavior diverges from the add-time list. Adding the repo method now keeps Milestone 4 thin.

- [ ] **Step 1: Inspect the existing repo pattern**

```bash
grep -n "updateYoutubeVideoId\|updateYoutubeOffset\|@Query\|fun update" app/src/main/java/ph/nextbank/drums/data/db/SongDao.kt
grep -n "updateYoutubeVideoId\|fun update" app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt
```

Note the pattern. The DAO has `@Query("UPDATE songs SET … WHERE id = :id")` methods; the Repository wraps them with simple `suspend` methods.

- [ ] **Step 2: Add updateVideoPointsJson to SongDao**

Open `app/src/main/java/ph/nextbank/drums/data/db/SongDao.kt`. Following the existing `updateYoutubeVideoId` (or similar) method, add:

```kotlin
    @androidx.room.Query("UPDATE songs SET videoPointsJson = :json WHERE id = :id")
    suspend fun updateVideoPointsJson(id: String, json: String?)
```

- [ ] **Step 3: Add updateVideoPoints to SongRepository**

Open `app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt`. Following `updateYoutubeVideoId` (or wherever Songsterr-aware methods live), add:

```kotlin
    suspend fun updateVideoPoints(id: String, entries: List<ph.nextbank.drums.audio.songsterr.VideoPointEntry>?) {
        dao.updateVideoPointsJson(id, ph.nextbank.drums.data.db.SongEntity.encodeVideoPoints(entries))
    }
```

Make sure `SongEntity.encodeVideoPoints` is visible (`internal` works since both are in the same module).

- [ ] **Step 4: Compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/db/SongDao.kt \
        app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt
git commit -m "Add updateVideoPoints repo+dao method"
```

---

# Milestone 3 — Add-flow integration (Tasks 11–12)

Wire the points fetch into the add flow so newly-added songs are persisted with the points list.

### Task 11: YouTubeSearchService.fetchMeta(videoId)

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeSearchService.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeYouTubeSearchService.kt`

Add a method that returns `SearchResult` metadata for a known videoId — needed so the Confirming dialog can render title/channel/thumbnail when the videoId came from Songsterr's points list.

- [ ] **Step 1: Add the interface method**

Open `app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeSearchService.kt`. After the existing `getAudioStreamUrl` declaration, add:

```kotlin
    /**
     * Look up title / channel / duration / thumbnail for [videoId]. Used when
     * the videoId came from somewhere other than search (e.g. Songsterr's
     * video-points list) but we still want to render the Confirming dialog
     * with familiar metadata. Returns null on extraction failure.
     */
    suspend fun fetchMeta(videoId: String): SearchResult?
```

- [ ] **Step 2: Implement in NewPipeYouTubeSearchService**

Open `app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeYouTubeSearchService.kt`. Inspect the existing `getAudioStreamUrl` method to copy its `StreamInfo.getInfo(...)` pattern. Add a sibling method, near the bottom of the class:

```kotlin
    override suspend fun fetchMeta(videoId: String): SearchResult? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!ensureInit()) return@withContext null
            runCatching {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val info = org.schabi.newpipe.extractor.stream.StreamInfo
                    .getInfo(org.schabi.newpipe.extractor.ServiceList.YouTube, watchUrl)
                SearchResult(
                    videoId = videoId,
                    title = info.name ?: "",
                    channelTitle = info.uploaderName ?: "",
                    durationSec = info.duration.toInt(),
                    thumbnailUrl = info.thumbnails?.firstOrNull()?.url ?: "",
                )
            }.getOrNull()
        }
```

(The fully-qualified names mirror the existing file's pattern — feel free to consolidate imports if the file already imports them.)

- [ ] **Step 3: Compile + run existing tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL. No existing tests cover NewPipe (network dependency), so they pass unchanged.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeSearchService.kt \
        app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeYouTubeSearchService.kt
git commit -m "Add YouTubeSearchService.fetchMeta(videoId)"
```

---

### Task 12: AddSongViewModel fetches points in parallel + persists

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt`
- Modify: `app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt`

After the user picks a Songsterr result, fetch the points list in parallel with the tab fetch. Persist them (possibly empty) on the new Song.

- [ ] **Step 1: Inspect the existing ViewModel structure**

```bash
grep -n "fun onResultClicked\|handleFetchResult\|persistSong\|@Inject constructor\|class AddSongViewModel" app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt
```

Note: the existing flow is sequential — `tabFetcher.fetchDrumTrack(songId)` → `parser.parse(...)` → `persistSong(...)`. We need to add a parallel `videoPointsService.fetch(...)` and thread its result into `persistSong`.

- [ ] **Step 2: Write the failing tests**

Open `app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt`. Find the existing `setUp()` and note how it wires dependencies (likely via `FakeSongsterrTabFetcher`, fake parser, fake repo). Add a new test-class-level field and pass it to the ViewModel constructor — same pattern as the existing fakes:

```kotlin
    private lateinit var pointsService: ph.nextbank.drums.audio.songsterr.FakeSongsterrVideoPointsService
```

In `setUp()`, initialize it before constructing the ViewModel:

```kotlin
        pointsService = ph.nextbank.drums.audio.songsterr.FakeSongsterrVideoPointsService()
```

And pass `pointsService` into the `AddSongViewModel(...)` constructor call — placed alongside `tabFetcher`/`parser`/`repo`.

Then add the two new tests at the end of the class:

```kotlin
    @Test fun `synced add persists videoPoints on the new song`() = runTest {
        // Arrange: tab fetcher returns a parseable result; points service returns 2 entries.
        val sampleEntries = listOf(
            ph.nextbank.drums.audio.songsterr.VideoPointEntry(
                youtubeVideoId = "abc12345678",
                points = listOf(0.0, 2.0, 4.0),
                feature = "alternative",
            ),
            ph.nextbank.drums.audio.songsterr.VideoPointEntry(
                youtubeVideoId = "def12345678",
                points = listOf(1.0, 3.0, 5.0),
                feature = null,
            ),
        )
        pointsService = ph.nextbank.drums.audio.songsterr.FakeSongsterrVideoPointsService(sampleEntries)
        // Reconstruct vm with the populated fake — exact constructor mirrors setUp().
        vm = AddSongViewModel(searchService, tabFetcher, parser, pointsService, repo)

        // Act: pick a result whose songId we know.
        vm.onResultClicked(makeResult(songId = 50420L))
        advanceUntilIdle()

        // Assert: the persisted song carries the entries.
        val saved = repo.lastInserted!!
        assertEquals(2, saved.videoPoints?.size)
        assertEquals("abc12345678", saved.videoPoints!![0].youtubeVideoId)
        assertEquals(50420L, pointsService.lastSongId)
    }

    @Test fun `unsynced add persists null videoPoints`() = runTest {
        pointsService = ph.nextbank.drums.audio.songsterr.FakeSongsterrVideoPointsService(emptyList())
        vm = AddSongViewModel(searchService, tabFetcher, parser, pointsService, repo)

        vm.onResultClicked(makeResult(songId = 50420L))
        advanceUntilIdle()

        val saved = repo.lastInserted!!
        assertEquals(null, saved.videoPoints)
    }
```

Adjust `makeResult` if it doesn't already exist — the existing tests likely have a helper. If not, construct a minimal `SongsterrResult` inline. Look up the existing test patterns first.

- [ ] **Step 3: Run the tests — expect compile failures (constructor signature)**

```bash
./gradlew :app:testDebugUnitTest --tests "*AddSongViewModelTest*" 2>&1 | tail -10
```

Expected: compile error — `AddSongViewModel` doesn't accept `pointsService`.

- [ ] **Step 4: Add the dependency + plumbing to AddSongViewModel**

Open `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt`. Update the `@HiltViewModel`-annotated class's primary constructor to inject `SongsterrVideoPointsService`. Example (the exact param order should match where Hilt-injected params live today — typically alongside `tabFetcher`):

```kotlin
@HiltViewModel
class AddSongViewModel @Inject constructor(
    private val searchService: SongsterrSearchService,
    private val tabFetcher: SongsterrTabFetcher,
    private val parser: DrumTabParser,
    private val pointsService: SongsterrVideoPointsService,
    private val repo: SongRepository,
) : ViewModel() {
```

Add the import: `import ph.nextbank.drums.audio.songsterr.SongsterrVideoPointsService`.

Inside the existing `onResultClicked` flow, change the section that calls `tabFetcher.fetchDrumTrack(...)` so it also fetches points once the revisionId is known. `SongsterrResult` does not expose `revisionId` (verified — it's not in the data class), so the points call must come AFTER the tab fetch:

```kotlin
            val fetched = tabFetcher.fetchDrumTrack(result.songId)
            val points = when (fetched) {
                is FetchResult.Success -> pointsService.fetch(result.songId, fetched.data.revisionId)
                else -> emptyList()
            }
            handleFetchResult(result, fetched, points)
```

(One extra round-trip serialized after the tab fetch, ~100ms. Add `import ph.nextbank.drums.audio.songsterr.VideoPointEntry` and `import ph.nextbank.drums.audio.songsterr.SongsterrVideoPointsService` at the top.)

Change `handleFetchResult` to take the points list and thread it into `persistSong`:

```kotlin
    private suspend fun handleFetchResult(
        result: SongsterrResult,
        fetched: FetchResult,
        points: List<VideoPointEntry>,
    ) {
        when (fetched) {
            is FetchResult.Success -> {
                when (val parsed = parser.parse(fetched.data)) {
                    is ParseResult.Success -> persistSong(result, fetched.data.revisionId, parsed, points)
                    // ... (other branches unchanged)
                }
            }
            // ... (other branches unchanged)
        }
    }
```

Change `persistSong` to accept and store the points:

```kotlin
    private suspend fun persistSong(
        result: SongsterrResult,
        revisionId: Long,
        parsed: ParseResult.Success,
        points: List<VideoPointEntry>,
    ) {
        val song = Song(
            // ... existing fields ...
            videoPoints = points.takeIf { it.isNotEmpty() },
        )
        repo.insert(song)
        _events.tryEmit(AddSongEvent.SongAdded(song.id))
    }
```

(Add `import ph.nextbank.drums.audio.songsterr.VideoPointEntry` at the top.)


- [ ] **Step 5: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*AddSongViewModelTest*" 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the full suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt \
        app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt
git commit -m "AddSongViewModel: fetch + persist Songsterr video-points"
```

---

# Milestone 4 — Player synced path (Tasks 13–15)

When the player opens a synced song, skip the YouTube search and show the Confirming dialog populated from the first points entry. "Try another" cycles through the list; exhausting it falls through to legacy search. Playback uses `PointsBasedTimeMap`.

### Task 13: PlayerViewModel synced branch in init + acceptCandidate

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`
- Modify: `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Open `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`. Find where the existing tests construct a fake `YouTubeSearchService`. Add a new test:

```kotlin
    @Test fun `synced song shows Confirming with first entry without searching`() = runTest {
        val entries = listOf(
            ph.nextbank.drums.audio.songsterr.VideoPointEntry(
                youtubeVideoId = "syncedAbc12",
                points = listOf(0.0, 2.0, 4.0, 6.0),
                feature = "alternative",
            ),
        )
        val song = makeSong(id = "song1", youtubeVideoId = null, videoPoints = entries)
        repo.preload(song)
        // Fake searchService.fetchMeta returns a known SearchResult; findFor must NOT be called.
        searchService.fetchMetaResponse = SearchResult(
            videoId = "syncedAbc12",
            title = "Synced Video Title",
            channelTitle = "Channel",
            durationSec = 240,
            thumbnailUrl = "https://example/thumb.jpg",
        )

        vm = PlayerViewModel(repo, bank, searchService, adapterFactory, SavedStateHandle(mapOf("songId" to "song1")))
        advanceUntilIdle()

        val phase = vm.state.value.phase
        assertTrue("expected Confirming, got $phase", phase is PlayerPhase.Confirming)
        assertEquals("syncedAbc12", (phase as PlayerPhase.Confirming).candidate.videoId)
        assertEquals(0, searchService.findForCalls)  // legacy path NOT taken.
    }
```

The fake `searchService` will need a `fetchMetaResponse` field and a `findForCalls` counter — extend the existing fake. Make `makeSong` accept `videoPoints = null` as a defaultable param (the existing helper probably doesn't have it yet — add it).

- [ ] **Step 2: Run the test — expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "*PlayerViewModelTest*" 2>&1 | tail -10
```

Expected: either compile error (extension fields missing on fake) or assertion failure.

- [ ] **Step 3: Implement the synced branch in PlayerViewModel.init**

Open `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`. The current `init` block (lines 73–87) reads:

```kotlin
    init {
        viewModelScope.launch {
            val s = repo.findById(songId) ?: return@launch
            _state.value = _state.value.copy(
                song = s,
                youtubeOffsetMs = s.youtubeOffsetMs,
            )
            val cachedId = s.youtubeVideoId
            if (cachedId != null) {
                startYouTubePlayback(cachedId)
            } else {
                runSearch(s, s.youtubeBlocklist.toSet())
            }
        }
    }
```

Replace it with:

```kotlin
    /** Index into song.videoPoints!! of the currently-displayed candidate. */
    private var syncedCandidateIdx: Int = 0

    init {
        viewModelScope.launch {
            val s = repo.findById(songId) ?: return@launch
            _state.value = _state.value.copy(
                song = s,
                youtubeOffsetMs = s.youtubeOffsetMs,
            )
            val cachedId = s.youtubeVideoId
            val syncedEntries = s.videoPoints
            when {
                cachedId != null -> startYouTubePlayback(cachedId)
                !syncedEntries.isNullOrEmpty() -> showSyncedCandidate(s, idx = 0)
                else -> runSearch(s, s.youtubeBlocklist.toSet())
            }
        }
    }

    private suspend fun showSyncedCandidate(song: Song, idx: Int) {
        val entries = song.videoPoints ?: return
        if (idx !in entries.indices) {
            // Exhausted — fall through to legacy YouTube search.
            _events.tryEmit(PlayerEvent.Toast("No more synced videos — searching YouTube."))
            runSearch(song, song.youtubeBlocklist.toSet())
            return
        }
        syncedCandidateIdx = idx
        _state.value = _state.value.copy(phase = PlayerPhase.Searching)
        val meta = searchService.fetchMeta(entries[idx].youtubeVideoId)
        if (meta == null) {
            // Skip and try the next one.
            showSyncedCandidate(song, idx + 1)
            return
        }
        _state.value = _state.value.copy(phase = PlayerPhase.Confirming(meta))
    }
```

Add `import ph.nextbank.drums.data.model.Song` if not already present.

- [ ] **Step 4: Run the test — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*PlayerViewModelTest*" 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL, the new test passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt \
        app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt
git commit -m "PlayerViewModel: enter Confirming with Songsterr-synced video on init"
```

---

### Task 14: tryAnotherVideo cycles synced entries

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`
- Modify: `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `PlayerViewModelTest.kt`:

```kotlin
    @Test fun `tryAnotherVideo on synced song advances to next entry`() = runTest {
        val entries = listOf(
            ph.nextbank.drums.audio.songsterr.VideoPointEntry("aaa11111111", listOf(0.0, 2.0), null),
            ph.nextbank.drums.audio.songsterr.VideoPointEntry("bbb22222222", listOf(0.0, 2.0), null),
        )
        val song = makeSong(id = "song2", youtubeVideoId = null, videoPoints = entries)
        repo.preload(song)
        searchService.fetchMetaResponse = SearchResult("aaa11111111", "First", "Ch", 200, "")

        vm = PlayerViewModel(repo, bank, searchService, adapterFactory, SavedStateHandle(mapOf("songId" to "song2")))
        advanceUntilIdle()
        // First candidate shown.
        assertEquals("aaa11111111", (vm.state.value.phase as PlayerPhase.Confirming).candidate.videoId)

        // Reconfigure fake to return the second entry's meta on next fetchMeta call.
        searchService.fetchMetaResponse = SearchResult("bbb22222222", "Second", "Ch", 200, "")
        vm.tryAnotherVideo()
        advanceUntilIdle()

        assertEquals("bbb22222222", (vm.state.value.phase as PlayerPhase.Confirming).candidate.videoId)
    }

    @Test fun `exhausting synced entries falls through to YouTube search`() = runTest {
        val entries = listOf(
            ph.nextbank.drums.audio.songsterr.VideoPointEntry("only11111111", listOf(0.0, 2.0), null),
        )
        val song = makeSong(id = "song3", youtubeVideoId = null, videoPoints = entries)
        repo.preload(song)
        searchService.fetchMetaResponse = SearchResult("only11111111", "Only", "Ch", 200, "")
        searchService.findForResponse = SearchResult("legacyXYZ12", "Legacy", "Ch", 200, "")

        vm = PlayerViewModel(repo, bank, searchService, adapterFactory, SavedStateHandle(mapOf("songId" to "song3")))
        advanceUntilIdle()

        vm.tryAnotherVideo()  // exhausts the single entry → triggers runSearch
        advanceUntilIdle()

        assertTrue(searchService.findForCalls >= 1)
        assertEquals("legacyXYZ12", (vm.state.value.phase as PlayerPhase.Confirming).candidate.videoId)
    }
```

- [ ] **Step 2: Run the test — expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "*PlayerViewModelTest*" 2>&1 | tail -10
```

Expected: assertion failure — `tryAnotherVideo` still uses the legacy retry path.

- [ ] **Step 3: Branch tryAnotherVideo on synced mode**

Open `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`. Replace the existing `tryAnotherVideo` (line 174):

```kotlin
    fun tryAnotherVideo() = retry(autoAccept = false)
```

With:

```kotlin
    fun tryAnotherVideo() {
        val song = _state.value.song
        val syncedEntries = song?.videoPoints
        if (song != null && !syncedEntries.isNullOrEmpty() && syncedCandidateIdx < syncedEntries.size) {
            // Synced path: cycle to the next entry (or fall through if exhausted).
            viewModelScope.launch { showSyncedCandidate(song, syncedCandidateIdx + 1) }
        } else {
            retry(autoAccept = false)
        }
    }
```

- [ ] **Step 4: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*PlayerViewModelTest*" 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt \
        app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt
git commit -m "PlayerViewModel: cycle synced entries on Try another"
```

---

### Task 15: Wire PointsBasedTimeMap into startYouTubePlayback

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`
- Modify: `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`

When the accepted video corresponds to a Songsterr-synced entry, build a `PointsBasedTimeMap` from its `points` and pass it to `YouTubePlaybackSource`. Otherwise keep today's `ConstantBpmTimeMap`.

- [ ] **Step 1: Write the failing test**

Add to `PlayerViewModelTest.kt`. The existing test setup likely has a `FakeYouTubeAdapterFactory` that captures the adapter; extend it to also capture the constructed `TimeMap`. If not, capture indirectly by asserting on slot output after feeding `onCurrentSecond` through the fake adapter:

```kotlin
    @Test fun `accepting a synced candidate uses PointsBasedTimeMap`() = runTest {
        val entries = listOf(
            ph.nextbank.drums.audio.songsterr.VideoPointEntry(
                youtubeVideoId = "syncedV1234",
                points = listOf(0.0, 2.0, 4.0, 6.0),  // 4 bars × default 16 slots/bar = 64 totalSlots
                feature = null,
            ),
        )
        val song = makeSong(
            id = "song4",
            youtubeVideoId = null,
            videoPoints = entries,
            bpm = 60,  // intentionally wrong vs the points (would yield different slots if ConstantBpmTimeMap were used)
            totalBars = 4,
        )
        repo.preload(song)
        searchService.fetchMetaResponse = SearchResult("syncedV1234", "Synced", "Ch", 240, "")
        searchService.streamUrlResponse = "fake-stream-url"

        vm = PlayerViewModel(repo, bank, searchService, adapterFactory, SavedStateHandle(mapOf("songId" to "song4")))
        advanceUntilIdle()
        vm.acceptCandidate()
        advanceUntilIdle()

        // Now feed the fake adapter onCurrentSecond(1.0) — halfway through bar 0.
        // With points [0,2,4,6] and slotsPerBar=16, slot at t=1 should be 8.
        // (If ConstantBpmTimeMap had been used: at 60 BPM × 4 slots/beat, t=1 → slot=4.)
        adapterFactory.lastListener?.onCurrentSecond(1.0f)
        advanceUntilIdle()
        assertEquals(8, vm.state.value.activeSlotIndex)
    }
```

- [ ] **Step 2: Run the test — expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "*PlayerViewModelTest*" 2>&1 | tail -10
```

Expected: assertion failure — `activeSlotIndex` is 4 (ConstantBpmTimeMap), not 8 (PointsBasedTimeMap).

- [ ] **Step 3: Build the right TimeMap in startYouTubePlayback**

Open `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`. The block that constructs the time map + source (modified in Task 3) currently always uses `ConstantBpmTimeMap`. Change it to pick `PointsBasedTimeMap` when the current synced entry has points whose videoId matches the one we just accepted:

```kotlin
        val adapter = adapterFactory.create(streamUrl)

        val syncedEntry = s.videoPoints?.firstOrNull { it.youtubeVideoId == videoId }
        val timeMap: ph.nextbank.drums.audio.TimeMap = if (syncedEntry != null) {
            ph.nextbank.drums.audio.PointsBasedTimeMap(
                points = syncedEntry.points,
                slotsPerBar = s.slotsPerBar,
                totalSlots = s.totalBars * s.slotsPerBar,
            )
        } else {
            ph.nextbank.drums.audio.ConstantBpmTimeMap(
                bpm = s.bpm,
                totalSlots = s.totalBars * s.slotsPerBar,
                slotsPerBeat = s.slotsPerBar / s.timeSig.first,
            )
        }

        val src = YouTubePlaybackSource(
            timeMap = timeMap,
            adapter = adapter,
            initialOffsetMs = s.youtubeOffsetMs,
        )
```

- [ ] **Step 4: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "*PlayerViewModelTest*" 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt \
        app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt
git commit -m "PlayerViewModel: use PointsBasedTimeMap for synced playback"
```

---

# Milestone 5 — End-to-end verification (Task 16)

### Task 16: Smoke test on device

**Files:** (none — verification only)

- [ ] **Step 1: Fresh-install run**

```bash
export ANDROID_HOME=/home/sara/Android/Sdk JAVA_HOME=/home/sara/Downloads/android-studio-panda4-patch1-linux/android-studio/jbr
$ANDROID_HOME/platform-tools/adb shell pm clear ph.nextbank.drums
./gradlew :app:installDebug
$ANDROID_HOME/platform-tools/adb shell am start -n ph.nextbank.drums/.MainActivity
```

- [ ] **Step 2: Walk the synced golden path**

In the app:
1. Tap `+`.
2. Search "smells like teen spirit". Tap the Nirvana result.
3. Wait for the "Loading tab…" overlay to clear.
4. Confirming dialog appears with a Songsterr-curated video (likely Nirvana's official Vevo). Tap "Use this video".
5. Audio plays. **The playhead and audible drum hits should line up within ~50ms** — no perceptible drift through at least the first chorus.

Expected: no manual nudge needed. If drift is visible, capture a screen recording and an `adb logcat` clip of `DrumsSgs` lines for diagnosis.

- [ ] **Step 3: Walk the "Try another" path**

1. From the same Confirming state (or re-open the song), tap "Try another".
2. A different YouTube video appears — different title/thumbnail.
3. Accept. Audio plays in sync with the same tab.

- [ ] **Step 4: Walk the legacy path**

1. Tap `+`. Search "in the air tonight phil collins". Tap the result.
2. Loading overlay → Confirming dialog with Phil Collins's video.
3. Accept and play. **Constant-BPM playback should work just like today** (no points entries available for this song). Manual offset nudge still functions.

- [ ] **Step 5: Run the full unit-test suite as a final regression**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: No code commit needed** — if a sync bug surfaces, fix it in a follow-up task; this milestone is verification.

---

## Risks and mitigations (reference)

| Risk | Mitigation |
|------|------------|
| Songsterr changes the `/api/video-points` shape | Same fragility we already accept for `/api/meta`. Service returns empty list on parse failure → silent legacy fallback. Fixture-based test catches schema changes during CI. |
| NewPipe `StreamInfo.getInfo` rate-limits or fails on metadata fetch | `fetchMeta` returns null → caller skips entry and tries the next. After exhausting all entries, legacy YouTube search takes over. |
| User's chosen Songsterr-curated video gets blocked / age-gated / embed-restricted | Existing `MAX_EXTRACTION_ATTEMPTS` (3) retry loop carries over. After 3 attempts across both synced and legacy modes, falls back to synth — same as today. |
| 12 KB per song of points JSON inflates DB size | Acceptable at library sizes of dozens of songs. If we ever support thousands, revisit storage (per-entry rows, lazy load). |
| Songsterr's points list ordering ≠ what user expects | Mirror the web player by trusting the raw order. If user reports bad picks frequently, add a re-ranking step (e.g. prefer `feature=null` or `feature=alternative` for studio-version songs). |
