# Persist YouTube Pick Across Song Opens

## Goal
Once a song has been played with a YouTube video at least once, future opens of that song play immediately with the same video — no picker, no search. Transient stream-extraction glitches must not undo that.

## Problem
The `Song.youtubeVideoId` cache exists end-to-end (model → Room → playback init) but is wiped on every transient NewPipe stream-extraction failure. Symptom: the "Pick the right video" dialog reappears on subsequent opens even after the user already picked a video that played fine before.

Two code paths in `PlayerViewModel.kt` cause this:

- `acceptCandidate` (line ~108) writes `youtubeVideoId` to the repo *before* attempting extraction. If extraction then fails, the cache holds a videoId that has never been proven to work.
- `startYouTubePlayback` (line ~143) writes `youtubeVideoId = null` to the repo on extraction failure, erasing the user's choice.

NewPipe's `getAudioStreamUrl` is documented as flaky ("YouTube applies per-video player-JS obfuscation that NewPipe can't always decode") — failures are often transient.

## Core change
Strengthen the invariant of `Song.youtubeVideoId` from "user clicked this in the picker" to **"this video extracted to a real audio stream at least once."** Move persistence from moment-of-pick to moment-of-proven-extractable. Stop wiping the cache on extraction failure.

## Changes (`PlayerViewModel.kt`)

1. **`acceptCandidate`** — drop the immediate `repo.updateYoutubeVideoId(...)` and the in-memory `song.copy(youtubeVideoId = ...)`. Just call `startYouTubePlayback(candidate.videoId)`.

2. **`startYouTubePlayback` success path** — after `streamUrl != null` and before configuring the playback source, write `repo.updateYoutubeVideoId(songId, videoId)` and update the in-memory `_state.value.song.youtubeVideoId`. Idempotent on cold-open-from-cache.

3. **`startYouTubePlayback` failure branch** — remove `repo.updateYoutubeVideoId(songId, null)` and the in-memory `youtubeVideoId = null`. Keep the blocklist write and the `showCandidates(...)` fallback. A failed candidate gets blocklisted (so the picker won't re-suggest it) but does not poison the persistent cache.

4. **`tryAnotherVideo`** — unchanged. Explicit user rejection still clears the cache.

## Behavior matrix

| Scenario | Result |
|---|---|
| First-time pick → extraction succeeds | Cached. Future opens play immediately. |
| First-time pick → extraction fails | Not cached. Picker remains; next pick gets a chance. |
| Cold open with cached id → extraction succeeds | Plays immediately, no picker. |
| Cold open with cached id → extraction fails | Cache preserved. Picker opens this session. If user picks a different working video → it overwrites. If user dismisses (synth) → next session retries the cached one (transient failures self-heal). |
| User hits "Try another video" | Cache cleared, picker shown (unchanged). |

## Tests (`PlayerViewModelTest.kt`)

Existing 13 tests should keep passing. They either don't assert on the cache after failure (`audio URL extraction failure triggers reopen`, `extraction failures hit cap of 3`) or test the post-success state (`acceptCandidate caches videoId` — extraction succeeds, persistence happens, same end state).

Two new tests:

- **Extraction failure preserves existing cached videoId** — seed with a cached id and no audio URL mapping. After init runs, assert `repo.snapshot.youtubeVideoId` is still the original value (not null) and the blocklist contains the failed id.
- **acceptCandidate defers persistence until extraction proves the stream URL** — start with no cache, pick a candidate that has no audio URL mapping. Assert `repo.snapshot.youtubeVideoId` stays null (the persistence does not happen until extraction succeeds).

## Out of scope

- Auto-falling-back to synth instead of re-showing the picker when a cached video fails. Worth revisiting if it proves annoying in practice.
- Changes to the AddSong flow's pre-population. `AddSongViewModel.confirmPendingAdd` writes `youtubeVideoId` directly into the upserted song; this is fine because the song hasn't been opened in the player yet, so there's nothing to protect. The Player's success-path persist will reaffirm it on first open.
- Refactoring the `extractionAttempts` cap or the picker UX.
