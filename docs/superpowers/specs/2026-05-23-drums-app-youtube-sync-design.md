# Drums App — Phase 2 Design Spec: YouTube-synced Playback

**Date:** 2026-05-23
**Owner:** Sara (product) · Claude (implementation)
**Status:** Approved for implementation

## 1. Goal

Make the Player screen play in Songsterr style: the YouTube recording is the audio source, and the bundled drum tab scrolls in sync with the video's playhead. The user never enters a URL — the app searches YouTube for the song, confirms the result with the user, then plays it.

This builds on Phase 1's drum-staff + cursor renderer; only the timing source and the audio path change.

## 2. Scope

### In scope

1. **In-app YouTube search.** When the user opens a song that has no cached video, the app searches YouTube using `"<title> <artist>"` and proposes the top result via a confirmation dialog. User taps "Use this video" or "Try another".
2. **YouTube as time source.** Once a video is locked in, the staff cursor's position is derived from the YouTube player's `currentSecond`, not from `SongClock`. Cursor motion is interpolated to 60 fps between YouTube position updates (which arrive at ~1 Hz).
3. **Synthetic drums muted in YouTube mode.** When YouTube provides the audio, `DrumSampleBank` does not play. Drum-hit chips still light up so the user sees what should be hit.
4. **Sync offset.** Per-song `youtubeOffsetMs` aligns bar 1 with the first downbeat of the recording. Adjustable from the Player (–/+ 50 ms steps).
5. **"Try another video" flow.** Three-dots menu item runs a fresh search excluding any blocklisted video IDs. Each rejected video is added to that song's blocklist.
6. **Robust loading / error states.** Play button is disabled with "Loading…" until the YouTube player reports ready. Network/search/playback errors surface as a toast and fall back to synthetic playback.
7. **Fallback to synth.** If search returns no results, or the YouTube player can't initialize, the song plays with the existing synthetic drum samples — no regression from Phase 1 behavior.

### Out of scope (deferred)

- Adding new songs via URL paste or in-app text input (Upload screen stays stubbed).
- Auto-transcription of tabs from YouTube audio.
- Tabs sourced from Songsterr or any third-party site (legal/ToS blockers).
- Variable playback speed (0.5× / 0.75×). Requires tempo-scaled staff math — deferred to Phase 2B.
- Loop integration with YouTube `seekTo` (loop currently works on the synth clock; with YouTube as source, looping requires seeks). Deferred.
- Tempo maps for songs whose recording tempo varies — Phase 1 assumes constant BPM and we keep that here.
- Onboarding / first-run explainer for the confirmation dialog.

## 3. Architecture

### 3.1 Playback source abstraction

Introduce `PlaybackSource` — the single contract the `PlayerViewModel` talks to. Two implementations, exactly one active per session.

```kotlin
sealed interface PlaybackSource {
    val state: StateFlow<PlaybackState>   // Idle | Loading | Ready | Playing | Paused | Finished | Error
    val currentSlot: StateFlow<Float>     // staff-slot position, interpolated for smooth cursor

    fun play()
    fun pause()
    fun stop()
    fun nudgeOffset(deltaMs: Int)         // only meaningful for YouTube
    fun release()                         // lifecycle cleanup
}
```

- **`SyntheticPlaybackSource`** wraps the existing `SongClock` + `DrumSampleBank`. State machine drives synth-drum scheduling on each slot crossing (unchanged from Phase 1).
- **`YouTubePlaybackSource`** wraps the `YouTubePlayer` reference handed in by the embedded player. Listens for `onCurrentSecond` callbacks, applies `youtubeOffsetMs` and `bpm` to derive `currentSlot`, interpolates between callbacks using `withFrameNanos`. `DrumSampleBank` is **not** wired in here — YouTube provides the audio.

The `PlayerViewModel` decides which source is active in `init {}`: if the song has a cached `youtubeVideoId`, attempt YouTube; otherwise run search → confirm → cache → YouTube. On any YouTube failure, fall back to `Synthetic`.

### 3.2 YouTube search service

```kotlin
interface YouTubeSearchService {
    suspend fun findFor(query: String, blocklist: Set<String>): SearchResult?
}

data class SearchResult(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val durationSec: Int,
    val thumbnailUrl: String,
)
```

- **Implementation: `NewPipeYouTubeSearchService`** — calls NewPipe Extractor (`com.github.TeamNewPipe:NewPipeExtractor`) on `Dispatchers.IO`. Filters to videos (no playlists/channels), takes the first non-blocklisted hit.
- **Fake: `FakeYouTubeSearchService`** — for unit tests; configurable canned results and failure modes.
- **No API key.** Library scrapes YouTube directly. Risk: YouTube HTML changes break the lib. Mitigation: keep the dep version pinned but updatable; failure surfaces as a clean fall-back to synth.

