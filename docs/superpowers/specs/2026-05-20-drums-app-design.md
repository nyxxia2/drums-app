# Drums App — Phase 1 Design Spec

**Date:** 2026-05-20
**Owner:** Sara (product) · Claude (implementation)
**Status:** Approved for implementation

## 1. Goal

Build a native Android app for drummers that displays drum sheet music and plays it back in time, with a moving violet playhead sweeping across the notes so the user always knows where they are. Phase 1 ships a fully playable app with bundled sample songs. Sheet-music import (OCR) and Spotify integration are explicitly out of scope and stubbed.

## 2. Source of truth for design

This spec defers all visual, typographic, and behavioral detail to the handoff:

- **`/home/sara/claude/drums-app/design-reference/handoff.md`** — full README with colors, type scale, spacing, screen layouts, drum-notation rendering math, playhead behavior, and state shape.
- **`/home/sara/claude/drums-app/design-reference/drum-staff.jsx`** — canonical reference for the SVG drum-staff rendering. Port the math to Compose `Canvas`.
- **`/home/sara/claude/drums-app/design-reference/drums-app.jsx`** — reference implementation of `useLivePlayhead` (the song-clock-driven playhead position). Port the timing logic verbatim.

Chosen visual direction: **Neon Studio (Violet)**. The other two directions in the prototype are reference only and will not ship.

## 3. Phase 1 scope (what we ship)

A standalone Android APK that:

1. Boots into a **Library** screen listing 5 bundled sample songs (Smells Like Teen Spirit, Tom Sawyer, Rosanna, In the Air Tonight, YYZ).
2. Lets the user open any song into the **Player**, where:
   - The drum part is rendered as a 5-line drum staff (Compose Canvas, procedural — no notation library).
   - Tapping play starts drum-sample playback in time, and a violet playhead line sweeps across the staff at 60 fps.
   - Drum-hit chips at the bottom light up live as each subdivision sounds.
   - Pause, stop, metronome, and loop controls work as designed.
3. Lets the user open **Song Detail** to change tempo, count-in, metronome, and mute settings (changes persist via Room).
4. Lets the user open **Practice (Speed ramp)** to loop a section across N loops while tempo ramps from `startBpm` to `targetBpm`.
5. **Add a song** screen renders the full design but every import path is a no-op (toast: "Coming in a future update"). The Spotify and OCR flows are deferred to Phase 2/3.

### Non-goals for Phase 1

- Optical music recognition (OMR) from PDF/photo.
- Spotify OAuth, Web Playback SDK, or any external playback integration.
- User accounts, cloud sync, multi-device.
- Onboarding flow.
- Multiple drum kits (single bundled acoustic kit only).
- Tablet / foldable layouts (phone-portrait only, 412 × 892 dp class).
- Localization beyond English.

## 4. Technical decisions

| Area | Decision | Reason |
|---|---|---|
| **Language** | Kotlin | Standard for modern Android. |
| **UI toolkit** | Jetpack Compose + Material 3 | Matches the handoff recommendation; declarative model maps well to the highly reactive Player screen. |
| **Min SDK** | 26 (Android 8.0) | Required for `AudioTrack` precision and modern Compose features; covers >95% of devices. |
| **Target SDK** | 34 (Android 14) | Current Play Store baseline. |
| **JDK** | 17 | Compose compiler requirement. |
| **Build system** | Gradle (Kotlin DSL) | Standard. |
| **Persistence** | Room | Local-only, single-device. Songs stored with their bar patterns serialized to JSON. |
| **Audio** | SoundPool + a song-clock derived from `SystemClock.elapsedRealtime()` | Sufficient latency for Phase 1; can be upgraded to Oboe later if needed. |
| **Drum samples** | Bundled in `app/src/main/assets/samples/` as 16-bit WAV. Free CC0 set (e.g. Soni Musicae acoustic). | No network dependency; deterministic playback. |
| **Drum-staff rendering** | Compose `Canvas` (procedural drawing, port from `drum-staff.jsx`) | Pixel control over playhead position; no third-party notation library. |
| **Playhead motion** | `withFrameNanos` driving a `Float` slot state, position derived from `(now - playStartTime) / msPerSlot` every frame | Visual and audio share one clock. |
| **Architecture** | MVVM with one `ViewModel` per screen (`LibraryViewModel`, `PlayerViewModel`, etc.) plus a `SongRepository` over Room | Standard Android architecture, simple to test. |
| **Dependency injection** | Hilt | Idiomatic for Compose + Room + ViewModel. |
| **Sample data** | 5 hard-coded `Song` objects pre-seeded into Room on first launch via a `RoomDatabase.Callback` | No backend needed. |

## 5. Module / package layout

Single-module project under `app/`:

