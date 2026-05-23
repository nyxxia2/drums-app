# Drums App — Phase 2 Implementation Plan: YouTube-synced Playback

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Songsterr-style playback for the 5 bundled songs — the embedded YouTube player provides the audio, the bundled drum tab scrolls in sync with the video's playhead, the app searches YouTube on first open and confirms the result with the user. Spotify cruft is removed along the way.

**Architecture:** Introduce a `PlaybackSource` sealed interface with two implementations (`Synthetic`, `YouTube`). `PlayerViewModel` switches between them based on a per-song cached `youtubeVideoId`. A `YouTubeSearchService` (NewPipe Extractor) searches YouTube without an API key. Confirmation dialog and "Try another video" affordance let the user override the auto-pick.

**Tech Stack:**
- Kotlin + Jetpack Compose + Material 3 + Hilt + Room (existing)
- `com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0` (existing) — embedded YouTube player
- `com.github.TeamNewPipe:NewPipeExtractor:0.24.3` (new) — keyless YouTube search
- `com.squareup.okhttp3:okhttp:4.12.0` (new) — Downloader backend for NewPipe Extractor

---

## File structure

**Created:**
- `app/src/main/java/ph/nextbank/drums/audio/playback/PlaybackSource.kt` — sealed interface + `PlaybackState` enum
- `app/src/main/java/ph/nextbank/drums/audio/playback/SyntheticPlaybackSource.kt` — wraps the existing `SongClock` + `DrumSampleBank`
- `app/src/main/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSource.kt` — wraps a `YouTubePlayer` reference, derives `currentSlot` from `onCurrentSecond` callbacks
- `app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeSearchService.kt` — interface + `SearchResult`
- `app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeYouTubeSearchService.kt` — NewPipe Extractor implementation
- `app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeDownloader.kt` — OkHttp-based downloader needed by NewPipe.init
- `app/src/main/java/ph/nextbank/drums/ui/player/YouTubeEmbed.kt` — Compose `AndroidView` wrapping `YouTubePlayerView`
- `app/src/main/java/ph/nextbank/drums/ui/player/YouTubeConfirmDialog.kt` — modal confirmation
- `app/src/test/java/ph/nextbank/drums/audio/youtube/FakeYouTubeSearchService.kt` — for unit tests
- `app/src/test/java/ph/nextbank/drums/audio/youtube/FakeYouTubePlayer.kt` — for unit tests of `YouTubePlaybackSource`
- `app/src/test/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSourceTest.kt`
- `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`

**Modified:**
- `gradle/libs.versions.toml` — add NewPipe + OkHttp
- `settings.gradle.kts` — add JitPack repo
- `app/build.gradle.kts` — add new dependencies
- `app/src/main/java/ph/nextbank/drums/data/model/ImportSource.kt` — drop `SPOTIFY`
- `app/src/main/java/ph/nextbank/drums/data/model/Song.kt` — add `youtubeOffsetMs`, `youtubeBlocklist`
- `app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt` — add columns + mappers
- `app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt` — version → 3, `MIGRATION_2_3`
- `app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt` — add `updateYoutubeVideoId`, `updateYoutubeOffset`, `addToBlocklist`
- `app/src/main/java/ph/nextbank/drums/data/db/SongDao.kt` — DAO methods for the above
- `app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt` — clear all 5 `youtubeVideoId` fields
- `app/src/main/java/ph/nextbank/drums/ui/library/LibraryScreen.kt` — `Spotify` pill → `Bundled`
- `app/src/main/java/ph/nextbank/drums/ui/upload/UploadScreen.kt` — drop "Connect Spotify" row
- `app/src/main/java/ph/nextbank/drums/di/AppModule.kt` — provide `YouTubeSearchService`, init NewPipe
- `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt` — full rewrite to use `PlaybackSource` + search service + state machine
- `app/src/main/java/ph/nextbank/drums/ui/player/PlayerScreen.kt` — embed YouTube player, sync-nudge UI, loading/error overlays, confirmation dialog, "Try another" menu

---

## Milestone 0 — Spotify cleanup (Tasks 1–2)

### Task 1: Remove `SPOTIFY` from data model + Library

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/data/model/ImportSource.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/ui/library/LibraryScreen.kt` (lines 71, 77, 82, 110)

- [ ] **Step 1: Drop `SPOTIFY` from the enum**

Replace the entire contents of `ImportSource.kt` with:

```kotlin
package ph.nextbank.drums.data.model