### 3.3 Confirmation dialog

After a successful search, before binding the YouTube player, show a modal:

```
┌────────────────────────────────────────┐
│  Found a video for                     │
│  Smells Like Teen Spirit               │
│                                        │
│  ┌──────────┐ Nirvana - Smells Like    │
│  │ [thumb]  │ Teen Spirit (Official…)  │
│  └──────────┘ Nirvana · 5:01           │
│                                        │
│  [ Try another ]     [ Use this video ]│
└────────────────────────────────────────┘
```

- "Use this video" → write `youtubeVideoId` to DB, transition to `Loading` then `Ready`.
- "Try another" → add this ID to the song's `youtubeBlocklist`, re-run search excluding blocklist, show the next result. If search returns no further results, toast "No more matches — playing synth drums" and fall back.
- Hardware back button or scrim tap → dismiss the dialog and fall back to synth playback for this session only. The dismissed video is **not** blocklisted (the user might want to see it again next launch).

### 3.4 Data model changes

`Song` (model):
- `youtubeVideoId: String?` — now meaning "cached video ID from the last successful confirmation". Renames its semantic but keeps the existing column.
- **New** `youtubeOffsetMs: Int = 0` — sync nudge.
- **New** `youtubeBlocklist: List<String> = emptyList()` — IDs the user said "try another" on, comma-separated in the entity.

`SongEntity` mirrors these. DB migration **v2 → v3**:

```sql
ALTER TABLE songs ADD COLUMN youtubeOffsetMs INTEGER NOT NULL DEFAULT 0;
ALTER TABLE songs ADD COLUMN youtubeBlocklist TEXT NOT NULL DEFAULT '';
```

`SampleSongs.kt`: **clear the hardcoded `youtubeVideoId` values** on the 5 bundled songs (set to `null`). The app will search and cache instead. This change affects only fresh installs — for users upgrading from a previous build, the migration leaves existing `youtubeVideoId` values intact (their cached choices persist). Result: a clean install always demonstrates the search flow; existing users keep their current videos.

### 3.5 Player UI layout (landscape, no major changes from Phase 1)

```
┌──────────────────────────────────────────────────────┐
│ ← Title                BPM/BAR             ┌────────┐│
│                                            │YouTube ││
│                                            │ 16:9   ││
│ ┌──────────────────────────────┐           └────────┘│
│ │  Drum staff (scrolling)      │       playhead      │
│ │  ─── ─── ─── ─── ─── ─── ─── ─── ─── ─── ─── ───   │
│ └──────────────────────────────┘                     │
│ [chips: kick snare hihat ...]                        │
│ [metro] [stop] [▶/⏸] [loop]    sync: [−][offset][+]  │
└──────────────────────────────────────────────────────┘
```

- YouTube embed: ~240 × 135 dp in the upper-right corner.
- Drum staff: same horizontal-scrolling layout, fixed playhead at the same `playheadXDp` as today.
- Sync nudge replaces the unused "Speed" pill in the bottom row. Tapping − or + adjusts `youtubeOffsetMs` by 50 ms and persists immediately.
- Status overlay: when state is `Loading`, the play button is disabled and shows a small spinner. When state is `Error` (post-fallback), a discreet "synth mode" pill appears next to the play button.

### 3.6 State machine

```
                              ┌────────────┐
                              │   Idle     │
                              └─────┬──────┘
            no cached ID            │            cached ID present
        ┌──────────────────────────┴──────────────────────────┐
        ▼                                                     ▼
 ┌─────────────┐  result    ┌─────────────┐  "Use this"   ┌─────────┐
 │  Searching  │ ─────────▶ │  Confirming │ ────────────▶ │ Loading │
 └──────┬──────┘            └──────┬──────┘               └────┬────┘
        │ no result                │ "Try another"             │ player onReady
        │ or error                 │   (loop back to            ▼
        ▼                          │   Searching with     ┌─────────┐
 ┌──────────────┐                  │   blocklist)         │  Ready  │
 │ SynthFallback│ ◀────────────────┤                      └────┬────┘
 └──────────────┘  back / dismiss  │                           │ play()
        ▲                          │                           ▼
        │   (any YT error)         │                      ┌─────────┐
        └──────────────────────────┴──────────────────────│ Playing │
                                                           └────┬────┘
                                                                │ pause() / end
                                                                ▼
                                                          ┌──────────────┐
                                                          │ Paused /     │
                                                          │ Finished     │
                                                          └──────────────┘
```

