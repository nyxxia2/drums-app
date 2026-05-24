# Resolve YouTube candidate at Add time

## Problem

Today the Add Song flow fetches Songsterr tab data and video-points and persists them, but YouTube video selection is deferred to the first time the user opens the player. As a result, opening a freshly-added song surfaces the `YouTubeConfirmDialog` — re-running a YouTube search (or a `fetchMeta` on the first synced entry) every open until the user accepts. The user wants the YouTube lookup to happen once, at Add time, so opening the player just plays.

Tab data is already cached correctly today (`AddSongViewModel.persistSong` writes bars + `videoPoints` to Room; `PlayerViewModel.init` reads from the DB and does not re-fetch). The only network work happening on a fresh open is the YouTube candidate resolution. This spec moves that work into the Add flow.

## Goals

- After a song is successfully added to the library, opening its player triggers no YouTube search and no confirmation dialog — it plays immediately.
- YouTube video selection still goes through a user-facing confirmation dialog, but during Add Song rather than during first player open.
- If the user dismisses the confirmation, the song is **not** persisted; the add is cancelled.
- The player's existing "Try another video" cycling, blocklist, and synth fallback behavior is preserved unchanged for both new and legacy songs.

## Non-goals

- No "Try another" cycling inside the Add-time dialog. The user picks the first surfaced candidate or cancels.
- No stream-URL extraction at Add time. Stream URLs aren't cacheable and validation here would only slow the add without removing any later failure path.
- No schema migration. The `youtubeVideoId` column on `SongEntity` already exists; we just start populating it earlier in the lifecycle.
- No change to bundled-song seeding. Bundled songs keep their existing `youtubeVideoId = null` and still resolve lazily in the player on first open.
- No retroactive backfill for songs that were added before this change. Their `youtubeVideoId` stays null until the user opens them, at which point the existing player flow applies.

## Approach

Extract the candidate-picking logic that currently lives inline in `PlayerViewModel` into a new helper, then call it from `AddSongViewModel` after the existing tab + points fetch. The fetched candidate is held in transient UI state until the user accepts or dismisses the existing `YouTubeConfirmDialog`. Persistence only happens on accept.

## Components

### `YouTubeCandidateResolver` (new)

Location: `app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeCandidateResolver.kt`

```kotlin
@Singleton
class YouTubeCandidateResolver @Inject constructor(
    private val searchService: YouTubeSearchService,
) {
    /**
     * Picks an initial YouTube candidate for a song.
     *
     * If [videoPoints] is non-empty (Songsterr-aligned songs), iterates the entries in order,
     * skipping IDs in [blocklist], and returns the first one whose YouTube metadata resolves.
     * Otherwise runs a general YouTube search via [YouTubeSearchService.findFor].
     *
     * Returns null when no candidate can be resolved (network failure, all blocklisted, no
     * search results, all metadata fetches failed).
     */
    suspend fun resolveInitial(
        title: String,
        artist: String,
        videoPoints: List<VideoPointEntry>?,
        blocklist: Set<String> = emptySet(),
    ): SearchResult?
}
```

The synced-path iteration mirrors `PlayerViewModel.showSyncedCandidate`'s existing "skip entries whose meta fails" behavior. The unsynced-path call mirrors `runSearch`. No new network endpoints — purely a refactor of existing calls into one place that both viewmodels can share.

### `AddSongViewModel` changes

`AddSongUiState` gains one field:

```kotlin
data class AddSongUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isAdding: Boolean = false,
    val results: List<SongsterrResult> = emptyList(),
    val pendingConfirm: PendingConfirm? = null,    // NEW
)

data class PendingConfirm(
    val songTemplate: Song,    // fully built; needs upsert on accept
    val candidate: SearchResult,
)
```

`onResultClicked` is restructured. After the existing tab fetch, points fetch, and parse succeed, instead of calling `persistSong` directly:

1. Build the `Song` template with `youtubeVideoId = null` (filled in on accept).
2. Call `resolver.resolveInitial(title, artist, videoPoints, blocklist = emptySet())`.
3. If null → emit toast "Couldn't find a YouTube match — try a different result"; set `isAdding = false`; **do not persist**.
4. If non-null → set `pendingConfirm = PendingConfirm(songTemplate, candidate)`; keep `isAdding = true` while the dialog is visible so the search list doesn't accept other clicks.

Two new methods:

```kotlin
fun confirmPendingAdd()    // upsert(songTemplate.copy(youtubeVideoId = candidate.videoId)); emit SongAdded
fun dismissPendingAdd()    // clear pendingConfirm; isAdding = false; no upsert
```

### `AddSongScreen` changes