enum class ImportSource { BUNDLED, PDF, IMAGE }
```

- [ ] **Step 2: Replace the Spotify pill in `LibraryScreen.kt`**

In `LibraryScreen.kt`, change the `filtered` computation (around line 67–74) from:

```kotlin
val filtered = remember(songs, tab) {
    when (tab) {
        "Recent" -> songs.filter { it.lastPlayed != null }
        "Spotify" -> songs.filter { it.importedFrom == ImportSource.SPOTIFY }
        else -> songs
    }
}
```

to:

```kotlin
val filtered = remember(songs, tab) {
    when (tab) {
        "Recent" -> songs.filter { it.lastPlayed != null }
        "Bundled" -> songs.filter { it.importedFrom == ImportSource.BUNDLED }
        else -> songs
    }
}
```

Change the `sectionLabel` (around line 75–79) from:

```kotlin
val sectionLabel = when (tab) {
    "Recent" -> "RECENTLY PLAYED"
    "Spotify" -> "FROM SPOTIFY"
    else -> "ALL SONGS"
}
```

to:

```kotlin
val sectionLabel = when (tab) {
    "Recent" -> "RECENTLY PLAYED"
    "Bundled" -> "BUNDLED SONGS"
    else -> "ALL SONGS"
}
```

Change the `emptyMessage` (around line 80–84) from:

```kotlin
val emptyMessage = when (tab) {
    "Recent" -> "You haven't played any songs yet."
    "Spotify" -> "Connect Spotify to see your tracks here."
    else -> "Tap the + button to add a song."
}
```

to:

```kotlin
val emptyMessage = when (tab) {
    "Recent" -> "You haven't played any songs yet."
    "Bundled" -> "No bundled songs found."
    else -> "Tap the + button to add a song."
}
```

Change the pill row (around line 110) from:

```kotlin
listOf("All ${songs.size}", "Recent", "Spotify").forEach { label ->
```

to:

```kotlin
listOf("All ${songs.size}", "Recent", "Bundled").forEach { label ->
```

- [ ] **Step 3: Compile**

```bash
export ANDROID_HOME=/home/sara/Android/Sdk JAVA_HOME=/home/sara/Downloads/android-studio-panda4-patch1-linux/android-studio/jbr
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If any file still imports `ImportSource.SPOTIFY`, fix the reference (delete the line or replace with `BUNDLED`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/model/ImportSource.kt \
        app/src/main/java/ph/nextbank/drums/ui/library/LibraryScreen.kt
git commit -m "Replace Spotify pill with Bundled, drop SPOTIFY enum value"
```

---

### Task 2: Remove "Connect Spotify" row from Upload

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/upload/UploadScreen.kt` (line 100)

- [ ] **Step 1: Delete the row**

Delete this entire line (currently line 100):

```kotlin
        SourceRow(Icons.Filled.LibraryMusic, false, "Connect Spotify", "Drum along to tracks you're playing") { comingSoon() }
```

Also delete the `Spacer(Modifier.height(8.dp))` immediately above it (line 99) so we don't leave a dangling spacer.

If `Icons.Filled.LibraryMusic` is no longer referenced anywhere in the file, remove its import.

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/upload/UploadScreen.kt
git commit -m "Drop Connect Spotify row from Upload screen"
```

---

## Milestone 1 — Data model + DB migration v3 (Tasks 3–5)

### Task 3: Add `youtubeOffsetMs` + `youtubeBlocklist` to `Song`

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/data/model/Song.kt`

- [ ] **Step 1: Add the two new fields**

Replace the entire contents of `Song.kt` with:

```kotlin
package ph.nextbank.drums.data.model

import java.time.Instant

/**
 * A song is a sequence of bars; each bar is a sequence of subdivision slots;
 * each slot is a (possibly empty) list of drum tokens hit at that subdivision.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val timeSig: Pair<Int, Int>,
    val bars: List<List<List<DrumToken>>>,
    val coverInitials: String,
    val importedFrom: ImportSource,
    val lastPlayed: Instant?,
    /** 11-char YouTube video ID. null = the app will search YouTube on first open. */
    val youtubeVideoId: String? = null,
    /** Sync nudge: positive = video plays earlier relative to staff bar 1. */
    val youtubeOffsetMs: Int = 0,
    /** YouTube video IDs the user said "Try another video" on. */
    val youtubeBlocklist: List<String> = emptyList(),
) {
    val totalBars: Int get() = bars.size
    val slotsPerBar: Int get() = bars.firstOrNull()?.size ?: 16
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD FAILED — `SongEntity.toSong()` and `fromSong()` no longer compile because the new fields aren't mapped. That's expected and is fixed in Task 4.

- [ ] **Step 3: Do not commit yet** — Task 4 finishes the model change.

---

### Task 4: Add columns + `MIGRATION_2_3` to Room

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt`

- [ ] **Step 1: Add the two new columns to `SongEntity`**

Replace the entire contents of `SongEntity.kt` with:

```kotlin
package ph.nextbank.drums.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import java.time.Instant

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val beatsPerBar: Int,
    val beatUnit: Int,
    val barsEncoded: String,
    val coverInitials: String,
    val importedFrom: ImportSource,
    val lastPlayedEpochMs: Long?,
    val youtubeVideoId: String?,
    val youtubeOffsetMs: Int,
    /** Comma-separated YouTube IDs. Empty string = no blocklist. */
    val youtubeBlocklist: String,
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        bpm = bpm,
        timeSig = beatsPerBar to beatUnit,
        bars = decodeBars(barsEncoded),
        coverInitials = coverInitials,
        importedFrom = importedFrom,
        lastPlayed = lastPlayedEpochMs?.let(Instant::ofEpochMilli),
        youtubeVideoId = youtubeVideoId,
        youtubeOffsetMs = youtubeOffsetMs,
        youtubeBlocklist = decodeBlocklist(youtubeBlocklist),
    )

    companion object {
        fun fromSong(s: Song): SongEntity = SongEntity(
            id = s.id,
            title = s.title,
            artist = s.artist,
            bpm = s.bpm,
            beatsPerBar = s.timeSig.first,
            beatUnit = s.timeSig.second,
            barsEncoded = encodeBars(s.bars),
            coverInitials = s.coverInitials,
            importedFrom = s.importedFrom,
            lastPlayedEpochMs = s.lastPlayed?.toEpochMilli(),
            youtubeVideoId = s.youtubeVideoId,
            youtubeOffsetMs = s.youtubeOffsetMs,
            youtubeBlocklist = encodeBlocklist(s.youtubeBlocklist),
        )

        internal fun encodeBars(bars: List<List<List<DrumToken>>>): String =
            bars.joinToString("|") { bar ->
                bar.joinToString(",") { slot -> slot.joinToString("+") { it.code } }
            }

        internal fun decodeBars(s: String): List<List<List<DrumToken>>> =
            s.split("|").map { bar ->
                bar.split(",").map { slot ->
                    if (slot.isEmpty()) emptyList()
                    else slot.split("+").map(DrumToken::fromCode)
                }
            }

        internal fun encodeBlocklist(ids: List<String>): String = ids.joinToString(",")
        internal fun decodeBlocklist(s: String): List<String> =
            if (s.isEmpty()) emptyList() else s.split(",")
    }
}
```

- [ ] **Step 2: Add `MIGRATION_2_3` and bump version**

Replace `AppDatabase.kt`'s `@Database` annotation and `companion object` with:

```kotlin
@Database(entities = [SongEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        /** v1 → v2: added youtubeVideoId column for YouTube-synced playback. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN youtubeVideoId TEXT")
            }
        }

        /** v2 → v3: added youtubeOffsetMs + youtubeBlocklist for sync nudge + "Try another video". */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN youtubeOffsetMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN youtubeBlocklist TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(ctx: Context, scope: CoroutineScope): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "drums.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) { /* no-op */ }
                    }
                })
                .build()
    }
}
```

(Keep the existing imports.)

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/model/Song.kt \
        app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt \
        app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt
git commit -m "Add youtubeOffsetMs + youtubeBlocklist (DB migration v3)"
```

---

### Task 5: Clear hardcoded `youtubeVideoId` in sample songs

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt`

- [ ] **Step 1: Remove the five `youtubeVideoId = "..."` lines**

In `SampleSongs.kt`, find each `youtubeVideoId = "..."` line (5 occurrences, currently around lines 80, 92, 104, 116, 128) and **delete the entire line**. Do not replace with `null` — the field has a `null` default in the model.

Verification:

```bash
grep -n "youtubeVideoId" app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt
```

Expected: no output (zero matches).

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt
git commit -m "Clear hardcoded YouTube IDs on sample songs"
```

> **Note**: The `idempotent seed` in `AppModule.provideSongRepository` runs `repo.upsertAll(SAMPLE_SONGS)` on every cold start with `OnConflictStrategy.REPLACE`. After this change, a user upgrading from a previous build will have their cached `youtubeVideoId` values **overwritten back to null** on next launch. That's acceptable — the search-and-confirm flow will repopulate them. If we want to preserve user choices later, we'd switch from `REPLACE` to a "skip if exists" insert in M5+.

---

## Milestone 2 — YouTube search service (Tasks 6–8)

### Task 6: Define `YouTubeSearchService` interface + tests

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeSearchService.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/youtube/FakeYouTubeSearchService.kt`

- [ ] **Step 1: Create the interface**

Write to `YouTubeSearchService.kt`:

```kotlin
package ph.nextbank.drums.audio.youtube

interface YouTubeSearchService {
    /**
     * Search YouTube for [query] and return the first video result whose ID
     * isn't in [blocklist]. Returns null if no results or if the network/search
     * fails.
     */
    suspend fun findFor(query: String, blocklist: Set<String> = emptySet()): SearchResult?
}

data class SearchResult(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val durationSec: Int,
    val thumbnailUrl: String,
)
```

- [ ] **Step 2: Create the fake (test-only)**

Write to `app/src/test/java/ph/nextbank/drums/audio/youtube/FakeYouTubeSearchService.kt`:

```kotlin
package ph.nextbank.drums.audio.youtube

class FakeYouTubeSearchService : YouTubeSearchService {
    /** Ordered list of results the fake will return. Each `findFor` call pops the next non-blocklisted item. */
    var queue: List<SearchResult> = emptyList()
    var lastQuery: String? = null
    var lastBlocklist: Set<String> = emptySet()
    var shouldReturnNull: Boolean = false

    override suspend fun findFor(query: String, blocklist: Set<String>): SearchResult? {
        lastQuery = query
        lastBlocklist = blocklist
        if (shouldReturnNull) return null
        return queue.firstOrNull { it.videoId !in blocklist }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/youtube/YouTubeSearchService.kt \
        app/src/test/java/ph/nextbank/drums/audio/youtube/FakeYouTubeSearchService.kt
git commit -m "Add YouTubeSearchService interface + test fake"
```

---

### Task 7: Add NewPipe Extractor + OkHttp dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions + library entries to `libs.versions.toml`**

In `[versions]`, add:

```toml
newpipe-extractor = "0.24.3"
okhttp = "4.12.0"
```

In `[libraries]`, add:

```toml
newpipe-extractor = { module = "com.github.TeamNewPipe:NewPipeExtractor", version.ref = "newpipe-extractor" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
```

- [ ] **Step 2: Add JitPack repo to `settings.gradle.kts`**

Open `settings.gradle.kts`. In the `dependencyResolutionManagement { repositories { ... } }` block, add `maven { url = uri("https://jitpack.io") }` after the existing `mavenCentral()` line. The block should look approximately like:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

If the existing block uses a different repositoriesMode, leave it alone — only add the `maven { ... }` line.

- [ ] **Step 3: Add dependencies in `app/build.gradle.kts`**

In the `dependencies { ... }` block, just below the existing `implementation(libs.youtube.player)` line, add:

```kotlin
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
```

- [ ] **Step 4: Verify Gradle sync**

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep -iE "newpipe|okhttp"
```

Expected: lines mentioning `com.github.TeamNewPipe:NewPipeExtractor:0.24.3` and `com.squareup.okhttp3:okhttp:4.12.0`. If `NewPipeExtractor` doesn't resolve, double-check the JitPack URL and that the JitPack maven block landed inside `dependencyResolutionManagement.repositories`.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts app/build.gradle.kts
git commit -m "Add NewPipe Extractor + OkHttp deps for YouTube search"
```

---

### Task 8: Implement `NewPipeYouTubeSearchService`

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeDownloader.kt`
- Create: `app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeYouTubeSearchService.kt`

- [ ] **Step 1: OkHttp-backed Downloader for NewPipe**

NewPipe Extractor needs a `Downloader` implementation. Write to `NewPipeDownloader.kt`:

```kotlin
package ph.nextbank.drums.audio.youtube

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import java.util.concurrent.TimeUnit

class NewPipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NpRequest): NpResponse {
        val builder = Request.Builder().url(request.url())
        request.headers().forEach { (k, vs) ->
            vs.forEach { v -> builder.addHeader(k, v) }
        }
        val data = request.dataToSend()
        val body = data?.toRequestBody()
        val httpReq = when (request.httpMethod().uppercase()) {
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody()).build()
            "PUT" -> builder.put(body ?: ByteArray(0).toRequestBody()).build()
            "HEAD" -> builder.head().build()
            else -> builder.get().build()
        }
        client.newCall(httpReq).execute().use { resp ->
            val responseHeaders = resp.headers.toMultimap()
            val responseBody = resp.body?.string() ?: ""
            return NpResponse(
                resp.code,
                resp.message,
                responseHeaders,
                responseBody,
                resp.request.url.toString(),
            )
        }
    }
}
```

- [ ] **Step 2: Implement the service**

Write to `NewPipeYouTubeSearchService.kt`:

```kotlin
package ph.nextbank.drums.audio.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class NewPipeYouTubeSearchService(
    private val initialized: Boolean = ensureInit(),
) : YouTubeSearchService {

    override suspend fun findFor(query: String, blocklist: Set<String>): SearchResult? =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = ServiceList.YouTube
                val handler = service.searchQHFactory.fromQuery(
                    query,
                    listOf("videos"),
                    "",
                )
                val extractor = service.getSearchExtractor(handler)
                extractor.fetchPage()
                val items = extractor.initialPage.items
                items.filterIsInstance<StreamInfoItem>()
                    .asSequence()
                    .mapNotNull { item ->
                        val id = item.url?.let(::extractVideoId) ?: return@mapNotNull null
                        if (id in blocklist) return@mapNotNull null
                        SearchResult(
                            videoId = id,
                            title = item.name ?: "",
                            channelTitle = item.uploaderName ?: "",
                            durationSec = item.duration.toInt().coerceAtLeast(0),
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                        )
                    }
                    .firstOrNull()
            }.getOrNull()
        }

    /** Extract the 11-char video ID from a YouTube URL like https://www.youtube.com/watch?v=XXXXXXXXXXX. */
    private fun extractVideoId(url: String): String? {
        val regex = Regex("""[?&]v=([A-Za-z0-9_-]{11})""")
        val direct = regex.find(url)?.groupValues?.getOrNull(1)
        if (direct != null) return direct
        val short = Regex("""youtu\.be/([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.getOrNull(1)
        return short
    }

    companion object {
        @Volatile private var inited = false

        private fun ensureInit(): Boolean {
            synchronized(this) {
                if (!inited) {
                    NewPipe.init(NewPipeDownloader(), Localization.DEFAULT)
                    inited = true
                }
            }
            return true
        }
    }
}
```

- [ ] **Step 3: Wire it into Hilt**

In `AppModule.kt`, add (alongside the existing `@Provides` functions):

```kotlin
    @Provides @Singleton
    fun provideYouTubeSearchService(): ph.nextbank.drums.audio.youtube.YouTubeSearchService =
        ph.nextbank.drums.audio.youtube.NewPipeYouTubeSearchService()
```

Add the import at the top if cleaner:

```kotlin
import ph.nextbank.drums.audio.youtube.NewPipeYouTubeSearchService
import ph.nextbank.drums.audio.youtube.YouTubeSearchService
```

- [ ] **Step 4: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual smoke check (optional — needs network)**

Open Android Studio. From `NewPipeYouTubeSearchService.kt`, add this temporary `main` (outside the class), run, then **delete the `main` before committing**:

```kotlin
suspend fun main() {
    val svc = NewPipeYouTubeSearchService()
    println(svc.findFor("Nirvana Smells Like Teen Spirit"))
}
```

Or skip this and rely on Task 17's manual verification — the production wiring exercises the path end-to-end.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeDownloader.kt \
        app/src/main/java/ph/nextbank/drums/audio/youtube/NewPipeYouTubeSearchService.kt \
        app/src/main/java/ph/nextbank/drums/di/AppModule.kt
git commit -m "Implement NewPipeYouTubeSearchService with OkHttp downloader"
```

---

## Milestone 3 — PlaybackSource abstraction (Tasks 9–11)

### Task 9: Define `PlaybackSource` interface + `PlaybackState`

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/playback/PlaybackSource.kt`

- [ ] **Step 1: Write the interface + state enum**

Write to `PlaybackSource.kt`:

```kotlin
package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState {
    Idle,        // not yet started anything
    Loading,     // YouTube player loading the video
    Ready,       // ready to play
    Playing,
    Paused,
    Finished,    // song ended (non-looping)
    Error,       // unrecoverable for this source — caller should fall back
}

interface PlaybackSource {
    val state: StateFlow<PlaybackState>
    /** Slot index (floating-point) currently under the playhead. */
    val currentSlot: StateFlow<Float>
    /** Drum tokens currently sounding — used by the chip strip. Always derivable from currentSlot + song, but exposed for convenience. */
    val activeSlotIndex: StateFlow<Int>

    fun play()
    fun pause()
    fun stop()
    /** Adjust YouTube/audio offset by [deltaMs]. No-op for sources that don't have an audio offset. */
    fun nudgeOffset(deltaMs: Int)
    /** Lifecycle cleanup. After this, the source is unusable. */
    fun release()
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/playback/PlaybackSource.kt
git commit -m "Add PlaybackSource sealed-style interface + PlaybackState enum"
```

---

### Task 10: Implement `SyntheticPlaybackSource` (wraps existing `SongClock`)

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/playback/SyntheticPlaybackSource.kt`

- [ ] **Step 1: Implement**

This source preserves Phase 1 behavior — `SongClock` drives the cursor, `DrumSampleBank` plays each slot's drum tokens.

Write to `SyntheticPlaybackSource.kt`:

```kotlin
package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.DrumSampleBank
import ph.nextbank.drums.audio.SongClock
import ph.nextbank.drums.data.model.Song

class SyntheticPlaybackSource(
    private val song: Song,
    private val bank: DrumSampleBank,
    private val scope: CoroutineScope,
    /** Wall-clock provider. Tests can override. */
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) : PlaybackSource {

    private val clock = SongClock(
        bpmProvider = { song.bpm },
        totalSlots = song.totalBars * song.slotsPerBar,
    )

    private val _state = MutableStateFlow(PlaybackState.Ready)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentSlot = MutableStateFlow(0f)
    override val currentSlot: StateFlow<Float> = _currentSlot.asStateFlow()

    private val _activeSlotIndex = MutableStateFlow(0)
    override val activeSlotIndex: StateFlow<Int> = _activeSlotIndex.asStateFlow()

    private var frameJob: Job? = null

    override fun play() {
        clock.play(nowMs())
        _state.value = PlaybackState.Playing
        startFrameLoop()
    }

    override fun pause() {
        clock.pause(nowMs())
        _state.value = PlaybackState.Paused
        frameJob?.cancel()
        frameJob = null
    }

    override fun stop() {
        clock.stop()
        _state.value = PlaybackState.Ready
        _currentSlot.value = 0f
        _activeSlotIndex.value = 0
        frameJob?.cancel()
        frameJob = null
    }

    override fun nudgeOffset(deltaMs: Int) { /* no-op for synth */ }

    override fun release() {
        frameJob?.cancel()
        frameJob = null
    }

    private fun startFrameLoop() {
        frameJob?.cancel()
        frameJob = scope.launch {
            while (true) {
                val now = nowMs()
                if (clock.isFinished(now)) {
                    clock.pause(now)
                    _state.value = PlaybackState.Finished
                    break
                }
                val cur = clock.currentSlot(now)
                _currentSlot.value = cur
                _activeSlotIndex.value = cur.toInt()
                clock.slotsJustEntered(now).forEach { idx ->
                    val barIdx = idx / song.slotsPerBar
                    val slotIdx = idx % song.slotsPerBar
                    song.bars.getOrNull(barIdx)?.get(slotIdx)?.forEach(bank::play)
                }
                delay(16)  // ~60 fps
            }
        }
    }
}
```

> The existing `PlayerScreen.kt` drives the frame loop with `withFrameNanos`. To keep the source independently testable, this implementation runs its own coroutine-based loop. We accept ~16 ms granularity — sufficient for audio scheduling, and the visual cursor will be re-sampled by the Composable anyway.

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/playback/SyntheticPlaybackSource.kt
git commit -m "Add SyntheticPlaybackSource wrapping SongClock + DrumSampleBank"
```

---

### Task 11: Implement `YouTubePlaybackSource` (TDD)

**Files:**
- Create: `app/src/test/java/ph/nextbank/drums/audio/playback/FakeYouTubeAdapter.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSourceTest.kt`
- Create: `app/src/main/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSource.kt`

The source consumes a thin `YouTubeAdapter` interface that hides the actual `YouTubePlayer` from the lib — so tests can fake it without depending on the YouTube SDK.

- [ ] **Step 1: Define `YouTubeAdapter` inside `YouTubePlaybackSource.kt`**

We'll create the source file first with just the adapter interface (and an empty class) so the test compiles. Write to `YouTubePlaybackSource.kt`:

```kotlin
package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper around the YouTubePlayer lib. The production adapter calls
 * into com.pierfrancescosoffritti.androidyoutubeplayer; tests use a fake.
 */
interface YouTubeAdapter {
    fun play()
    fun pause()
    fun stop()
    fun seekToSeconds(seconds: Float)

    /** Receives YouTube callbacks. Implementations forward `onCurrentSecond`, `onReady`, etc. */
    fun setListener(listener: YouTubeAdapterListener)
}

interface YouTubeAdapterListener {
    fun onReady()
    fun onPlay()
    fun onPause()
    fun onEnded()
    fun onError(message: String)
    /** Called approximately once per second by the YouTube IFrame API. */
    fun onCurrentSecond(seconds: Float)
}

class YouTubePlaybackSource(
    private val songBpm: Int,
    private val slotsPerBeat: Int,
    private val totalSlots: Int,
    private val adapter: YouTubeAdapter,
    initialOffsetMs: Int,
) : PlaybackSource {

    override val state: StateFlow<PlaybackState> get() = TODO()
    override val currentSlot: StateFlow<Float> get() = TODO()
    override val activeSlotIndex: StateFlow<Int> get() = TODO()

    var offsetMs: Int = initialOffsetMs
        private set

    override fun play() = TODO()
    override fun pause() = TODO()
    override fun stop() = TODO()
    override fun nudgeOffset(deltaMs: Int) { offsetMs += deltaMs }
    override fun release() = TODO()
}
```

- [ ] **Step 2: Write the fake adapter**

Write to `app/src/test/java/ph/nextbank/drums/audio/playback/FakeYouTubeAdapter.kt`:

```kotlin
package ph.nextbank.drums.audio.playback

class FakeYouTubeAdapter : YouTubeAdapter {
    var listener: YouTubeAdapterListener? = null
    val playCalls = mutableListOf<Unit>()
    val pauseCalls = mutableListOf<Unit>()
    val stopCalls = mutableListOf<Unit>()
    val seekCalls = mutableListOf<Float>()

    override fun play() { playCalls.add(Unit) }
    override fun pause() { pauseCalls.add(Unit) }
    override fun stop() { stopCalls.add(Unit) }
    override fun seekToSeconds(seconds: Float) { seekCalls.add(seconds) }
    override fun setListener(listener: YouTubeAdapterListener) { this.listener = listener }

    fun simulateReady() = listener!!.onReady()
    fun simulatePlay() = listener!!.onPlay()
    fun simulatePause() = listener!!.onPause()
    fun simulateEnded() = listener!!.onEnded()
    fun simulateError(msg: String) = listener!!.onError(msg)
    fun simulateSecond(s: Float) = listener!!.onCurrentSecond(s)
}
```

- [ ] **Step 3: Write the failing tests**

Write to `app/src/test/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSourceTest.kt`:

```kotlin
package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubePlaybackSourceTest {

    /**
     * 120 BPM, 4 slots/beat → msPerSlot = 500 / 4 = 125ms.
     * onCurrentSecond at 1.0s with offset 0 → slot = 1000 / 125 = 8.
     */
    @Test
    fun `slot computed from currentSecond using bpm and offset`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            songBpm = 120,
            slotsPerBeat = 4,
            totalSlots = 128,
            adapter = fake,
            initialOffsetMs = 0,
        )
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(1.0f)
        assertEquals(8f, source.currentSlot.first(), 0.01f)
    }

    @Test
    fun `positive offset shifts video earlier — slot is larger at the same currentSecond`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            songBpm = 120,
            slotsPerBeat = 4,
            totalSlots = 128,
            adapter = fake,
            initialOffsetMs = 250,
        )
        fake.simulateReady()
        fake.simulatePlay()
        // currentSecond = 1.0s; effective audio position = 1.0 + 0.25 = 1.25s → slot = 1250/125 = 10
        fake.simulateSecond(1.0f)
        assertEquals(10f, source.currentSlot.first(), 0.01f)
    }

    @Test
    fun `onReady transitions Idle to Ready`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        assertEquals(PlaybackState.Idle, source.state.first())
        fake.simulateReady()
        assertEquals(PlaybackState.Ready, source.state.first())
    }

    @Test
    fun `play calls adapter play and transitions to Playing on onPlay`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateReady()
        source.play()
        assertEquals(1, fake.playCalls.size)
        fake.simulatePlay()
        assertEquals(PlaybackState.Playing, source.state.first())
    }

    @Test
    fun `onEnded transitions to Finished`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateReady()
        source.play()
        fake.simulatePlay()
        fake.simulateEnded()
        assertEquals(PlaybackState.Finished, source.state.first())
    }

    @Test
    fun `onError transitions to Error`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateError("video unavailable")
        assertEquals(PlaybackState.Error, source.state.first())
    }

    @Test
    fun `nudgeOffset adjusts the slot derivation on next onCurrentSecond`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(1.0f)
        assertEquals(8f, source.currentSlot.first(), 0.01f)
        source.nudgeOffset(125) // shift +125ms = +1 slot at this bpm
        fake.simulateSecond(1.0f)
        assertEquals(9f, source.currentSlot.first(), 0.01f)
    }

    @Test
    fun `activeSlotIndex is currentSlot truncated to int`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(1.07f)  // → slot = 1070/125 = 8.56 → int 8
        assertEquals(8, source.activeSlotIndex.first())
    }
}
```

- [ ] **Step 4: Run the tests — expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.audio.playback.YouTubePlaybackSourceTest"
```

Expected: failures — all methods of `YouTubePlaybackSource` currently throw `NotImplementedError()`.

- [ ] **Step 5: Implement `YouTubePlaybackSource`**

Replace the entire contents of `YouTubePlaybackSource.kt` with:

```kotlin
package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface YouTubeAdapter {
    fun play()
    fun pause()
    fun stop()
    fun seekToSeconds(seconds: Float)
    fun setListener(listener: YouTubeAdapterListener)
}

interface YouTubeAdapterListener {
    fun onReady()
    fun onPlay()
    fun onPause()
    fun onEnded()
    fun onError(message: String)
    fun onCurrentSecond(seconds: Float)
}

class YouTubePlaybackSource(
    private val songBpm: Int,
    private val slotsPerBeat: Int,
    private val totalSlots: Int,
    private val adapter: YouTubeAdapter,
    initialOffsetMs: Int,
) : PlaybackSource {

    private val _state = MutableStateFlow(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentSlot = MutableStateFlow(0f)
    override val currentSlot: StateFlow<Float> = _currentSlot.asStateFlow()

    private val _activeSlotIndex = MutableStateFlow(0)
    override val activeSlotIndex: StateFlow<Int> = _activeSlotIndex.asStateFlow()

    var offsetMs: Int = initialOffsetMs
        private set

    private val msPerSlot: Float = (60_000f / songBpm) / slotsPerBeat

    init {
        adapter.setListener(object : YouTubeAdapterListener {
            override fun onReady() {
                if (_state.value == PlaybackState.Idle) _state.value = PlaybackState.Ready
            }
            override fun onPlay() { _state.value = PlaybackState.Playing }
            override fun onPause() {
                if (_state.value == PlaybackState.Playing) _state.value = PlaybackState.Paused
            }
            override fun onEnded() { _state.value = PlaybackState.Finished }
            override fun onError(message: String) { _state.value = PlaybackState.Error }
            override fun onCurrentSecond(seconds: Float) {
                val effectiveMs = seconds * 1000f + offsetMs
                val slot = effectiveMs / msPerSlot
                val clamped = slot.coerceIn(0f, totalSlots.toFloat() - 0.001f)
                _currentSlot.value = clamped
                _activeSlotIndex.value = clamped.toInt()
            }
        })
    }

    override fun play() { adapter.play() }
    override fun pause() { adapter.pause() }
    override fun stop() {
        adapter.stop()
        _state.value = PlaybackState.Ready
        _currentSlot.value = 0f
        _activeSlotIndex.value = 0
    }
    override fun nudgeOffset(deltaMs: Int) { offsetMs += deltaMs }
    override fun release() {
        // Adapter owns the actual YouTubePlayer lifecycle; nothing to do here
        // beyond letting the GC reclaim the closure.
    }
}
```

- [ ] **Step 6: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.audio.playback.YouTubePlaybackSourceTest"
```

Expected: 8 tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSource.kt \
        app/src/test/java/ph/nextbank/drums/audio/playback/FakeYouTubeAdapter.kt \
        app/src/test/java/ph/nextbank/drums/audio/playback/YouTubePlaybackSourceTest.kt
git commit -m "Add YouTubePlaybackSource with TDD-verified slot math"
```

---

## Milestone 4 — Compose YouTube embed (Task 12)

### Task 12: `YouTubeEmbed` Composable + production adapter

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/player/YouTubeEmbed.kt`

The Compose wrapper hands the `YouTubePlayer` from the lib up to the ViewModel via a callback (or via the adapter pattern from Task 11).

- [ ] **Step 1: Write the embed + production adapter**

Write to `YouTubeEmbed.kt`:

```kotlin
package ph.nextbank.drums.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import ph.nextbank.drums.audio.playback.YouTubeAdapter
import ph.nextbank.drums.audio.playback.YouTubeAdapterListener

/**
 * Compose embed for a YouTube video. The [onAdapterReady] callback fires once the
 * underlying `YouTubePlayer` is initialized — pass the resulting adapter to your
 * `YouTubePlaybackSource`. The composable owns the `YouTubePlayerView` lifecycle.
 */
@Composable
fun YouTubeEmbed(
    videoId: String,
    modifier: Modifier = Modifier,
    onAdapterReady: (YouTubeAdapter) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(videoId) { mutableHolder<YouTubePlayerView>() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val opts = IFramePlayerOptions.Builder()
                .controls(0)
                .fullscreen(0)
                .autoplay(0)
                .build()
            YouTubePlayerView(ctx).apply {
                enableAutomaticInitialization = false
                val adapter = YouTubePlayerAdapter()
                initialize(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        adapter.bind(youTubePlayer)
                        adapter.listener?.onReady()
                        youTubePlayer.cueVideo(videoId, 0f)
                    }
                    override fun onStateChange(
                        youTubePlayer: YouTubePlayer,
                        s: PlayerConstants.PlayerState,
                    ) {
                        when (s) {
                            PlayerConstants.PlayerState.PLAYING -> adapter.listener?.onPlay()
                            PlayerConstants.PlayerState.PAUSED -> adapter.listener?.onPause()
                            PlayerConstants.PlayerState.ENDED -> adapter.listener?.onEnded()
                            else -> { /* ignore intermediate states */ }
                        }
                    }
                    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                        adapter.listener?.onCurrentSecond(second)
                    }
                    override fun onError(
                        youTubePlayer: YouTubePlayer,
                        error: PlayerConstants.PlayerError,
                    ) {
                        adapter.listener?.onError(error.name)
                    }
                }, opts)
                onAdapterReady(adapter)
                view.value = this
            }
        },
    )

    DisposableEffect(lifecycleOwner, view) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) view.value?.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.value?.release()
        }
    }
}

private class Holder<T>(var value: T? = null)
private fun <T> mutableHolder() = Holder<T>()

/**
 * Production [YouTubeAdapter] backed by the YouTubePlayer SDK. The actual `YouTubePlayer`
 * reference is bound asynchronously via [bind] once `onReady` fires.
 */
class YouTubePlayerAdapter : YouTubeAdapter {
    var listener: YouTubeAdapterListener? = null
    private var player: YouTubePlayer? = null

    fun bind(p: YouTubePlayer) { player = p }

    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun stop() {
        // YouTubePlayer lib has no `stop()`; pause + seek to 0 is the closest equivalent.
        player?.pause()
        player?.seekTo(0f)
    }
    override fun seekToSeconds(seconds: Float) { player?.seekTo(seconds) }
    override fun setListener(listener: YouTubeAdapterListener) { this.listener = listener }
}
```

Note: `Holder<T>` and `mutableHolder()` are private helpers defined at file scope (see the bottom of the file above). Don't let the IDE add an import for `mutableHolder` — it's not from any library.

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/player/YouTubeEmbed.kt
git commit -m "Add YouTubeEmbed Composable + production YouTubePlayerAdapter"
```

---

## Milestone 5 — PlayerViewModel state machine (Tasks 13–14)

### Task 13: Rewrite `PlayerViewModel` with state machine + search

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/data/db/SongDao.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt`

- [ ] **Step 1: Add DAO methods for the new fields**

Replace `SongDao.kt`'s contents with:

```kotlin
package ph.nextbank.drums.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY (lastPlayedEpochMs IS NULL) ASC, lastPlayedEpochMs DESC, title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun findById(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Update
    suspend fun update(song: SongEntity)

    @Query("UPDATE songs SET bpm = :bpm WHERE id = :id")
    suspend fun updateBpm(id: String, bpm: Int)

    @Query("UPDATE songs SET youtubeVideoId = :videoId WHERE id = :id")
    suspend fun updateYoutubeVideoId(id: String, videoId: String?)

    @Query("UPDATE songs SET youtubeOffsetMs = :offsetMs WHERE id = :id")
    suspend fun updateYoutubeOffset(id: String, offsetMs: Int)

    @Query("UPDATE songs SET youtubeBlocklist = :blocklist WHERE id = :id")
    suspend fun updateYoutubeBlocklist(id: String, blocklist: String)
}
```

- [ ] **Step 2: Add repo passthrough methods**

Replace `SongRepository.kt`'s contents with:

```kotlin
package ph.nextbank.drums.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.nextbank.drums.data.db.SongDao
import ph.nextbank.drums.data.db.SongEntity
import ph.nextbank.drums.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(private val dao: SongDao) {
    fun observeAll(): Flow<List<Song>> = dao.observeAll().map { list -> list.map(SongEntity::toSong) }
    suspend fun findById(id: String): Song? = dao.findById(id)?.toSong()
    suspend fun upsertAll(songs: List<Song>) = dao.insertAll(songs.map(SongEntity::fromSong))
    suspend fun updateBpm(id: String, bpm: Int) = dao.updateBpm(id, bpm)
    suspend fun updateYoutubeVideoId(id: String, videoId: String?) = dao.updateYoutubeVideoId(id, videoId)
    suspend fun updateYoutubeOffset(id: String, offsetMs: Int) = dao.updateYoutubeOffset(id, offsetMs)
    suspend fun updateYoutubeBlocklist(id: String, blocklist: List<String>) =
        dao.updateYoutubeBlocklist(id, blocklist.joinToString(","))
}
```

- [ ] **Step 3: Rewrite `PlayerViewModel`**

This is the biggest single file change. Replace `PlayerViewModel.kt`'s contents with:

```kotlin
package ph.nextbank.drums.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.DrumSampleBank
import ph.nextbank.drums.audio.playback.PlaybackSource
import ph.nextbank.drums.audio.playback.PlaybackState
import ph.nextbank.drums.audio.playback.SyntheticPlaybackSource
import ph.nextbank.drums.audio.playback.YouTubeAdapter
import ph.nextbank.drums.audio.playback.YouTubePlaybackSource
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.audio.youtube.YouTubeSearchService
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import javax.inject.Inject

/** UI-facing flow phase. */
sealed interface PlayerPhase {
    /** Song hasn't loaded yet. */
    data object Loading : PlayerPhase
    /** Searching YouTube for the song's video. */
    data object Searching : PlayerPhase
    /** Search returned a result; waiting for user to accept or reject. */
    data class Confirming(val candidate: SearchResult) : PlayerPhase
    /** YouTube player is starting up. */
    data object YouTubeBuffering : PlayerPhase
    /** YouTube source is live. */
    data class YouTubeReady(val videoId: String) : PlayerPhase
    /** No video (search failed / user dismissed) — playing synth. */
    data object SynthFallback : PlayerPhase
}

data class PlayerUiState(
    val song: Song? = null,
    val phase: PlayerPhase = PlayerPhase.Loading,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val currentSlot: Float = 0f,
    val activeSlotIndex: Int = 0,
    val youtubeOffsetMs: Int = 0,
    val metronomeOn: Boolean = false,
    val looping: Boolean = false,
)

sealed interface PlayerEvent {
    data class Toast(val message: String) : PlayerEvent
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repo: SongRepository,
    private val bank: DrumSampleBank,
    private val searchService: YouTubeSearchService,
    handle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = handle.get<String>("songId")!!

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private var source: PlaybackSource? = null

    init {
        viewModelScope.launch {
            val s = repo.findById(songId) ?: return@launch
            _state.value = _state.value.copy(
                song = s,
                youtubeOffsetMs = s.youtubeOffsetMs,
            )
            if (s.youtubeVideoId != null) {
                _state.value = _state.value.copy(phase = PlayerPhase.YouTubeBuffering)
                // PlayerScreen will mount the YouTubeEmbed and call onAdapterReady.
            } else {
                runSearch(s, s.youtubeBlocklist.toSet())
            }
        }
    }

    private suspend fun runSearch(song: Song, blocklist: Set<String>) {
        _state.value = _state.value.copy(phase = PlayerPhase.Searching)
        val query = "${song.title} ${song.artist}"
        val result = searchService.findFor(query, blocklist)
        if (result == null) {
            _events.tryEmit(PlayerEvent.Toast("No YouTube result — playing synth drums"))
            switchToSynth()
        } else {
            _state.value = _state.value.copy(phase = PlayerPhase.Confirming(result))
        }
    }

    /** User tapped "Use this video" in the confirmation dialog. */
    fun acceptCandidate() {
        val cur = _state.value
        val candidate = (cur.phase as? PlayerPhase.Confirming)?.candidate ?: return
        viewModelScope.launch {
            repo.updateYoutubeVideoId(songId, candidate.videoId)
            _state.value = cur.copy(
                phase = PlayerPhase.YouTubeBuffering,
                song = cur.song?.copy(youtubeVideoId = candidate.videoId),
            )
        }
    }

    /** User tapped "Try another video" — either from dialog or three-dots menu. */
    fun tryAnotherVideo() {
        val cur = _state.value
        val song = cur.song ?: return
        val rejectedId: String? = when (val p = cur.phase) {
            is PlayerPhase.Confirming -> p.candidate.videoId
            is PlayerPhase.YouTubeReady -> p.videoId
            else -> null
        }
        viewModelScope.launch {
            val newBlocklist = (song.youtubeBlocklist + listOfNotNull(rejectedId)).distinct()
            repo.updateYoutubeBlocklist(songId, newBlocklist)
            if (rejectedId != null) {
                repo.updateYoutubeVideoId(songId, null)
            }
            // Tear down current source, restart search.
            source?.release(); source = null
            val updatedSong = song.copy(
                youtubeBlocklist = newBlocklist,
                youtubeVideoId = null,
            )
            _state.value = cur.copy(song = updatedSong)
            runSearch(updatedSong, newBlocklist.toSet())
        }
    }

    /** User dismissed the confirmation dialog without picking — fall back to synth for this session. */
    fun dismissConfirmation() {
        if (_state.value.phase is PlayerPhase.Confirming) switchToSynth()
    }

    /** PlayerScreen calls this with the production adapter once YouTubeEmbed wires it up. */
    fun bindYouTubeAdapter(adapter: YouTubeAdapter) {
        val s = _state.value.song ?: return
        if (_state.value.phase !is PlayerPhase.YouTubeBuffering) return
        val src = YouTubePlaybackSource(
            songBpm = s.bpm,
            slotsPerBeat = s.slotsPerBar / s.timeSig.first,
            totalSlots = s.totalBars * s.slotsPerBar,
            adapter = adapter,
            initialOffsetMs = s.youtubeOffsetMs,
        )
        source = src
        wireSource(src)
        viewModelScope.launch {
            src.state.collect { st ->
                if (st == PlaybackState.Ready &&
                    _state.value.phase is PlayerPhase.YouTubeBuffering
                ) {
                    val videoId = s.youtubeVideoId ?: return@collect
                    _state.value = _state.value.copy(phase = PlayerPhase.YouTubeReady(videoId))
                }
                if (st == PlaybackState.Error) {
                    _events.tryEmit(PlayerEvent.Toast("YouTube failed — falling back to synth"))
                    switchToSynth()
                }
            }
        }
    }

    private fun switchToSynth() {
        source?.release()
        val s = _state.value.song ?: return
        val synth = SyntheticPlaybackSource(s, bank, viewModelScope)
        source = synth
        wireSource(synth)
        _state.value = _state.value.copy(phase = PlayerPhase.SynthFallback)
    }

    private fun wireSource(src: PlaybackSource) {
        viewModelScope.launch {
            src.state.collect { _state.value = _state.value.copy(playbackState = it) }
        }
        viewModelScope.launch {
            src.currentSlot.collect { _state.value = _state.value.copy(currentSlot = it) }
        }
        viewModelScope.launch {
            src.activeSlotIndex.collect { _state.value = _state.value.copy(activeSlotIndex = it) }
        }
    }

    fun togglePlay() {
        val src = source ?: return
        when (src.state.value) {
            PlaybackState.Playing -> src.pause()
            PlaybackState.Ready, PlaybackState.Paused, PlaybackState.Finished -> src.play()
            else -> { /* Loading/Error/Idle — ignore */ }
        }
    }

    fun stop() { source?.stop() }

    fun nudgeOffset(deltaMs: Int) {
        val src = source ?: return
        src.nudgeOffset(deltaMs)
        val newOffset = (_state.value.youtubeOffsetMs + deltaMs)
        _state.value = _state.value.copy(youtubeOffsetMs = newOffset)
        viewModelScope.launch { repo.updateYoutubeOffset(songId, newOffset) }
    }

    fun toggleMetronome() {
        _state.value = _state.value.copy(metronomeOn = !_state.value.metronomeOn)
    }

    fun toggleLoop() {
        _state.value = _state.value.copy(looping = !_state.value.looping)
        // Phase 2: loop only affects synth (Phase 2B will integrate with YouTube seekTo).
    }

    override fun onCleared() {
        source?.release()
        super.onCleared()
    }
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD FAILED — `PlayerScreen.kt` still references the old `vm.onFrame`, `vm.clock`, `vm.bank`, etc. That's expected and is fixed in Task 15. Do not commit yet.

- [ ] **Step 5: Hold the commit until PlayerScreen compiles (Task 15)**

---

### Task 14: `PlayerViewModelTest` (TDD around the new state machine)

**Files:**
- Create: `app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt`
- Create: `app/src/test/java/ph/nextbank/drums/data/repo/FakeSongRepository.kt`

PlayerViewModel needs a `SongRepository` and a `DrumSampleBank`. Both are concrete classes today; for a JVM unit test we'll first refactor each behind an interface, then write the fakes and tests.

- [ ] **Step 1: Refactor `SongRepository` into an interface + Room impl**

Replace `app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt`'s contents with:

```kotlin
package ph.nextbank.drums.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.nextbank.drums.data.db.SongDao
import ph.nextbank.drums.data.db.SongEntity
import ph.nextbank.drums.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

interface SongRepository {
    fun observeAll(): Flow<List<Song>>
    suspend fun findById(id: String): Song?
    suspend fun upsertAll(songs: List<Song>)
    suspend fun updateBpm(id: String, bpm: Int)
    suspend fun updateYoutubeVideoId(id: String, videoId: String?)
    suspend fun updateYoutubeOffset(id: String, offsetMs: Int)
    suspend fun updateYoutubeBlocklist(id: String, blocklist: List<String>)
}

@Singleton
class RoomSongRepository @Inject constructor(private val dao: SongDao) : SongRepository {
    override fun observeAll(): Flow<List<Song>> = dao.observeAll().map { list -> list.map(SongEntity::toSong) }
    override suspend fun findById(id: String): Song? = dao.findById(id)?.toSong()
    override suspend fun upsertAll(songs: List<Song>) = dao.insertAll(songs.map(SongEntity::fromSong))
    override suspend fun updateBpm(id: String, bpm: Int) = dao.updateBpm(id, bpm)
    override suspend fun updateYoutubeVideoId(id: String, videoId: String?) = dao.updateYoutubeVideoId(id, videoId)
    override suspend fun updateYoutubeOffset(id: String, offsetMs: Int) = dao.updateYoutubeOffset(id, offsetMs)
    override suspend fun updateYoutubeBlocklist(id: String, blocklist: List<String>) =
        dao.updateYoutubeBlocklist(id, blocklist.joinToString(","))
}
```

Update `AppModule.kt` to provide the implementation:

```kotlin
    @Provides @Singleton
    fun provideSongRepository(dao: SongDao, scope: CoroutineScope): SongRepository {
        val repo: SongRepository = RoomSongRepository(dao)
        scope.launch { repo.upsertAll(SAMPLE_SONGS) }
        return repo
    }
```

(Add `import ph.nextbank.drums.data.repo.RoomSongRepository` at the top.)

- [ ] **Step 2: Update the existing `SongRepositoryTest` to use the implementation**

In `app/src/androidTest/java/ph/nextbank/drums/data/SongRepositoryTest.kt`, change any `SongRepository(dao)` calls to `RoomSongRepository(dao)`.

- [ ] **Step 3: Refactor `DrumSampleBank` behind an interface**

Edit `app/src/main/java/ph/nextbank/drums/audio/DrumSampleBank.kt`. Just above the existing `class DrumSampleBank(...)`, add:

```kotlin
interface DrumSampleBankApi {
    fun play(token: DrumToken)
}
```

Make `DrumSampleBank` implement it (change `class DrumSampleBank(...)` to `class DrumSampleBank(...) : DrumSampleBankApi`, and add `override` to its `play(token: DrumToken)` method).

In `SyntheticPlaybackSource.kt`, change the `bank` parameter type from `DrumSampleBank` to `DrumSampleBankApi`.

In `PlayerViewModel.kt`, change the `bank` parameter type from `DrumSampleBank` to `DrumSampleBankApi`.

In `AppModule.kt`, change the binding:

```kotlin
    @Provides @Singleton
    fun provideDrumSampleBank(@ApplicationContext ctx: Context): DrumSampleBankApi = DrumSampleBank(ctx)
```

Add `import ph.nextbank.drums.audio.DrumSampleBankApi`.

- [ ] **Step 4: Write the fake bank for tests**

Write to `app/src/test/java/ph/nextbank/drums/audio/FakeDrumSampleBank.kt`:

```kotlin
package ph.nextbank.drums.audio

import ph.nextbank.drums.data.model.DrumToken

class FakeDrumSampleBank : DrumSampleBankApi {
    val plays = mutableListOf<DrumToken>()
    override fun play(token: DrumToken) { plays.add(token) }
}
```

- [ ] **Step 5: Write the fake song repo**

Write to `app/src/test/java/ph/nextbank/drums/data/repo/FakeSongRepository.kt`:

```kotlin
package ph.nextbank.drums.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ph.nextbank.drums.data.model.Song

class FakeSongRepository : SongRepository {
    private val songs = MutableStateFlow<Map<String, Song>>(emptyMap())
    fun seed(song: Song) { songs.value = songs.value + (song.id to song) }
    fun snapshot(id: String): Song? = songs.value[id]

    override fun observeAll(): Flow<List<Song>> = songs.map { it.values.toList() }
    override suspend fun findById(id: String): Song? = songs.value[id]
    override suspend fun upsertAll(s: List<Song>) {
        songs.value = songs.value + s.associateBy { it.id }
    }
    override suspend fun updateBpm(id: String, bpm: Int) {
        songs.value = songs.value + (id to songs.value[id]!!.copy(bpm = bpm))
    }
    override suspend fun updateYoutubeVideoId(id: String, videoId: String?) {
        songs.value = songs.value + (id to songs.value[id]!!.copy(youtubeVideoId = videoId))
    }
    override suspend fun updateYoutubeOffset(id: String, offsetMs: Int) {
        songs.value = songs.value + (id to songs.value[id]!!.copy(youtubeOffsetMs = offsetMs))
    }
    override suspend fun updateYoutubeBlocklist(id: String, blocklist: List<String>) {
        songs.value = songs.value + (id to songs.value[id]!!.copy(youtubeBlocklist = blocklist))
    }
}
```

- [ ] **Step 6: Verify the project still compiles after the refactors**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If compilation fails, the most likely culprit is a leftover `SongRepository(dao)` constructor call somewhere — replace with `RoomSongRepository(dao)`.

- [ ] **Step 7: Write the PlayerViewModelTest**

```kotlin
package ph.nextbank.drums.ui.player

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.nextbank.drums.audio.FakeDrumSampleBank
import ph.nextbank.drums.audio.youtube.FakeYouTubeSearchService
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.FakeSongRepository

class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() { Dispatchers.setMain(dispatcher) }

    @After
    fun teardown() { Dispatchers.resetMain() }

    private fun songWithoutVideo(
        id: String = "test1",
        blocklist: List<String> = emptyList(),
    ) = Song(
        id = id,
        title = "Test Song",
        artist = "Tester",
        bpm = 120,
        timeSig = 4 to 4,
        bars = listOf(List(16) { emptyList<DrumToken>() }),
        coverInitials = "TT",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
        youtubeBlocklist = blocklist,
    )

    private fun mkVm(
        repo: FakeSongRepository,
        search: FakeYouTubeSearchService,
        songId: String = "test1",
    ) = PlayerViewModel(
        repo = repo,
        bank = FakeDrumSampleBank(),
        searchService = search,
        handle = SavedStateHandle(mapOf("songId" to songId)),
    )

    @Test
    fun `song without cached video triggers search`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(SearchResult("abc12345678", "Test Song - Tester", "Tester VEVO", 200, "thumb"))
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        val phase = vm.state.first().phase
        assertTrue(phase is PlayerPhase.Confirming)
        assertEquals("abc12345678", (phase as PlayerPhase.Confirming).candidate.videoId)
        assertEquals("Test Song Tester", search.lastQuery)
    }

    @Test
    fun `acceptCandidate caches videoId and moves to YouTubeBuffering`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(SearchResult("abc12345678", "T", "U", 100, ""))
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        vm.acceptCandidate()
        advanceUntilIdle()
        assertEquals(PlayerPhase.YouTubeBuffering, vm.state.first().phase)
        assertEquals("abc12345678", repo.snapshot("test1")!!.youtubeVideoId)
    }

    @Test
    fun `tryAnotherVideo blocklists current candidate and searches again`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(
                SearchResult("aaa11111111", "First", "U", 100, ""),
                SearchResult("bbb22222222", "Second", "U", 100, ""),
            )
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        // First candidate proposed
        vm.tryAnotherVideo()
        advanceUntilIdle()
        val phase = vm.state.first().phase
        assertTrue(phase is PlayerPhase.Confirming)
        assertEquals("bbb22222222", (phase as PlayerPhase.Confirming).candidate.videoId)
        assertTrue("aaa11111111" in repo.snapshot("test1")!!.youtubeBlocklist)
    }

    @Test
    fun `empty search result falls back to synth`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply { shouldReturnNull = true }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        assertEquals(PlayerPhase.SynthFallback, vm.state.first().phase)
    }

    @Test
    fun `dismissConfirmation falls back to synth`() = runTest {
        val repo = FakeSongRepository().apply { seed(songWithoutVideo()) }
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(SearchResult("abc12345678", "T", "U", 100, ""))
        }
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        vm.dismissConfirmation()
        advanceUntilIdle()
        assertEquals(PlayerPhase.SynthFallback, vm.state.first().phase)
    }

    @Test
    fun `song with cached video skips search and goes to YouTubeBuffering`() = runTest {
        val seed = songWithoutVideo().copy(youtubeVideoId = "ccc33333333")
        val repo = FakeSongRepository().apply { seed(seed) }
        val search = FakeYouTubeSearchService()
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        assertEquals(PlayerPhase.YouTubeBuffering, vm.state.first().phase)
        assertEquals(null, search.lastQuery)  // search not called
    }

    @Test
    fun `nudgeOffset persists to repo`() = runTest {
        val seed = songWithoutVideo().copy(youtubeVideoId = "ccc33333333", youtubeOffsetMs = 50)
        val repo = FakeSongRepository().apply { seed(seed) }
        val search = FakeYouTubeSearchService()
        val vm = mkVm(repo, search)
        advanceUntilIdle()
        // No adapter bound — nudge is a no-op on source, but should still update repo via the offset state.
        // For Phase 2 we accept that nudge before adapter binding is a UI-only edit; once bound the source picks up the latest.
        // This test just exercises the persistence path.
        vm.nudgeOffset(50)
        advanceUntilIdle()
        // Since source is null pre-bind, the impl currently early-returns. Update the impl
        // to always persist (see fix below).
        assertEquals(100, repo.snapshot("test1")!!.youtubeOffsetMs)
    }
}
```

The last test exposes a bug in the ViewModel: `nudgeOffset` early-returns if `source` is null. Fix it.

In `PlayerViewModel.kt`, change `nudgeOffset`:

```kotlin
    fun nudgeOffset(deltaMs: Int) {
        source?.nudgeOffset(deltaMs)
        val newOffset = _state.value.youtubeOffsetMs + deltaMs
        _state.value = _state.value.copy(youtubeOffsetMs = newOffset)
        viewModelScope.launch { repo.updateYoutubeOffset(songId, newOffset) }
    }
```

(Drop the early `return` — the persistence path runs even without a bound source.)

- [ ] **Step 8: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PlayerViewModelTest's 7 tests pass, plus the existing SongClockTest and DrumStaffLayoutTest tests pass, plus the YouTubePlaybackSourceTest from Task 11. Total: ≥ 8 + 2 + 8 = at least 18 tests passing.

If any fail with "no test results" or coroutine-test errors, double-check `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")` is in `app/build.gradle.kts` under `testImplementation`, not just `androidTestImplementation`. Add it if missing:

```kotlin
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
```

- [ ] **Step 9: Hold the commit until PlayerScreen compiles (Task 15)**

---

## Milestone 6 — PlayerScreen + Confirmation dialog (Tasks 15–16)

### Task 15: Rewrite `PlayerScreen` for the new state machine

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/player/PlayerScreen.kt`

Largest UI change. We:
1. Embed the YouTube player in the upper-right (`240×135 dp` in landscape).
2. Drive the staff cursor from `state.currentSlot`.
3. Show a loading spinner / synth pill depending on `state.phase`.
4. Show the confirmation dialog when `state.phase is Confirming`.
5. Drop synth-mode drum-sample scheduling here — the source handles it.
6. Add sync nudge buttons in the bottom bar.

- [ ] **Step 1: Replace `PlayerScreen.kt` contents**

```kotlin
package ph.nextbank.drums.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.audio.playback.PlaybackState
import ph.nextbank.drums.ui.components.DrumHitChips
import ph.nextbank.drums.ui.components.DrumStaff
import ph.nextbank.drums.ui.components.TransportButton
import ph.nextbank.drums.ui.components.TransportStyle
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun PlayerScreen(
    songId: String,
    onBack: () -> Unit,
    vm: PlayerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val song = state.song ?: return
    val ctx = LocalContext.current

    DisposableEffect(Unit) {
        val activity = ctx.findActivity()
        val prior = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                prior ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Toast events
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is PlayerEvent.Toast ->
                    Toast.makeText(ctx, ev.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(DrumsColors.Bg)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            HeaderRow(song.title, state, vm, onBack, ctx)

            // Body: staff (full width) + YouTube panel overlay (upper-right)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                StaffArea(state, song)
                YouTubePanel(
                    phase = state.phase,
                    onAdapterReady = vm::bindYouTubeAdapter,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .width(240.dp)
                        .aspectRatio(16f / 9f),
                )
            }

            DrumHitChips(
                activeTokens = remember(state.activeSlotIndex, song) {
                    val slot = state.activeSlotIndex.coerceIn(0, song.bars.size * song.slotsPerBar - 1)
                    val barIdx = slot / song.slotsPerBar
                    val slotIdx = slot % song.slotsPerBar
                    song.bars[barIdx][slotIdx].toSet()
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            BottomRow(state, vm)
        }

        // Confirmation dialog overlay
        val phase = state.phase
        if (phase is PlayerPhase.Confirming) {
            YouTubeConfirmDialog(
                candidate = phase.candidate,
                onUseThis = { vm.acceptCandidate() },
                onTryAnother = { vm.tryAnotherVideo() },
                onDismiss = { vm.dismissConfirmation() },
            )
        }
    }
}

@Composable
private fun HeaderRow(
    title: String,
    state: PlayerUiState,
    vm: PlayerViewModel,
    onBack: () -> Unit,
    ctx: android.content.Context,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBox(Icons.Filled.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f)) {
            Text("NOW READING", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text(title, color = DrumsColors.Text, style = DrumsType.cardTitle, maxLines = 1)
        }
        if (state.phase is PlayerPhase.SynthFallback) {
            Text(
                "SYNTH",
                color = DrumsColors.Dim, style = DrumsType.allCapsLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, DrumsColors.Dim, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Box {
            IconBox(Icons.Filled.MoreVert, "More") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Try another video") },
                    onClick = { menuOpen = false; vm.tryAnotherVideo() },
                )
            }
        }
    }
}

@Composable
private fun StaffArea(state: PlayerUiState, song: ph.nextbank.drums.data.model.Song) {
    val density = LocalDensity.current
    val barWidthDp = 380
    val playheadXDp = 140
    val staffHeightDp = 280
    val barCount = song.bars.size
    val totalWidthDp = barWidthDp * barCount
    val slotsTotal = barCount * song.slotsPerBar
    val clefWPx = with(density) { 32.dp.toPx() }
    val padXPx = with(density) { 12.dp.toPx() }
    val totalWidthPx = with(density) { totalWidthDp.dp.toPx() }
    val innerX0Px = clefWPx + padXPx
    val innerWPx = totalWidthPx - clefWPx - 2 * padXPx
    val pxPerSlotPx = innerWPx / slotsTotal
    val playheadXPx = with(density) { playheadXDp.dp.toPx() }
    val translationXPx = playheadXPx - innerX0Px - state.currentSlot * pxPerSlotPx

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DrumsColors.Surface)
            .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        DrumStaff(
            bars = song.bars,
            currentSlot = 0f,
            showClef = true,
            showPlayhead = false,
            timeSig = song.timeSig,
            widthDp = totalWidthDp,
            heightDp = staffHeightDp,
            modifier = Modifier.graphicsLayer { translationX = translationXPx },
        )
        Box(
            modifier = Modifier
                .offset(x = (playheadXDp - 1).dp)
                .fillMaxHeight()
                .width(2.5.dp)
                .background(DrumsColors.Playhead),
        )
    }
}

@Composable
private fun YouTubePanel(
    phase: PlayerPhase,
    onAdapterReady: (ph.nextbank.drums.audio.playback.YouTubeAdapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val videoId = when (phase) {
        is PlayerPhase.YouTubeBuffering, is PlayerPhase.YouTubeReady -> {
            (phase as? PlayerPhase.YouTubeReady)?.videoId
                ?: (phase as? PlayerPhase.YouTubeBuffering)?.let {
                    // YouTubeBuffering means the song has a cached videoId — read from state.song?
                    // Actually we capture it inside the parent. Cleaner approach: pass videoId explicitly.
                    null
                }
        }
        else -> null
    }
    if (videoId == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DrumsColors.Surface2)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                is PlayerPhase.Searching -> Text("Searching YouTube…", color = DrumsColors.Dim, style = DrumsType.caption)
                is PlayerPhase.SynthFallback -> Text("Synth mode", color = DrumsColors.Dim, style = DrumsType.caption)
                else -> CircularProgressIndicator(color = DrumsColors.Accent)
            }
        }
        return
    }
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        YouTubeEmbed(
            videoId = videoId,
            modifier = Modifier.fillMaxSize(),
            onAdapterReady = onAdapterReady,
        )
    }
}

@Composable
private fun BottomRow(state: PlayerUiState, vm: PlayerViewModel) {
    val isPlaying = state.playbackState == PlaybackState.Playing
    val canPlay = state.playbackState in setOf(
        PlaybackState.Ready, PlaybackState.Playing,
        PlaybackState.Paused, PlaybackState.Finished,
    )
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            icon = rememberVectorPainter(Icons.Outlined.MusicNote),
            contentDescription = "Metronome",
            onClick = vm::toggleMetronome,
            style = if (state.metronomeOn) TransportStyle.PRIMARY else TransportStyle.GHOST,
            sizeDp = 42.dp,
        )
        TransportButton(
            icon = rememberVectorPainter(Icons.Filled.Stop),
            contentDescription = "Stop",
            onClick = vm::stop,
            sizeDp = 42.dp,
        )
        TransportButton(
            icon = rememberVectorPainter(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow),
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = { if (canPlay) vm.togglePlay() },
            style = TransportStyle.PRIMARY,
            sizeDp = 64.dp,
        )
        TransportButton(
            icon = rememberVectorPainter(Icons.Outlined.Loop),
            contentDescription = if (state.looping) "Stop looping" else "Loop song",
            onClick = vm::toggleLoop,
            style = if (state.looping) TransportStyle.PRIMARY else TransportStyle.GHOST,
            sizeDp = 42.dp,
        )
        // Sync nudge
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SyncButton("−", onClick = { vm.nudgeOffset(-50) })
            Text("${state.youtubeOffsetMs}ms", color = DrumsColors.Dim, style = DrumsType.caption)
            SyncButton("+", onClick = { vm.nudgeOffset(50) })
        }
    }
}

@Composable
private fun SyncButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .border(1.dp, DrumsColors.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = DrumsColors.Text, style = DrumsType.caption) }
}

private fun android.content.Context.findActivity(): Activity? {
    var c: android.content.Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun IconBox(icon: ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(1.dp, DrumsColors.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = cd, tint = DrumsColors.Text) }
}
```

> One known issue: `YouTubePanel` needs the videoId from `PlayerPhase.YouTubeBuffering` even though that phase doesn't carry one. Fix in Step 2.

- [ ] **Step 2: Add `videoId` to `YouTubeBuffering`**

In `PlayerViewModel.kt`, change `PlayerPhase.YouTubeBuffering` from a `data object` to a `data class`:

```kotlin
    data class YouTubeBuffering(val videoId: String) : PlayerPhase
```

Update every place in `PlayerViewModel.kt` that constructed it as `PlayerPhase.YouTubeBuffering`:

```kotlin
            if (s.youtubeVideoId != null) {
                _state.value = _state.value.copy(phase = PlayerPhase.YouTubeBuffering(s.youtubeVideoId))
            } ...
```

```kotlin
            _state.value = cur.copy(
                phase = PlayerPhase.YouTubeBuffering(candidate.videoId),
                ...
            )
```

```kotlin
        if (_state.value.phase !is PlayerPhase.YouTubeBuffering) return
```

```kotlin
                if (st == PlaybackState.Ready &&
                    _state.value.phase is PlayerPhase.YouTubeBuffering
                ) {
                    val videoId = (_state.value.phase as PlayerPhase.YouTubeBuffering).videoId
                    _state.value = _state.value.copy(phase = PlayerPhase.YouTubeReady(videoId))
                }
```

In `PlayerScreen.kt`'s `YouTubePanel`, simplify:

```kotlin
    val videoId = when (phase) {
        is PlayerPhase.YouTubeReady -> phase.videoId
        is PlayerPhase.YouTubeBuffering -> phase.videoId
        else -> null
    }
```

In `PlayerViewModelTest.kt`, update the assertion that expects `PlayerPhase.YouTubeBuffering`:

```kotlin
        assertTrue(vm.state.first().phase is PlayerPhase.YouTubeBuffering)
        assertEquals("abc12345678", (vm.state.first().phase as PlayerPhase.YouTubeBuffering).videoId)
```

(There are two such tests — the `acceptCandidate` test and the `song with cached video` test.)

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Re-run unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/player/PlayerScreen.kt \
        app/src/main/java/ph/nextbank/drums/ui/player/PlayerViewModel.kt \
        app/src/main/java/ph/nextbank/drums/data/db/SongDao.kt \
        app/src/main/java/ph/nextbank/drums/data/repo/SongRepository.kt \
        app/src/main/java/ph/nextbank/drums/audio/DrumSampleBank.kt \
        app/src/main/java/ph/nextbank/drums/audio/playback/SyntheticPlaybackSource.kt \
        app/src/main/java/ph/nextbank/drums/di/AppModule.kt \
        app/src/test/java/ph/nextbank/drums/audio/FakeDrumSampleBank.kt \
        app/src/test/java/ph/nextbank/drums/data/repo/FakeSongRepository.kt \
        app/src/test/java/ph/nextbank/drums/ui/player/PlayerViewModelTest.kt \
        app/src/androidTest/java/ph/nextbank/drums/data/SongRepositoryTest.kt
git commit -m "Rewire PlayerScreen + ViewModel around PlaybackSource state machine"
```

---

### Task 16: Confirmation dialog Composable

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/player/YouTubeConfirmDialog.kt`

- [ ] **Step 1: Write the dialog**

```kotlin
package ph.nextbank.drums.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import ph.nextbank.drums.audio.youtube.SearchResult
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun YouTubeConfirmDialog(
    candidate: SearchResult,
    onUseThis: () -> Unit,
    onTryAnother: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Found a video",
                color = DrumsColors.Dim,
                style = DrumsType.allCapsLabel,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = candidate.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DrumsColors.Surface2),
                )
                Column(Modifier.weight(1f)) {
                    Text(candidate.title, color = DrumsColors.Text, style = DrumsType.cardTitle, maxLines = 2)
                    Text(
                        "${candidate.channelTitle} · ${formatDuration(candidate.durationSec)}",
                        color = DrumsColors.Dim,
                        style = DrumsType.caption,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DialogButton("Try another", DrumsColors.Surface2, DrumsColors.Text, onTryAnother)
                DialogButton("Use this video", DrumsColors.Accent, androidx.compose.ui.graphics.Color.White, onUseThis)
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = DrumsType.cardTitle)
    }
}

private fun formatDuration(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
```

- [ ] **Step 2: Add Coil for image loading**

`AsyncImage` comes from Coil. Add the dep.

In `libs.versions.toml` `[versions]`:

```toml
coil = "2.7.0"
```

In `[libraries]`:

```toml
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
```

In `app/build.gradle.kts` under `dependencies`:

```kotlin
    implementation(libs.coil.compose)
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/player/YouTubeConfirmDialog.kt \
        gradle/libs.versions.toml \
        app/build.gradle.kts
git commit -m "Add YouTube confirmation dialog + Coil for thumbnails"
```

---

## Milestone 7 — Verification (Task 17)

### Task 17: Build, install, manual smoke test

**Files:** none (verification)

- [ ] **Step 1: Clean build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Inspect any warnings.

- [ ] **Step 2: Install on emulator or device**

Use Android Studio's Run button, or:

```bash
./gradlew :app:installDebug
```

- [ ] **Step 3: Walk through the verification checklist**

For each of the 5 sample songs (Smells Like Teen Spirit, Tom Sawyer, Rosanna, In the Air Tonight, YYZ):

  1. Open the Library — verify the pill row reads `All 5 · Recent · Bundled` (no Spotify pill).
  2. Open the Upload screen via "+" — verify the "Connect Spotify" row is gone.
  3. Tap a song.
  4. Verify "Searching YouTube…" appears in the upper-right embed area.
  5. Confirmation dialog appears with a video thumbnail + title + channel + duration.
  6. Tap "Use this video".
  7. The video loads (spinner → embed visible).
  8. Tap play. Both YouTube audio and staff cursor advance in sync.
  9. Pause + resume — both stop and resume together.
  10. Stop — both reset to 0.
  11. Adjust sync nudge — `–50ms` shifts the cursor visibly later relative to the audio; `+50ms` shifts it earlier.
  12. Exit and re-enter the song — it should skip the search, go straight to `YouTubeBuffering → YouTubeReady` with the cached video.
  13. From the three-dots menu, tap "Try another video" — confirmation dialog reappears with a different result; original ID is in the blocklist (verify by re-entering — search excludes the rejected ID).

- [ ] **Step 4: Failure-mode checks**

  1. Enable airplane mode. Open a fresh-install song. Expected: toast "No YouTube result — playing synth drums"; song plays with synth audio + staff cursor (Phase 1 behavior). "SYNTH" pill visible in the header.
  2. Disable airplane mode, re-enter the song. Expected: search runs, confirmation appears.
  3. With a song already cached, edit the row in `drums.db` to set `youtubeVideoId` to a known-invalid 11-char string (e.g., via `adb shell run-as ph.nextbank.drums sqlite3`). Re-enter song. Expected: toast about YouTube failure, fall back to synth.

- [ ] **Step 5: Commit verification notes**

If you made any small fixups during verification, commit them. Otherwise this task is just a checkpoint and produces no commit.

---

## Definition of done

- All 5 sample songs play with YouTube audio after the confirmation dialog flow.
- Staff cursor stays in sync with YouTube playback within ±1 sixteenth note over 60 seconds (after a one-time nudge if needed).
- "Try another video" successfully cycles to the next result.
- Airplane mode / pulled video / forced YouTube error each yield a clean fall-back to synth playback with a toast, no crash.
- All Phase 1 unit tests still pass; all Phase 2 unit tests (`YouTubePlaybackSourceTest`, `PlayerViewModelTest`) pass.
- DB migration v2 → v3 verified by upgrading a Phase-1 install (or by clearing app data on a Phase-1 install — both paths land at the new schema).
- Spotify pill, "Connect Spotify" row, and `ImportSource.SPOTIFY` are gone.