`stop()` from any non-Idle state returns to `Ready` (or `SynthFallback` if that's the active source). Any unrecoverable YouTube error falls through to `SynthFallback` with a toast.

## 4. Module / package additions

```
audio/
├── (existing) DrumSampleBank.kt, Metronome.kt, SongClock.kt
├── playback/
│   ├── PlaybackSource.kt          // sealed interface + PlaybackState enum
│   ├── SyntheticPlaybackSource.kt // wraps SongClock + DrumSampleBank
│   └── YouTubePlaybackSource.kt   // wraps YouTubePlayer
└── youtube/
    ├── YouTubeSearchService.kt    // interface + SearchResult data class
    ├── NewPipeYouTubeSearchService.kt
    └── FakeYouTubeSearchService.kt  // testImplementation only

ui/player/
├── (modified) PlayerScreen.kt, PlayerViewModel.kt
├── YouTubeEmbed.kt                // AndroidView wrapper for YouTubePlayerView
└── YouTubeConfirmDialog.kt        // confirmation modal
```

DI changes (`AppModule.kt`): provide `YouTubeSearchService` as `@Singleton`, binding the NewPipe implementation in production. Tests can override via `@TestInstallIn(SingletonComponent::class, replaces = [AppModule::class])`.

## 5. Testing

### 5.1 Unit tests (JVM)

- **`SyntheticPlaybackSourceTest`** — preserves Phase 1 behavior: state transitions, slot scheduling, end-of-song.
- **`YouTubePlaybackSourceTest`** — given canned `onCurrentSecond` callbacks at known timestamps, asserts `currentSlot` computed using bpm + offset matches the expected slot. Uses a fake `YouTubePlayer` interface to avoid needing a real device.
- **`PlayerViewModelTest`** — given `FakeYouTubeSearchService` + fake clock:
  - Song with cached ID → goes Idle → Loading → Ready without confirmation.
  - Song without cached ID → goes Idle → Searching → Confirming, user accepts → ID persists.
  - User picks "Try another" → blocklist grows, second search runs.
  - Search returns empty → falls back to Synthetic.
  - YouTube reports error → falls back to Synthetic with toast event.
- **`NewPipeYouTubeSearchServiceTest`** — small integration check (skipped on CI by default), verifies a known stable video ID surfaces for a known query. Network-dependent, marked `@Ignore` by default.

### 5.2 Manual verification

For each of the 5 sample songs:

1. Fresh install (DB seeded with empty `youtubeVideoId`).
2. Tap song → confirmation dialog shows a plausible result.
3. Accept → audio plays, staff cursor tracks YouTube playhead.
4. Adjust sync nudge → visible cursor shift, persists across pause/stop/exit.
5. Pause → both audio and cursor freeze. Resume → both resume in sync.
6. "Try another" once → second result loads.

### 5.3 Failure-mode walkthrough

- Airplane mode on cold start: confirmation never shows; toast "Couldn't search YouTube — playing synth drums"; song plays via synth (Phase 1 behavior).
- Video pulled by uploader between confirmation and play: YouTube player error → toast → fall back to synth for this session; cached ID is **not** invalidated (next launch retries; user can "Try another" if it stays broken).

## 6. Risks / known unknowns

- **NewPipe Extractor breakage.** YouTube changes its HTML occasionally; the lib usually catches up within days. Failure mode is graceful (no results → synth fallback). We accept this risk for the no-API-key benefit.
- **Latency between YouTube `onCurrentSecond` and audio.** YouTube reports position at ~1 Hz with unspecified lag from the actual audio output. Mitigation: `youtubeOffsetMs` nudge is the user's escape valve.
- **APK size.** NewPipe Extractor + dependencies add roughly 1 MB to the APK. Acceptable.
- **ToS for NewPipe Extractor.** The library scrapes YouTube without an API key, which is in a grey area but is well-established. We're not republishing content — just searching and embedding YouTube's own player. Embedding YouTube videos via the official IFrame/Android player is explicitly allowed by YouTube ToS.

## 7. Definition of done

- All 5 sample songs play with YouTube audio after the confirmation dialog flow.
- Staff cursor stays in sync with YouTube playback within ±1 sixteenth note over 60 seconds (after a one-time nudge if needed).
- "Try another video" successfully cycles to the next result.
- Pull network, force-quit YouTube player, or revoke INTERNET permission — each yields a clean fall back to synth playback with a toast, no crash.
- All new unit tests pass; Phase 1 tests still pass.
- DB migration v2 → v3 verified on a device upgraded from a Phase-1 install.