```
app/src/main/java/ph/nextbank/drums/
├── DrumsApplication.kt           // @HiltAndroidApp
├── data/
│   ├── db/                        // Room: SongDao, SongEntity, AppDatabase, seed callback
│   ├── model/                     // Song, DrumToken, ImportSource (pure data classes)
│   ├── repo/                      // SongRepository
│   └── samples/                   // Hard-coded sample songs (Nirvana, Rush, Toto, ...)
├── audio/
│   ├── DrumSampleBank.kt          // SoundPool wrapper, drum-token -> sample id
│   ├── SongClock.kt               // play / pause / stop / currentSlot derivation
│   └── Metronome.kt               // Click track scheduling
├── ui/
│   ├── theme/                     // NeonStudio color scheme, typography, shapes
│   ├── components/
│   │   ├── DrumStaff.kt           // Compose Canvas — staff, notes, playhead
│   │   ├── DrumStaffStack.kt      // Multi-line scrollable score
│   │   ├── DrumHitChips.kt        // 8-chip live indicator row
│   │   ├── CoverArt.kt            // Procedural gradient + diagonal stripes + monogram
│   │   ├── PillButton.kt          // Tab pills
│   │   └── TransportButton.kt     // FAB / play / ghost icon buttons
│   ├── library/                   // LibraryScreen + LibraryViewModel
│   ├── upload/                    // UploadScreen (stubs) + UploadViewModel
│   ├── player/                    // PlayerScreen + PlayerViewModel
│   ├── song_detail/               // SongDetailScreen + SongDetailViewModel
│   ├── practice/                  // PracticeScreen + PracticeViewModel
│   └── nav/                       // NavHost, Route sealed class
└── util/
    └── ext/                       // Compose / Kotlin extensions
```

Tests under `app/src/test/` (unit) and `app/src/androidTest/` (instrumented):

- `SongClockTest` — given BPM and elapsed time, `currentSlot` is correct.
- `DrumStaffMathTest` — given bar pattern + slot, expected x-coordinate matches handoff math.
- `SongRepositoryTest` — DB seed + read.
- One smoke instrumented test that launches the app and verifies the Library shows 5 songs.

## 6. Sample-song data

The 5 sample songs from the handoff. Each defined as a `Song` object in `data/samples/`:

| Title | Artist | BPM | Time Sig | Bars | Initials |
|---|---|---|---|---|---|
| Smells Like Teen Spirit | Nirvana | 116 | 4/4 | 8 | NV |
| Tom Sawyer | Rush | 88 | 4/4 | 8 | RU |
| Rosanna | Toto | 86 | 4/4 | 8 | TO |
| In the Air Tonight | Phil Collins | 95 | 4/4 | 8 | PC |
| YYZ | Rush | 144 | 4/4 | 8 | RU |

Bar patterns are simplified canonical grooves — not transcriptions. Good-enough for a recognizable feel; legal because we're not reproducing copyrighted notation, just shipping common drum patterns associated with these tempos. (If legal-uncertainty becomes a concern, swap to fictional song titles before publishing.) All songs are 4/4 in Phase 1 — supporting other time signatures adds rendering complexity better left for a follow-up.

## 7. Build & install path for the user

1. **User installs Android Studio** (one-time, ~3 GB). Bundles JDK, Android SDK, build tools, and an emulator manager.
2. **User opens the project** at `/home/sara/claude/drums-app/` — Android Studio auto-syncs Gradle.
3. **User picks a target**: either a Pixel 8 emulator (created via AVD Manager inside the IDE) OR a physical Android phone in USB-debug mode.
4. **User clicks ▶ Run**. The APK builds, installs, and launches.

No command-line interaction required from the user. Claude scaffolds the project so the IDE Just Works.

## 8. Risks / known unknowns

- **Audio latency on cheap devices.** SoundPool gives ~50 ms latency on mid-range hardware. The playhead motion is interpolated from clock-time, not from audio callbacks, so the visual stays smooth even if drum-samples lag slightly. If user reports drift, upgrade to Oboe in Phase 2.
- **Phase 2 (OMR).** Hosted OMR services have non-trivial cost and don't reliably handle photos of paper notation. Recommend deferring decision until Phase 1 ships and we have a real device for camera testing.
- **Sample-song licensing.** Drum grooves themselves are not copyrightable; song titles probably aren't infringing either; album art / cover gradients are procedural. Low risk for v1.

## 9. Definition of done for Phase 1

- All 5 screens render pixel-accurately to the handoff (manual visual check on emulator + one real device).
- Tapping play on any sample song starts synchronized audio + playhead motion that never drifts more than 1 sixteenth-note over 60 seconds.
- Settings changed in Song Detail persist across app restarts.
- App installs cleanly via Android Studio Run.
- All unit + instrumented tests pass.
- `app/build.gradle.kts` compiles with no warnings.