Observes `pendingConfirm`. When non-null, renders the existing `YouTubeConfirmDialog` from `ui/player/` over the search list. Wires its accept callback to `confirmPendingAdd()` and its dismiss callback to `dismissPendingAdd()`. The dialog does **not** get a "Try another" button in this surface.

### `PlayerViewModel` changes

Behavior preserved. The candidate-picking inside `showSyncedCandidate` and `runSearch` is refactored to delegate to the same `YouTubeCandidateResolver`. The rest of the player flow — blocklist updates, retry-on-extraction-failure, "Try another video" cycling, synth fallback — is unchanged.

For songs added via the new Add flow, `cachedId != null` will already be true in `init`, so `startYouTubePlayback` is called directly and the synced/unsynced branches don't execute. They remain on the code path for:
- Bundled songs (seeded with `youtubeVideoId = null`).
- Songs added before this change (already persisted with `youtubeVideoId = null`).
- Songs whose cached video was cleared by the player's "Try another video" action.

## Data flow

```
[Add Song] user picks Songsterr result
   │
   ├─ tabFetcher.fetchDrumTrack          (existing)
   ├─ pointsService.fetch                (existing)
   ├─ parser.parse                       (existing)
   │
   ├─ resolver.resolveInitial(...)       (new)
   │      synced:    walk videoPoints, fetchMeta on each
   │      unsynced:  searchService.findFor
   │
   ├─ null      → toast; isAdding=false; abort
   └─ non-null  → state.pendingConfirm = PendingConfirm(template, candidate)
                   │
                   └── AddSongScreen shows YouTubeConfirmDialog
                          ├─ accept  → upsert(template with videoId); emit SongAdded
                          └─ dismiss → clear pendingConfirm; isAdding=false (no upsert)

[Player] open song
   │
   ├─ youtubeVideoId != null  → startYouTubePlayback     ← NEW songs land here
   ├─ videoPoints non-empty   → showSyncedCandidate(0)   ← legacy / bundled
   └─ otherwise               → runSearch                ← legacy / bundled
```

## Error handling

| Failure | Behavior |
|---|---|
| Tab fetch / parse failures | Unchanged. Existing toasts; no add. |
| `resolveInitial` returns null (no synced entry meta resolves, or YouTube search returns no result) | Toast "Couldn't find a YouTube match — try a different result". `isAdding = false`. Song **not** persisted. |
| Network error during `resolveInitial` | Caught by the same `runCatching` block that wraps the tab fetch today. Toast "Check your connection." `isAdding = false`. Song not persisted. |
| User dismisses dialog | `dismissPendingAdd()` — `pendingConfirm = null`, `isAdding = false`. No DB row. |
| Stream extraction fails on first play (NewPipe returns 0 streams) | Unchanged: the player's existing `MAX_EXTRACTION_ATTEMPTS=3` retry path runs, blocklisting the failed video and either picking the next synced entry, falling back to YouTube search, or finally switching to synth. The user does see the player's normal retry UX in that case — it's not a regression. |

## Testing

### `YouTubeCandidateResolverTest` (new)

- Synced song with one entry whose meta resolves → returns that entry.
- Synced song where the first entry's meta returns null and the second resolves → returns the second.
- Synced song where every entry's meta returns null → returns null.
- Synced song where every entry is blocklisted → returns null.
- Unsynced song (videoPoints null/empty) with search results → returns first non-blocklisted result.
- Unsynced song with no search results → returns null.

### `AddSongViewModelTest` (new tests added)

- After a successful synced add, `state.pendingConfirm` exposes the first videoPoints entry's `SearchResult` and `isAdding` stays true.
- After a successful unsynced add, `state.pendingConfirm` exposes the YouTube search result.
- `confirmPendingAdd()` upserts a Song with `youtubeVideoId` set to the candidate's id, emits `SongAdded`, and clears `pendingConfirm`.
- `dismissPendingAdd()` clears `pendingConfirm`, sets `isAdding = false`, and does **not** call `repo.upsertAll`.
- Resolver returning null produces the toast and leaves the DB untouched.
- Network failure during resolve produces the existing connection toast and leaves the DB untouched.

### `PlayerViewModelTest` (existing tests updated)

- Tests are updated to inject the new resolver (likely via a fake) but assertions remain the same — the player's observable behavior is unchanged. No new player tests required for this change.

## Out of scope

- Migrating existing rows to populate `youtubeVideoId` retroactively.
- Storing the resolved YouTube metadata (channel, duration, thumbnail) alongside the videoId. The player already re-fetches what it needs.
- Validating stream extractability at Add time.
- A "Try another candidate" button in the Add-time dialog.
