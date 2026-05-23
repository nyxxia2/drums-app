# Drums App — Phase 3 Design: Songsterr-sourced Tabs

**Goal:** Replace the hand-coded `SampleSongs.kt` placeholder content with on-demand drum tabs fetched from Songsterr. Users type a song name, pick a search result, and the app loads a real drum tab plus auto-matched YouTube audio (via the existing Phase 2 flow).

**Product framing:** The bundled-songs concept goes away. The app's value prop becomes "tell me a song, I'll play it" — the library starts empty and grows as the user adds songs.

---

## Why Songsterr (and the caveat)

Songsterr has community-maintained tabs for a huge catalogue, including drum tracks. They expose a documented JSON search endpoint:

- `GET https://www.songsterr.com/a/ra/songs.json?pattern={query}` — returns title, artist, songId, track metadata.

The actual tab notation, however, is **not** behind a documented public endpoint. Songsterr's web player loads per-track JSON ("revision JSON") from their CDN, which our parser will need to fetch by scraping the song page for the current `revisionId`. This is the same approach taken by working open-source projects (e.g. `Metaphysics0/songsterr-downloader`).

**Risk:** Songsterr can change their CDN URL structure or page state shape at any time. We accept this — when it breaks, we update the scraper. We mitigate with a fixture-based test corpus.

We explicitly considered and rejected the alternatives:
- **LLM-generated tabs** — too easy to hallucinate plausible-but-wrong patterns.
- **Wrap songsterr-downloader.com** — externalises the fragility but adds an arbitrary third-party dependency.
- **In-app tab editor** — bigger UX investment, defers the "any song" experience.

---

## Architecture

```
AddSongScreen (search input + results list)
        │
        ▼
SongsterrSearchService          GET /a/ra/songs.json?pattern={query}
        │                       (documented endpoint)
        ▼
List<SongsterrResult>
        │
        │ user picks one
        ▼
SongsterrTabFetcher             (a) scrape https://www.songsterr.com/a/wsa/{songId}
                                    for revisionId + drum trackId
                                (b) GET https://.../revisions/{revisionId}/{trackId}.json
        │
        ▼
DrumTabParser                   pure function: RevisionJson → Song.bars
                                picks the percussion-channel track,
                                walks beats/notes, maps MIDI to DrumToken,
                                flattens to a slot grid
        │
        ▼
Song domain object → SongRepository.insert → existing YouTube auto-search
```

Three new units, each independently testable:

- **`SongsterrSearchService`** — keyless OkHttp + JSON. Returns `List<SongsterrResult>`.
- **`SongsterrTabFetcher`** — page-scrape + CDN fetch, returns raw `RevisionJson`.
- **`DrumTabParser`** — pure function `RevisionJson → ParseResult` where `ParseResult` is a sealed type (`Success(Song.bars, slotsPerBar, timeSig, bpm)` or one of the failure variants below). No I/O — trivial to unit-test.

---

## Drum-note mapping

Songsterr encodes percussion by MIDI percussion number (General MIDI standard). Mapping:

| MIDI # | GM percussion           | DrumToken      |
|--------|-------------------------|----------------|
| 35, 36 | Acoustic/Bass kick      | `KICK`         |
| 38, 40 | Acoustic/Electric snare | `SNARE`        |
| 42, 44 | Closed hat / pedal hat  | `HIHAT_CLOSED` |
| 46     | Open hat                | `HIHAT_OPEN`   |
| 49, 57 | Crash 1/2               | `CRASH`        |
| 51, 53, 59 | Ride / bell / cymbal 2 | `RIDE`     |
| 48, 50 | Hi tom / Hi-mid tom     | `TOM_HI`       |
| 45, 47 | Lo tom / Lo-mid tom     | `TOM_MID`      |
| 41, 43 | Floor tom 1/2           | `TOM_FLOOR`    |
| others | cowbell, splash, china, etc. | dropped + debug-logged |

**Slot quantization.** Songsterr stores notes with positional offsets in fractions of a beat. The parser quantizes each note to the nearest slot. `slotsPerBar` defaults to 16; if the parser detects triplet-feel notes (12-tuplet markers) in the JSON, it uses 24 slots for the whole song instead. There is no mid-song subdivision change.

**Explicitly punted:**
- Mid-song time-signature changes — use the first bar's meter for the whole song, log a warning if the song actually changes meter.
- Tempo changes — use first tempo.
- Multi-drum-kit tracks — pick the track on MIDI channel 10 with the most notes; ignore the rest.
- Tab quality variance — whatever the Songsterr community uploaded is what plays.

---

## Data flow and storage

### Domain model changes

`Song` gains two optional fields:

- `songsterrId: Long?` — Songsterr's song ID, so we can re-fetch / refresh later.
- `songsterrRevisionId: String?` — pinned revision so the parsed tab doesn't silently drift if the community edits the source.

### Database migration v4

```sql
ALTER TABLE songs ADD COLUMN songsterrId INTEGER;
ALTER TABLE songs ADD COLUMN songsterrRevisionId TEXT;
```

### Bundled-songs removal

