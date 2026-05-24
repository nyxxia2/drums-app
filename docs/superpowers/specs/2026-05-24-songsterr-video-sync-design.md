# Drums App — Songsterr Video Sync Design

**Goal:** Drum tab and YouTube audio stay in sync automatically, without the user having to nudge the offset slider, by consuming Songsterr's pre-computed per-video bar timestamps.

**Product framing:** Today the player picks a YouTube video via a general search (Phase 2), runs the tab clock at constant BPM, and exposes an `± Oms` nudger so the user can manually align the two. Result: most pop/rock songs drift within a few bars because (a) the picked YouTube video's intro length doesn't match the tab's bar 1, and (b) real recordings have small tempo variations. This redesign uses Songsterr's own sync data — published alongside the tab data — to remove the manual step.

---

## Why this approach

Songsterr publishes `/api/video-points/{songId}/{revisionId}/list`. For every YouTube video they've aligned to a tab (manually or automatically — we don't need to know which), the response includes an array of **bar-start timestamps in video-time** (seconds).

**Definition:** in this spec, a "synced song" is one where `Song.videoPoints` is non-null and non-empty — i.e. Songsterr returned at least one aligned video for it. For "Smells Like Teen Spirit" alone there are 111 entries (one per known YouTube cut: official, alternative angles, lessons, etc.), each with ~143 points corresponding to the song's 143 bars.

That's exactly the data we need: a piecewise-linear map from video time to bar position. No DSP, no beat detection, no fingerprinting. We pick a synced video as the audio source and the existing manual offset stays for last-mile fine-tuning.

**Alternatives considered and rejected:**
- **Beat-detect the YouTube audio on-device.** Heavy DSP, model-dependent, fragile on covers.
- **Cross-correlate a synthesized tab preview against the audio envelope.** Doable in Kotlin (FFT) but tens-of-MB of code/data for marginal win over Songsterr's hand-curated data.
- **Keep current flow, surface a quick auto-calibrate "tap along" button.** Better than today but still manual. Sara wants automatic.
- **Wait for a tempo estimation library to mature on Android.** Same issue — drift from start offset is the dominant problem, not BPM.

**Risk:** Songsterr could change the points endpoint shape. We accept this — same fragility we already accept for the tab endpoint — and mitigate with fixture-based tests.

---

## Architecture

```
┌──────────────────────┐    fetch video-points list    ┌──────────────────┐
│  AddSongViewModel    │ ──────────────────────────►   │ Songsterr API    │
│  (synced add flow)   │ ◄─────────────────────────────│ /video-points/…  │
└──────────────────────┘   List<VideoPointEntry>       └──────────────────┘
            │                  (videoId, points[])
            │ persist
            ▼
┌──────────────────────┐
│  Song.videoPoints    │   ← new nullable column on the Song entity
└──────────────────────┘
            │
            ▼
┌──────────────────────┐     show first entry         ┌──────────────────┐
│  PlayerViewModel     │ ────────────────────────►    │ Confirming dialog│
│  (synced path)       │ ◄────────────────────────────│ (Use this/Try…)  │
└──────────────────────┘   user accepts / cycles      └──────────────────┘
            │
            ▼ inject TimeMap into playback source
┌──────────────────────────────────────────────────────────────────────────┐
│  YouTubePlaybackSource(timeMap)                                          │
│    onCurrentSecond(t) → slot = timeMap.slotAt(t + offset/1000)           │
│                                                                          │
│   TimeMap implementations:                                               │
│     • ConstantBpmTimeMap  ← today's math (legacy songs, fallback)        │
│     • PointsBasedTimeMap  ← interpolates within each bar from points[]   │
└──────────────────────────────────────────────────────────────────────────┘
```

The data path is one-way: Songsterr → Song row → Player. The clock change is the only behavioral change at playback; everything else is plumbing.

---

## Components

### New (3 files)

**`audio/songsterr/SongsterrVideoPointsService.kt`** — interface + OkHttp implementation.
```kotlin
interface SongsterrVideoPointsService {
    suspend fun fetch(songId: Long, revisionId: Long): List<VideoPointEntry>
}

data class VideoPointEntry(
    val youtubeVideoId: String,
    val points: List<Double>,
    val feature: String?,  // "alternative", "solo", "backing", or null
)
```
On network failure, malformed JSON, or HTTP error: returns empty list (callers treat as "no sync available"). On success: preserves Songsterr's list order — that order is what the Songsterr web player itself uses to pick a default video, so we mirror it.

**`audio/TimeMap.kt`** — small abstraction layered between video time and tab slot.
```kotlin
interface TimeMap {
    val totalSlots: Int
    fun slotAt(videoSec: Float): Float
    fun videoSecAt(slot: Float): Float
}

class ConstantBpmTimeMap(bpm: Int, totalSlots: Int, slotsPerBeat: Int) : TimeMap
class PointsBasedTimeMap(points: List<Double>, slotsPerBar: Int, totalSlots: Int) : TimeMap
```

