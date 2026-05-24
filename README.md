# Drums

An Android drum-practice app. Type a song, get a real drum tab from Songsterr, play it back synced to YouTube audio — no manual nudging required.

```
LIBRARY                      NOW READING
Your kit                     In The Air Tonight

[All 3] Recent  Bundled       ┌─────────────────────────────────────┐
                              │  ♩       ♩ ♩       ♩       ♩ ♩     │
ALL SONGS                     │ x   x x  x  x x  x   x x  x  x x   │
                              │ ●         ●         ●         ●    │
🎸 Phil Collins — 95 BPM      └─────────────────────────────────────┘
🎤 Nirvana    — 117 BPM
                              KICK SNR HH CRSH RIDE TOM1 TOM2 FLR

                                       ▶  ⏸  🔁    − 0ms +
```

## What it does

1. **Search Songsterr** for a song — type "smells like teen spirit", pick the Nirvana result.
2. **Drum tab is fetched + parsed** from Songsterr's community-maintained data, stored locally.
3. **A YouTube video is picked** — preferring Songsterr's pre-aligned ones when available, falling back to a general search otherwise.
4. **Playback stays in sync.** For songs Songsterr has aligned (the majority of popular tabs), the playhead follows per-bar timestamps in the video — no constant-BPM approximation, no drift, no manual offset slider needed.
5. **Synth fallback** if YouTube extraction fails — the app plays the tab through bundled drum samples instead.

## How sync actually works

The naïve approach is "BPM × time = bar position." That drifts within seconds because (a) YouTube videos have intros of varying length and (b) real recordings have small tempo variations.

Songsterr already solved this — they publish `/api/video-points/{songId}/{revisionId}/list` with a per-bar timestamp array for every YouTube video they've editorially aligned to a tab. We consume that:

```
            piecewise-linear time map
audio time ────────────────────────────►  bar position
 0.00s ────────────────────────────────►  bar 0
 2.50s ────────────────────────────────►  bar 1
 4.68s ────────────────────────────────►  bar 2
 …
```

Within a bar, slot position is linearly interpolated. Outside the points range, it clamps. The user's manual offset slider still works as a fine-tune on top of the synced base.

Songs Songsterr doesn't have sync data for fall back to constant-BPM playback (today's manual-nudge workflow).

## Tech stack

- **Kotlin 2.0** + **Jetpack Compose** (Material 3, BOM 2024.09)
- **Hilt** for DI
- **Room** for local storage (5 migrations and counting)
- **ExoPlayer** (via [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)) for YouTube audio playback without the official YouTube SDK
- **OkHttp 4** + **kotlinx.serialization** for the Songsterr REST calls
- **AGP 8.5**, JDK 17, minSdk 26, targetSdk 34

No proprietary YouTube libraries, no Songsterr SDK (they don't have one). Everything talks to documented or empirically discovered endpoints.

## Building & running

You need Android Studio (Panda 4 or newer) and an Android device or emulator on API 26+.

```bash
# First open in Android Studio to trigger Gradle sync — generates gradlew.
# After that, from the terminal:

export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=$HOME/Library/Android/sdk/jbr  # or wherever AS's bundled JDK lives

./gradlew :app:installDebug    # install to a connected device
./gradlew :app:testDebugUnitTest  # run the 89 unit tests
```

If you don't see drum tabs loading, check that you have internet — both Songsterr and YouTube need to be reachable.

## Project layout

```
app/src/main/java/ph/nextbank/drums/
├── audio/
│   ├── songsterr/        # Search, tab fetcher, video-points service, parser
│   ├── youtube/          # NewPipe-backed video search + audio stream extraction
│   ├── playback/         # YouTube + synthetic playback sources
│   ├── TimeMap.kt        # Constant-BPM and points-based time-to-slot mapping
│   └── SongClock.kt
├── data/
│   ├── model/            # Domain types (Song, DrumToken, …)
│   ├── db/               # Room entity, DAO, migrations v1–v5
│   └── repo/             # SongRepository
├── ui/
│   ├── library/          # Song list, empty-state, FAB
│   ├── add/              # Search Songsterr + add to library
│   ├── player/           # Staff renderer, transport controls, confirmation dialog
│   └── components/       # DrumStaff, DrumHitChips, etc.
└── di/                   # Hilt module
```

Specs and plans for each phase live under `docs/superpowers/specs/` and `docs/superpowers/plans/`.

## Testing

89 JVM-side unit tests cover the parser, the Songsterr services (against MockWebServer + captured fixtures), the time maps, both viewmodels, and the playback source. There are also a few Hilt-based instrumentation tests under `app/src/androidTest/` for the Room layer.

```bash
./gradlew :app:testDebugUnitTest         # fast — pure logic, no emulator
./gradlew :app:connectedDebugAndroidTest # needs a device/emulator
```

## Status

Three feature phases shipped:

| Phase | What it adds | Branch (historical) |
|---|---|---|
| 1 | Drum staff renderer, library, bundled songs, manual upload | merged into `master` |
| 2 | YouTube audio matching, confirmation dialog, manual offset slider | `phase-2-youtube-sync` |
| 3 | Songsterr-sourced tabs, multi-CDN fallback, auto-sync via video-points | `phase-3-songsterr-tabs` |

`master` is the integrated state — all three phases. The phase branches are kept as historical milestones.

## Acknowledgments

- **[Songsterr](https://www.songsterr.com)** for the community-maintained drum tabs and the per-video alignment data. This app is not affiliated with Songsterr; we use their public-but-undocumented endpoints respectfully (low request rates, no scraping abuse).
- **[NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)** for letting us play YouTube audio without their proprietary SDK.
- All the unnamed drummers who tabbed out these songs in the first place.