- Delete `app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt`.
- Delete the seed call in `AppModule.provideSongRepository` (the `repo.upsertAll(SAMPLE_SONGS)` line).
- The migration above is unrelated to seeding — existing installs keep their data; only the auto-seeding goes away.
- Library screen's empty state: keep the existing "Tap the + button to add a song." copy.

### Search flow (user-facing)

1. User opens "Add song" → types query → debounced `SongsterrSearchService.search(query)` after **400 ms**.
2. UI shows top 10 results: cover initials, "Title — Artist", optional small label if Songsterr reports multiple tab versions for the same song.
3. User taps a result → loading state → `SongsterrTabFetcher` + `DrumTabParser` run on `Dispatchers.IO`.
4. On success: Song is persisted with parsed bars + Songsterr IDs, then the existing Phase 2 YouTube auto-search kicks in using `"${title} ${artist}"`. User sees the existing "Found a video / Use this video" confirmation dialog.
5. On failure: toast (specific copy per error variant below), return to the search results, do **not** persist.

### Error model

Sealed `ParseResult` / `FetchResult` types — exceptions don't cross the service boundary:

- `NoDrumTrack` — Songsterr has the song but no percussion track. Toast: "This song doesn't have a drum tab on Songsterr."
- `ScrapeFailure(reason: String)` — page format changed or revision endpoint 404'd. Toast: "Couldn't load tab from Songsterr — try a different result." Full reason debug-logged.
- `NetworkError` — offline / timeout. Toast: "Check your connection."
- `ParseError(reason: String)` — JSON structure unexpected. Toast: "Couldn't read the tab data." Full reason debug-logged.

### Caching

Parsed `Song`s persist in Room (existing infrastructure). We do **not** cache the raw revision JSON — re-fetching by `songsterrRevisionId` is cheap and gives us automatic recovery if the parser improves later.

---

## Testing strategy

**Pure-logic unit tests** (no network, run via `./gradlew testDebugUnitTest`):

- `DrumTabParserTest`
  - Single 4/4 bar, kick on 1, snare on 3, closed-hat 8ths → expected slot grid.
  - Triplet-feel input → parser switches to 24 slots/bar.
  - Unmapped MIDI note (e.g. cowbell 56) → dropped, song parses without crashing.
  - Multiple drum tracks → picks the one with most notes.
  - No percussion track at all → returns `NoDrumTrack`.
  - Mid-song meter change → uses first meter, logs warning, still produces a valid song.

- `SongsterrSearchServiceTest` — OkHttp `MockWebServer`. Asserts query encoding, JSON deserialization, empty-result handling.

- `SongsterrTabFetcherTest` — `MockWebServer`. Verifies the two-step flow (page scrape → CDN fetch), surfaces `ScrapeFailure` when the page HTML lacks expected markers.

**Fixture corpus**: bundle 3–4 real revision JSON files (captured from actual Songsterr fetches) under `app/src/test/resources/songsterr/`. When Songsterr changes their format and we update the parser, these tests catch regressions.

**Manual / integration verification** (not automated): after each milestone, install on device, search for a known song with a drum track, confirm the tab loads + YouTube audio plays.

**Explicitly out of scope for automated tests**: end-to-end against live Songsterr. A third-party API breaking should fail in the wild, not in CI.

---

## Implementation milestones

Each milestone ends with a working app that can be installed and exercised on device.

1. **M1 — Search alone.** `SongsterrSearchService` + a minimal "Add song" screen that lists results but doesn't fetch tabs yet. Tapping a result is a no-op. Verifies the search endpoint still works against real Songsterr before any parser investment.
2. **M2 — Fetch + parse a single track.** `SongsterrTabFetcher` + `DrumTabParser` with fixture-based tests. Tapping a result fetches, parses, and persists a `Song` with real `bars` but no YouTube wiring yet. The new Song appears in the library and is playable in synth mode.
3. **M3 — Wire to YouTube + drop bundled.** Hook M2 into the Phase 2 YouTube auto-search confirmation flow. Delete `SampleSongs.kt`, the seed call in `AppModule`, the related `SAMPLE_SONGS` references. Add DB migration v4. After install, library is empty until the user adds a song.
4. **M4 — Error polish + edge cases.** Implement the four sealed error variants, toast copy, no-drum-track fallback, optional "refresh tab" affordance from the player menu.

---

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Songsterr changes CDN URL / page state shape | Fixture corpus catches it; isolated parser is straightforward to update |
| Popular song has no drum tab on Songsterr | Surface `NoDrumTrack` clearly; user picks a different search result |
| YouTube auto-match picks the wrong video | Existing Phase 2 "Try another video" affordance still works |
| Slow first-tab load (search → scrape → fetch → parse) | Show progress states; all I/O on `Dispatchers.IO` with reasonable timeouts |
| Multi-track ambiguity (e.g. song has two drummers) | Pick channel-10 track with most notes; document the heuristic; revisit if it causes complaints |

---

## Out of scope (deferred)

- Mid-song meter or tempo changes (model extension).
- In-app drum-tab editor / corrections.
- Caching parsed tabs offline (existing Room storage covers post-fetch needs).
- Selecting between multiple Songsterr revisions or drum tracks via UI.
- Source diversification (Ultimate Guitar, etc.) — Songsterr only, for now.