`PointsBasedTimeMap.slotAt(t)`:
- Binary search for bar `i` where `points[i] ≤ t < points[i+1]`.
- `t < points[0]` → returns 0 (clamped, drums haven't started).
- `t ≥ points.last()` → returns `totalSlots − 1` (clamped, song over).
- Inside a bar: `fraction = (t − points[i]) / (points[i+1] − points[i])`, then `slot = (i + fraction) × slotsPerBar`.

`videoSecAt(slot)` is the inverse: convert slot back to video seconds (used by features like seek, looping). Same binary search on bar boundaries, linear interpolation within.

**Method addition to `YouTubeSearchService`:**
```kotlin
suspend fun fetchMeta(videoId: String): SearchResult?
```
Returns title/channel/duration/thumbnail for a known videoId. Backed by NewPipe's `StreamInfo.getInfo`, same as today's `getAudioStreamUrl`. Needed so the Confirming dialog can render a familiar card when the video came from Songsterr (not from a YouTube search).

### Modified

**`data/model/Song.kt` + `data/db/SongEntity.kt`** — new nullable field `videoPoints: List<VideoPointEntry>?`. Stored as a JSON-serialized TEXT column (`videoPointsJson`) via a Room TypeConverter. Migration v5 adds the column.

**`di/AppModule.kt`** — provide `SongsterrVideoPointsService` (one new `@Provides`).

**`ui/add/AddSongViewModel.kt`** — after `SongsterrTabFetcher.fetchDrumTrack` succeeds, also call `SongsterrVideoPointsService.fetch` (in parallel via `async`). Persist the points list (possibly empty) on the new `Song`. The existing parse/persist/navigate flow is otherwise untouched.

**`ui/player/PlayerViewModel.kt`** — in `init`, branch:
- `song.videoPoints` non-empty AND `song.youtubeVideoId` null → enter synced mode:
  - `candidateIdx = 0`
  - `searchService.fetchMeta(videoPoints[0].youtubeVideoId)` → `Confirming(candidate)`
  - On `tryAnotherVideo()`: `candidateIdx++`; if `< videoPoints.size`, fetchMeta the next; otherwise fall through to legacy YouTube search.
- `song.videoPoints` null/empty → legacy path (unchanged: runSearch → Confirming).
- `youtubeVideoId` already set → today's straight-to-playback (unchanged).

On `acceptCandidate()` in synced mode: commit the chosen videoId, build a `PointsBasedTimeMap` from the entry's `points`, hand it to `YouTubePlaybackSource`.

**`audio/playback/YouTubePlaybackSource.kt`** — constructor change. Replace `songBpm`/`slotsPerBeat`/`totalSlots` triplet with a single `timeMap: TimeMap`. The `onCurrentSecond` body becomes:
```kotlin
override fun onCurrentSecond(seconds: Float) {
    val effectiveSec = seconds + offsetMs / 1000f
    val slot = timeMap.slotAt(effectiveSec)
    val clamped = slot.coerceIn(0f, timeMap.totalSlots.toFloat() - 0.001f)
    _currentSlot.value = clamped
    _activeSlotIndex.value = clamped.toInt()
}
```
Today's BPM-based callers wrap their bpm/slot params in a `ConstantBpmTimeMap` — zero behavioral change for unsynced songs.

---

## Data flow

**Add (synced happy path):**
```
User picks Songsterr search result
  ├─ SongsterrTabFetcher.fetchDrumTrack(songId)       ──┐
  └─ SongsterrVideoPointsService.fetch(songId, rev)   ──┤ parallel (async)
                                                         ▼
DrumTabParser.parse(...)
Song persisted: title/artist/bars/… + videoPoints (videoId still null)
Navigate to player
```

**Player open (synced):**
```
PlayerViewModel.init
  song.videoPoints != null && videoId == null
  → candidateIdx = 0
  → searchService.fetchMeta(videoPoints[0].youtubeVideoId)
  → phase = Confirming(candidate)
       │
       ├─ "Use this video" → commit videoId, PointsBasedTimeMap(points), playback
       └─ "Try another"    → candidateIdx++; loop with next entry's meta;
                              if exhausted: fall through to legacy YouTube search
```

**Player open (legacy):**
```
PlayerViewModel.init
  song.videoPoints null/empty
  → runSearch(...)               ← today's Phase 2 path
  → Confirming → accept → ConstantBpmTimeMap → playback
```

**Playback (the actual sync):**
```
YouTube reports onCurrentSecond(t)
  → effectiveSec = t + youtubeOffsetMs/1000
  → slot = timeMap.slotAt(effectiveSec)
       PointsBasedTimeMap interpolates inside bars
       ConstantBpmTimeMap returns effectiveSec × slotsPerSec
  → publish currentSlot, activeSlotIndex
```

The user's `± Oms` nudge stays as `youtubeOffsetMs`, applied uniformly *before* the time map. So even on the synced path, the user can fine-tune ± a few hundred ms if Songsterr's points are slightly off for their particular YouTube cut.

---

## Persistence

New column on `SongEntity`: `videoPointsJson TEXT NULL`. Stored as serialized `List<VideoPointEntry>`. Existing rows get NULL after migration v5.

**Why store the full list (not just the picked entry):** the user may "Try another" later, which needs to cycle through the remaining entries without re-hitting Songsterr. ~12KB per song (111 × 143 doubles × JSON overhead). Acceptable; library is dozens of songs, not thousands.

**TypeConverter:** `kotlinx.serialization.json` already in the project (Phase 3). One pair of `@TypeConverter` methods (List ↔ JSON String).

**Migration v5:** trivial `ALTER TABLE Song ADD COLUMN videoPointsJson TEXT`. No data backfill — existing rows take the legacy playback path.

---

## Error handling & edge cases

| Situation | Behavior |
|---|---|
| Points fetch fails (network/HTTP) during add | Persist `videoPoints = null`. Silent degrade to legacy path. No toast. |
| Empty points list | Same as fetch failure. |
| `fetchMeta(videoId)` fails for a synced entry | Skip entry, try next. If every entry's meta fails, fall through to legacy search. |
| Stream extraction fails on accepted synced video | Auto-advance to the next entry in the points list (not the legacy YouTube search). Once the points list is exhausted, the next failure falls through to legacy search. After `MAX_EXTRACTION_ATTEMPTS` total (3, today's value, counted across both modes), synth fallback. |
| User exhausts every synced video via "Try another" | One toast: "No more synced videos — searching YouTube." Then runSearch with the legacy path. |
| Existing songs without points (Phil Collins, etc.) | `videoPoints` is NULL → legacy branch, identical to today. No re-fetch on re-open. |
| Songsterr edits points after we cached | Out of scope. Workaround: delete + re-add. Note in code; no logic. |
| `t < points[0]` (e.g. Teen Spirit's `−0.15`) | `slotAt` returns 0 (clamped). The interpolation arithmetic handles negative points cleanly. |
| `t ≥ points.last()` | `slotAt` returns `totalSlots − 1`. `isFinished` path takes over. |
| Single-bar fixture / fewer than 2 points | `slotAt` returns 0; no interpolation (no bar boundary). Test covers it. |

---

## Testing

**New unit tests** (all JVM-side, no instrumentation):

- `OkHttpSongsterrVideoPointsServiceTest` — MockWebServer + trimmed fixture:
  - Happy path returns expected entries
  - HTTP 404 → empty list
  - Malformed JSON → empty list
  - Network failure → empty list

- `PointsBasedTimeMapTest`:
  - `t < points[0]` → slot 0
  - `t == points[i]` → slot `i × slotsPerBar`
  - `t` halfway between → slot `(i + 0.5) × slotsPerBar`
  - `t ≥ points.last()` → slot clamped to `totalSlots − 1`
  - Negative first point (Teen Spirit's `−0.15`)
  - `videoSecAt` is inverse of `slotAt` within rounding tolerance
  - Single-bar / two-point edge case

- `ConstantBpmTimeMapTest` — small sanity set proving today's BPM math is preserved.

- `AddSongViewModelTest` — extend existing suite:
  - Points service returns 2 entries → persisted Song has `videoPoints` set; no YouTube search invoked
  - Points service returns empty → persisted Song has `videoPoints = null`; existing assertions hold

- `PlayerViewModelTest` — extend existing suite:
  - Synced song enters Confirming with first entry; `YouTubeSearchService.findFor` is NOT called
  - `tryAnotherVideo()` cycles entries within the points list
  - Exhausting synced entries falls through to legacy search
  - Accepting commits videoId and uses `PointsBasedTimeMap` for the playback source (verified via fake adapter capturing the time map)

- `YouTubePlaybackSourceTest` — existing tests adapted for new constructor (now takes `TimeMap`). Plus one new test: feeding `onCurrentSecond` to a points-based time map yields the expected interpolated slots.

**New fixture:** `app/src/test/resources/songsterr/video-points-teen-spirit.json` — real response trimmed to 3 entries × 5 points (~30 lines).

**Existing tests:** All 63 stay green. `YouTubePlaybackSourceTest` needs constructor-call updates (mechanical).

**No new instrumentation / UI tests.** Confirmation dialog rendering is already covered.

---

## Scope NOT in this design (YAGNI)

- **Country/locale filtering of points entries.** Songsterr entries include a `countries` field but we ignore it; the raw list order matches the web player's default.
- **Per-feature preference** (e.g. prefer `feature=null` over `alternative`). Raw order is good enough.
- **Refresh of stale points on song re-open.** Library is the user's working set; if Songsterr re-edits, delete + re-add. A 5-minute reload UX isn't worth the complexity.
- **Editor for points.** They're data, not config.
- **Confidence/quality scoring of entries.** If Songsterr lists it, we trust it. Bad picks are handled via "Try another".

---

## Open follow-ups

None blocking implementation. After ship:
- If users frequently "Try another" through every entry, consider re-ranking by country or by NewPipe extraction success.
- If Songsterr ships a third CDN host or changes the points endpoint shape, fold the discovery into `OkHttpSongsterrVideoPointsService` the same way `OkHttpSongsterrTabFetcher`'s multi-CDN fallback was added.
