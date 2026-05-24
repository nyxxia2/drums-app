# Drums App — Phase 3 Implementation Plan: Songsterr-sourced Tabs

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `SampleSongs.kt` with a user-driven flow — type a song name, the app searches Songsterr, fetches the community drum tab, persists it, and uses the existing Phase 2 YouTube auto-search to find audio.

**Architecture:** Three new files under `audio/songsterr/` — search (documented API), fetcher (scraping, endpoint discovered in M2), parser (pure-function JSON-to-bars). One new UI screen replaces `UploadScreen`. Drop the bundled-songs seed. DB migration v4 adds `songsterrId` + `songsterrRevisionId` to `Song`.

**Tech Stack:**
- Kotlin + Jetpack Compose + Hilt + Room + OkHttp (all existing)
- `kotlinx.serialization-json` (new) — parsing Songsterr's JSON; cleaner than `org.json` and fixtures are well-typed
- `okhttp-mockwebserver` (new) — for `SongsterrSearchServiceTest` / `SongsterrTabFetcherTest`

---

## File structure

**Created:**
- `app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrSearchService.kt` — interface + `SongsterrResult`, `SongsterrTrack` data classes
- `app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrSearchService.kt` — production impl
- `app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrTabFetcher.kt` — interface + `RevisionJson` data classes + sealed `FetchResult`
- `app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrTabFetcher.kt` — production impl (uses URL pattern discovered in Task 8)
- `app/src/main/java/ph/nextbank/drums/audio/songsterr/DrumTabParser.kt` — pure function + sealed `ParseResult`
- `app/src/main/java/ph/nextbank/drums/audio/songsterr/MidiPercussionMap.kt` — MIDI number → DrumToken table
- `app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt` — search input + results list
- `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt` — orchestrates search → fetch → parse → persist
- `app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrSearchServiceTest.kt`
- `app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrTabFetcherTest.kt`
- `app/src/test/java/ph/nextbank/drums/audio/songsterr/DrumTabParserTest.kt`
- `app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrSearchService.kt`
- `app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrTabFetcher.kt`
- `app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt`
- `app/src/test/resources/songsterr/search-in-the-air-tonight.json` — captured search response fixture
- `app/src/test/resources/songsterr/revision-sample.json` — captured track-data response fixture (saved during Task 8)
- `docs/superpowers/notes/2026-05-23-songsterr-track-data-endpoint.md` — written during Task 8

**Modified:**
- `gradle/libs.versions.toml` — add `kotlinx-serialization-json`, `okhttp-mockwebserver`
- `app/build.gradle.kts` — add kotlinx serialization plugin + dependencies
- `app/src/main/java/ph/nextbank/drums/data/model/Song.kt` — add `songsterrId`, `songsterrRevisionId`
- `app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt` — add columns + mapper updates
- `app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt` — version → 4, `MIGRATION_3_4`
- `app/src/main/java/ph/nextbank/drums/di/AppModule.kt` — provide Songsterr services, drop seed call
- `app/src/main/java/ph/nextbank/drums/ui/nav/NavGraph.kt` — route `Route.Upload` now renders `AddSongScreen` (we keep the route name to avoid touching the call site)

**Deleted:**
- `app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt`
- `app/src/main/java/ph/nextbank/drums/ui/upload/UploadScreen.kt` (replaced by `AddSongScreen`)

---

## Verified prerequisites (already discovered)

These were probed live during plan-writing. Tasks reference them as concrete starting points:

- **Search endpoint**: `GET https://www.songsterr.com/api/songs?pattern={query}` returns JSON array (HTTP 200). Each item has `songId`, `artistId`, `artist`, `title`, `tracks[]`, `popularTrackDrum` (index into tracks or null).
- **Track schema**: each track has `instrumentId` (number — **1024 means Drums**), `instrument` (string), `name`, `views`, optional `difficulty`, and `hash` (e.g. `"drums_OX64y6oG"`).
- **Revisions endpoint**: `GET https://www.songsterr.com/api/meta/{songId}/revisions` returns array; first entry is the latest, has `revisionId`.
- **Revision metadata**: `GET https://www.songsterr.com/api/meta/{songId}/{revisionId}` returns the same `tracks[]` (with `hash`) as the search result, plus moderation info.
- **Track-data endpoint**: NOT yet discovered. The actual notes are loaded by JavaScript from a URL we'll capture in Task 8 using Playwright. All endpoints we guessed during plan-writing (S3, static3, /api/track/) returned 403/404 without the right path.

---

# Milestone 0 — Foundation (Tasks 1–3)

Set up dependencies and the new screen shell. Cheapest tasks first to surface any build-system issues.

### Task 1: Add new Gradle dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the new versions + libraries in `libs.versions.toml`**

In `[versions]`, after the existing entries, add:

```toml
kotlinx-serialization = "1.7.3"
```

In `[libraries]`, after the existing entries, add:

```toml
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
```

In `[plugins]`, after the existing entries, add:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Apply the plugin + add deps in `app/build.gradle.kts`**

In the `plugins { ... }` block, add at the end (after `alias(libs.plugins.hilt)`):

```kotlin
    alias(libs.plugins.kotlin.serialization)
```

In `dependencies { ... }`, after the existing `implementation(libs.rhino)`, add:

```kotlin
    implementation(libs.kotlinx.serialization.json)
```

After the existing `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")`, add:

```kotlin
    testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 3: Sync gradle**

```bash
export ANDROID_HOME=/home/sara/Android/Sdk JAVA_HOME=/home/sara/Downloads/android-studio-panda4-patch1-linux/android-studio/jbr
./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep -iE "serialization|mockwebserver"
```

Expected output: lines mentioning `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` and `com.squareup.okhttp3:mockwebserver:4.12.0`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "Add kotlinx-serialization + okhttp mockwebserver for Songsterr work"
```

---

### Task 2: Empty-library state for Library screen

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/library/LibraryScreen.kt`

This makes the empty library readable BEFORE we drop the bundled songs. It's a no-op on current builds (library is never empty today) but ensures the empty-state experience is good when we delete `SampleSongs.kt` in Task 18.

- [ ] **Step 1: Read the current empty-state copy**

Open `LibraryScreen.kt` and find the `emptyMessage` variable. It currently reads:

```kotlin
val emptyMessage = when (tab) {
    "Recent" -> "You haven't played any songs yet."
    "Bundled" -> "No bundled songs found."
    else -> "Tap the + button to add a song."
}
```

The "All" tab's empty copy ("Tap the + button to add a song.") is what new users will see — verify it's already user-friendly. If it isn't, update it to:

```kotlin
val emptyMessage = when (tab) {
    "Recent" -> "Songs you've played show up here."
    "Bundled" -> "Bundled songs show up here."
    else -> "Tap the + button to add a song from Songsterr."
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/library/LibraryScreen.kt
git commit -m "Refresh empty-state copy to mention Songsterr"
```

---

### Task 3: AddSongScreen skeleton

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/ui/nav/NavGraph.kt`

The screen has just the back arrow + search input + empty results area for now. Wired into the existing `Route.Upload` so the FAB in the library still navigates here.

- [ ] **Step 1: Write the screen**

Create `app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt`:

```kotlin
package ph.nextbank.drums.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun AddSongScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(DrumsColors.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, DrumsColors.Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DrumsColors.Text) }
            Spacer(Modifier.width(12.dp))
            Text("Add a song", color = DrumsColors.Text, style = DrumsType.sectionTitle)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Search Songsterr — we'll grab the drum tab and find the audio on YouTube.",
            color = DrumsColors.Dim, style = DrumsType.body,
        )

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (query.isEmpty()) {
                Text("Song or artist…", color = DrumsColors.Dim, style = DrumsType.body)
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = DrumsColors.Text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        // Results list will go here in Task 7.
    }
}
```

- [ ] **Step 2: Swap the route to use the new screen**

In `NavGraph.kt`, replace:

```kotlin
import ph.nextbank.drums.ui.upload.UploadScreen
```

with:

```kotlin
import ph.nextbank.drums.ui.add.AddSongScreen
```

And replace:

```kotlin
composable(Route.Upload.path) {
    UploadScreen(onBack = { nav.popBackStack() })
}
```

with:

```kotlin
composable(Route.Upload.path) {
    AddSongScreen(onBack = { nav.popBackStack() })
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt \
        app/src/main/java/ph/nextbank/drums/ui/nav/NavGraph.kt
git commit -m "Add AddSongScreen skeleton + route from FAB"
```

---

# Milestone 1 — Songsterr Search (Tasks 4–7)

End-to-end: typing into the search box returns real Songsterr results. Tapping a result is a no-op so far.

### Task 4: SongsterrResult data classes + service interface

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrSearchService.kt`

The shape comes from the verified `/api/songs` JSON probed during plan-writing.

- [ ] **Step 1: Write the interface**

Create `SongsterrSearchService.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface SongsterrSearchService {
    /**
     * Returns up to ~20 Songsterr songs matching [query] across all tabs.
     * Each result may include zero, one, or many drum tracks — drum extraction
     * is the caller's job.
     *
     * Returns an empty list on network failure (caller's UI handles the
     * "nothing found" / "search failed" UX as one path).
     */
    suspend fun search(query: String): List<SongsterrResult>
}

@Serializable
data class SongsterrResult(
    val songId: Long,
    val artistId: Long,
    val artist: String,
    val title: String,
    val tracks: List<SongsterrTrack> = emptyList(),
    /** Index into [tracks] of the most-popular drum track, or null if no drums. */
    val popularTrackDrum: Int? = null,
)

@Serializable
data class SongsterrTrack(
    val instrumentId: Int,
    val instrument: String,
    val name: String,
    val views: Int = 0,
    val difficulty: Int? = null,
    val hash: String,
) {
    /** Songsterr's instrumentId 1024 means "Drums" — verified against the live API. */
    val isDrums: Boolean get() = instrumentId == 1024
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrSearchService.kt
git commit -m "Add SongsterrSearchService interface + result types"
```

---

### Task 5: Test fixture for search response

**Files:**
- Create: `app/src/test/resources/songsterr/search-in-the-air-tonight.json`

This is a real captured Songsterr response that the search-service test will replay.

- [ ] **Step 1: Capture the fixture**

```bash
mkdir -p app/src/test/resources/songsterr
curl -sS -H "User-Agent: Mozilla/5.0" \
  "https://www.songsterr.com/api/songs?pattern=in+the+air+tonight" \
  > app/src/test/resources/songsterr/search-in-the-air-tonight.json
```

- [ ] **Step 2: Verify it parses**

```bash
python3 -c "import json; d=json.load(open('app/src/test/resources/songsterr/search-in-the-air-tonight.json')); print(len(d), 'results;', sum(1 for r in d for t in r.get('tracks',[]) if t['instrumentId']==1024), 'drum tracks total')"
```

Expected: prints two numbers, first ≥ 1, second ≥ 1 (the Phil Collins entry has at least 2 drum tracks).

- [ ] **Step 3: Commit**

```bash
git add app/src/test/resources/songsterr/search-in-the-air-tonight.json
git commit -m "Add Songsterr search response fixture for IATA"
```

---

### Task 6: OkHttpSongsterrSearchService + tests (TDD)

**Files:**
- Create: `app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrSearchServiceTest.kt`
- Create: `app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrSearchService.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrSearchService.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrSearchServiceTest.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class OkHttpSongsterrSearchServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var svc: OkHttpSongsterrSearchService

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        svc = OkHttpSongsterrSearchService(
            client = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After fun tearDown() { server.shutdown() }

    private fun fixture(name: String): String =
        File("src/test/resources/songsterr/$name").readText()

    @Test fun `parses real-world IATA fixture into typed results`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("search-in-the-air-tonight.json")))
        val results = svc.search("in the air tonight")
        assertTrue(results.isNotEmpty())
        val phil = results.first { it.artist == "Phil Collins" && it.title == "In The Air Tonight" }
        // The fixture's IATA entry has popularTrackDrum pointing into a track with instrumentId 1024.
        assertNotNull(phil.popularTrackDrum)
        val drum = phil.tracks[phil.popularTrackDrum!!]
        assertEquals(1024, drum.instrumentId)
        assertTrue(drum.hash.startsWith("drums_"))
    }

    @Test fun `encodes spaces as plus signs in query`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        svc.search("in the air tonight")
        val req = server.takeRequest()
        // OkHttp's HttpUrl encodes spaces as %20 by default, but Songsterr accepts both.
        // Either is acceptable as long as the query is faithful.
        assertTrue(
            "query encoding malformed: ${req.path}",
            req.path?.contains("pattern=in%20the%20air%20tonight") == true ||
                req.path?.contains("pattern=in+the+air+tonight") == true,
        )
    }

    @Test fun `returns empty list on HTTP error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val results = svc.search("anything")
        assertEquals(emptyList<SongsterrResult>(), results)
    }

    @Test fun `returns empty list on network failure`() = runTest {
        server.shutdown()  // forces connection refused
        val results = svc.search("anything")
        assertEquals(emptyList<SongsterrResult>(), results)
    }

    @Test fun `unknown fields in response don't crash parser`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"songId":1,"artistId":1,"artist":"A","title":"B","tracks":[],"someNewField":"ignored"}]""",
            ),
        )
        val results = svc.search("x")
        assertEquals(1, results.size)
        assertEquals("A", results[0].artist)
    }
}
```

- [ ] **Step 2: Run the test — expect failure (class missing)**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.audio.songsterr.OkHttpSongsterrSearchServiceTest"
```

Expected: COMPILATION FAILED — `OkHttpSongsterrSearchService` not found.

- [ ] **Step 3: Implement the service**

Create `app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrSearchService.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "DrumsSgs"
private const val DEFAULT_BASE_URL = "https://www.songsterr.com"

class OkHttpSongsterrSearchService(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : SongsterrSearchService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): List<SongsterrResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$baseUrl/api/songs?pattern=".toHttpUrlOrNull()!!
                    .newBuilder()
                    .removeAllQueryParameters("pattern")
                    .addQueryParameter("pattern", query)
                    .build()
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "DrumsApp/0.1 (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "search '$query' returned HTTP ${resp.code}")
                        return@withContext emptyList()
                    }
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    json.decodeFromString<List<SongsterrResult>>(body)
                }
            }.onFailure { Log.w(TAG, "search '$query' failed", it) }
                .getOrElse { emptyList() }
        }
}
```

- [ ] **Step 4: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.audio.songsterr.OkHttpSongsterrSearchServiceTest"
```

Expected: 5 tests pass.

- [ ] **Step 5: Add the fake for downstream tests**

Create `app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrSearchService.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

class FakeSongsterrSearchService : SongsterrSearchService {
    var results: List<SongsterrResult> = emptyList()
    var lastQuery: String? = null
    var shouldThrow: Throwable? = null

    override suspend fun search(query: String): List<SongsterrResult> {
        lastQuery = query
        shouldThrow?.let { throw it }
        return results
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrSearchService.kt \
        app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrSearchServiceTest.kt \
        app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrSearchService.kt
git commit -m "Implement OkHttp-based SongsterrSearchService + fake"
```

---

### Task 7: Wire Songsterr search into AddSongScreen + Hilt

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/di/AppModule.kt`
- Create: `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt`

- [ ] **Step 1: Provide the service from Hilt**

In `AppModule.kt`, add this import at the top (alongside the other imports):

```kotlin
import ph.nextbank.drums.audio.songsterr.OkHttpSongsterrSearchService
import ph.nextbank.drums.audio.songsterr.SongsterrSearchService
```

In the object body, after `provideYouTubeSearchService`, add:

```kotlin
    @Provides @Singleton
    fun provideSongsterrSearchService(): SongsterrSearchService = OkHttpSongsterrSearchService()
```

- [ ] **Step 2: Create the AddSongViewModel**

Create `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt`:

```kotlin
package ph.nextbank.drums.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.songsterr.SongsterrResult
import ph.nextbank.drums.audio.songsterr.SongsterrSearchService
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 400L

data class AddSongUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SongsterrResult> = emptyList(),
)

@HiltViewModel
class AddSongViewModel @Inject constructor(
    private val searchService: SongsterrSearchService,
) : ViewModel() {

    private val _state = MutableStateFlow(AddSongUiState())
    val state: StateFlow<AddSongUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.value = _state.value.copy(isSearching = false, results = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.value = _state.value.copy(isSearching = true)
            val res = searchService.search(q).take(10)
            _state.value = _state.value.copy(isSearching = false, results = res)
        }
    }

    /** Stub — wired up in Task 14. */
    fun onResultClicked(result: SongsterrResult) { /* TODO Task 14 */ }
}
```

- [ ] **Step 3: Wire the ViewModel + results list into AddSongScreen**

Replace the entire contents of `AddSongScreen.kt` with:

```kotlin
package ph.nextbank.drums.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.audio.songsterr.SongsterrResult
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun AddSongScreen(
    onBack: () -> Unit,
    vm: AddSongViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(DrumsColors.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, DrumsColors.Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DrumsColors.Text) }
            Spacer(Modifier.width(12.dp))
            Text("Add a song", color = DrumsColors.Text, style = DrumsType.sectionTitle)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Search Songsterr — we'll grab the drum tab and find the audio on YouTube.",
            color = DrumsColors.Dim, style = DrumsType.body,
        )

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (state.query.isEmpty()) {
                Text("Song or artist…", color = DrumsColors.Dim, style = DrumsType.body)
            }
            BasicTextField(
                value = state.query,
                onValueChange = vm::onQueryChanged,
                singleLine = true,
                textStyle = TextStyle(color = DrumsColors.Text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        if (state.isSearching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DrumsColors.Accent)
                Spacer(Modifier.width(8.dp))
                Text("Searching…", color = DrumsColors.Dim, style = DrumsType.caption)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.results, key = { it.songId }) { result ->
                    SongsterrResultRow(result) { vm.onResultClicked(result) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SongsterrResultRow(result: SongsterrResult, onClick: () -> Unit) {
    val hasDrums = result.popularTrackDrum != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DrumsColors.Surface)
            .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
            .clickable(enabled = hasDrums, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(result.title, color = DrumsColors.Text, style = DrumsType.cardTitle)
            Text(
                if (hasDrums) result.artist else "${result.artist} · no drum tab",
                color = if (hasDrums) DrumsColors.Dim else DrumsColors.Line,
                style = DrumsType.caption,
            )
        }
        if (hasDrums) {
            Text("›", color = DrumsColors.Dim, style = DrumsType.cardTitle)
        }
    }
}
```

- [ ] **Step 4: Delete the old UploadScreen**

```bash
rm app/src/main/java/ph/nextbank/drums/ui/upload/UploadScreen.kt
```

- [ ] **Step 5: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Install + manual smoke test**

```bash
./gradlew :app:installDebug
$ANDROID_HOME/platform-tools/adb shell am start -n ph.nextbank.drums/.MainActivity
```

In the app: tap the `+` FAB → AddSongScreen opens → type "in the air tonight" → after ~0.5s you should see a list of results including "In The Air Tonight" by Phil Collins. Songs without a drum tab show "no drum tab" in dim text. Tapping does nothing yet — that's fine.

If results don't appear, check `adb logcat | grep DrumsSgs` for the OkHttp error.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/di/AppModule.kt \
        app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt \
        app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt
git rm app/src/main/java/ph/nextbank/drums/ui/upload/UploadScreen.kt
git commit -m "Wire Songsterr search into AddSongScreen; replace UploadScreen"
```

---

# Milestone 2 — Tab fetcher (Tasks 8–13)

The hard part: discover the track-data endpoint, capture a fixture, then build the fetcher + parser. Each piece is testable in isolation.

### Task 8: Discover the track-data endpoint via Playwright

**Files:**
- Create: `docs/superpowers/notes/2026-05-23-songsterr-track-data-endpoint.md`
- Create: `app/src/test/resources/songsterr/revision-sample.json`

The Songsterr player loads track data via JavaScript after the page renders. None of the path guesses during plan-writing landed (`/api/track/*`, `/static/track/*`, S3 direct, etc — all 403/404). The only reliable way to learn the real URL is to render the player in a browser and capture network requests. We use the Playwright MCP for this.

- [ ] **Step 1: Launch the Songsterr player in Playwright**

In Claude Code, run:

```
mcp__plugin_playwright_playwright__browser_navigate
  url: https://www.songsterr.com/a/wsa/phil-collins-in-the-air-tonight-tab-s50420t6
```

Wait ~5 seconds for the player to fully load (it pulls JS + then fetches track data). Then:

```
mcp__plugin_playwright_playwright__browser_wait_for
  time: 6
```

- [ ] **Step 2: Capture network requests**

```
mcp__plugin_playwright_playwright__browser_network_requests
```

Scan the output for requests whose URL contains the track hash. Look for the request body that comes back with the actual note/beat data (it'll be the largest non-static request, probably JSON, hit AFTER the page HTML loads). Note its full URL pattern, HTTP method, any request headers that look mandatory (Cookie, Referer, etc.).

- [ ] **Step 3: Save the response body**

Re-fetch the discovered URL with curl to verify it works from outside the browser context:

```bash
curl -sS -H "User-Agent: Mozilla/5.0" -H "Referer: https://www.songsterr.com/" \
  "<DISCOVERED_URL_HERE>" \
  -o app/src/test/resources/songsterr/revision-sample.json -w "HTTP %{http_code} | %{size_download} bytes\n"
```

If curl gets a different response than the browser (auth/cookies needed), document that — the OkHttp fetcher in Task 10 will need to mimic the browser's headers.

Quick sanity check the JSON parses:

```bash
python3 -c "import json; d=json.load(open('app/src/test/resources/songsterr/revision-sample.json')); print(type(d).__name__, 'top-level keys:', list(d.keys())[:10] if isinstance(d, dict) else len(d), 'items')"
```

- [ ] **Step 4: Write the discovery note**

Create `docs/superpowers/notes/2026-05-23-songsterr-track-data-endpoint.md`:

```markdown
# Songsterr track-data endpoint (discovered 2026-05-23)

**URL pattern**: `<EXACT URL TEMPLATE with {songId}/{revisionId}/{trackHash} placeholders>`

**Method**: `<GET / POST>`

**Required headers** (anything beyond plain User-Agent):
- `<header name>: <value>`

**Response shape** (top-level fields only; full example in `app/src/test/resources/songsterr/revision-sample.json`):
- `<field>`: `<type>` — `<one-line meaning>`

**Where to find each piece needed by `DrumTabParser`**:
- BPM: `<JSON path>`
- Time signature numerator: `<JSON path>`
- Time signature denominator: `<JSON path>`
- Bars list: `<JSON path>`
- Per-bar beats: `<JSON path>`
- Per-beat notes (MIDI percussion numbers + duration): `<JSON path>`
- Triplet/tuplet indicators (so we know to use 24 slots/bar): `<JSON path>` (or "none — infer from durations")

**Stability notes**:
- `<anything Songsterr is likely to change soon>`
- `<auth/rate-limit behaviour>`
```

Fill in every angle-bracket placeholder with concrete values from the actual response. **Do not commit with placeholders remaining.**

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/notes/2026-05-23-songsterr-track-data-endpoint.md \
        app/src/test/resources/songsterr/revision-sample.json
git commit -m "Discover Songsterr track-data endpoint + capture fixture"
```

---

### Task 9: SongsterrTabFetcher interface + RevisionJson types

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrTabFetcher.kt`

The exact field structure comes from the discovery note written in Task 8. This task only nails down the OUTER shape — what callers see — and one named placeholder for the inner JSON tree, which Task 11's parser will navigate.

- [ ] **Step 1: Write the interface + sealed result type**

Create `SongsterrTabFetcher.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import kotlinx.serialization.json.JsonObject

interface SongsterrTabFetcher {
    /**
     * Fetch raw track-data JSON for the drum track of [songId].
     *
     * The fetcher picks the most-popular drum track using the metadata from
     * [SongsterrSearchService] / the song's revision metadata. The returned
     * [RevisionJson] is opaque — only [DrumTabParser] knows its inner shape.
     */
    suspend fun fetchDrumTrack(songId: Long): FetchResult
}

/** Opaque container — DrumTabParser navigates the inner JSON tree. */
data class RevisionJson(
    val songId: Long,
    val revisionId: Long,
    val drumTrackHash: String,
    val root: JsonObject,
)

sealed interface FetchResult {
    data class Success(val data: RevisionJson) : FetchResult
    data object NoDrumTrack : FetchResult
    data class ScrapeFailure(val reason: String) : FetchResult
    data object NetworkError : FetchResult
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/songsterr/SongsterrTabFetcher.kt
git commit -m "Add SongsterrTabFetcher interface + FetchResult sealed type"
```

---

### Task 10: OkHttpSongsterrTabFetcher + tests

**Files:**
- Create: `app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrTabFetcherTest.kt`
- Create: `app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrTabFetcher.kt`
- Create: `app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrTabFetcher.kt`

This builds on the discovery from Task 8. The flow is:
1. Fetch `/api/meta/{songId}/revisions` → take first entry's `revisionId`
2. Fetch `/api/meta/{songId}/{revisionId}` → find the most-popular drum track (`popularTrackDrum` index)
3. Fetch the track data from the URL pattern documented in Task 8

If step 2 finds no drum track, return `NoDrumTrack` without doing step 3.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrTabFetcherTest.kt`:

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

class OkHttpSongsterrTabFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var fetcher: OkHttpSongsterrTabFetcher

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = OkHttpSongsterrTabFetcher(
            client = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After fun tearDown() { server.shutdown() }

    private fun fixture(name: String): String =
        File("src/test/resources/songsterr/$name").readText()

    @Test fun `happy path returns Success with parsed RevisionJson`() = runTest {
        // Step 1: revisions endpoint
        server.enqueue(MockResponse().setBody("""[{"songId":50420,"revisionId":5548296}]"""))
        // Step 2: revision metadata — must contain at least one instrumentId=1024 track and popularTrackDrum
        server.enqueue(
            MockResponse().setBody(
                """{"songId":50420,"revisionId":5548296,"tracks":[
                    {"instrumentId":29,"instrument":"Guitar","name":"G","hash":"guitar_AAA"},
                    {"instrumentId":1024,"instrument":"Drums","name":"D","hash":"drums_BBB"}
                ],"popularTrackDrum":1}""",
            ),
        )
        // Step 3: track data — use the real fixture so the JSON tree looks like production
        server.enqueue(MockResponse().setBody(fixture("revision-sample.json")))

        val result = fetcher.fetchDrumTrack(50420L)
        assertTrue("expected Success, got $result", result is FetchResult.Success)
        val data = (result as FetchResult.Success).data
        assertEquals(50420L, data.songId)
        assertEquals(5548296L, data.revisionId)
        assertEquals("drums_BBB", data.drumTrackHash)
    }

    @Test fun `returns NoDrumTrack when revision has no drum tracks`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"songId":1,"revisionId":2}]"""))
        server.enqueue(
            MockResponse().setBody(
                """{"songId":1,"revisionId":2,"tracks":[
                    {"instrumentId":29,"instrument":"Guitar","name":"G","hash":"guitar_X"}
                ]}""",
            ),
        )
        val result = fetcher.fetchDrumTrack(1L)
        assertEquals(FetchResult.NoDrumTrack, result)
    }

    @Test fun `returns ScrapeFailure when revisions list is empty`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        val result = fetcher.fetchDrumTrack(1L)
        assertTrue("expected ScrapeFailure, got $result", result is FetchResult.ScrapeFailure)
    }

    @Test fun `returns ScrapeFailure when revisions endpoint returns 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = fetcher.fetchDrumTrack(1L)
        assertTrue("expected ScrapeFailure, got $result", result is FetchResult.ScrapeFailure)
    }

    @Test fun `returns NetworkError on connection failure`() = runTest {
        server.shutdown()  // forces connection refused
        val result = fetcher.fetchDrumTrack(1L)
        assertEquals(FetchResult.NetworkError, result)
    }
}
```

- [ ] **Step 2: Run the test — expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.audio.songsterr.OkHttpSongsterrTabFetcherTest"
```

Expected: COMPILATION FAILED — `OkHttpSongsterrTabFetcher` not found.

- [ ] **Step 3: Implement the fetcher**

Create `app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrTabFetcher.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG = "DrumsSgs"
private const val DEFAULT_BASE_URL = "https://www.songsterr.com"

class OkHttpSongsterrTabFetcher(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : SongsterrTabFetcher {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchDrumTrack(songId: Long): FetchResult =
        withContext(Dispatchers.IO) {
            // Step 1: revisions list
            val revisionId: Long = runCatching {
                val body = getJsonOrNull("$baseUrl/api/meta/$songId/revisions") ?: return@withContext FetchResult.NetworkError
                val arr = json.parseToJsonElement(body).jsonArray
                if (arr.isEmpty()) return@withContext FetchResult.ScrapeFailure("no revisions for songId=$songId")
                arr.first().jsonObject["revisionId"]!!.jsonPrimitive.content.toLong()
            }.getOrElse {
                Log.w(TAG, "revisions step failed for $songId", it)
                return@withContext if (it is IOException) FetchResult.NetworkError
                    else FetchResult.ScrapeFailure("revisions parse: ${it.message}")
            }

            // Step 2: revision metadata → find drum track hash
            val drumHash: String = runCatching {
                val body = getJsonOrNull("$baseUrl/api/meta/$songId/$revisionId") ?: return@withContext FetchResult.NetworkError
                val obj = json.parseToJsonElement(body).jsonObject
                val tracks = obj["tracks"]?.jsonArray ?: return@withContext FetchResult.ScrapeFailure("no tracks field")
                val popularDrum = obj["popularTrackDrum"]?.jsonPrimitive?.contentOrNullSafe()?.toIntOrNull()
                val chosen = if (popularDrum != null && popularDrum in tracks.indices) {
                    tracks[popularDrum].jsonObject
                } else {
                    tracks.map { it.jsonObject }
                        .filter { it["instrumentId"]?.jsonPrimitive?.int == 1024 }
                        .maxByOrNull { it["views"]?.jsonPrimitive?.int ?: 0 }
                        ?: return@withContext FetchResult.NoDrumTrack
                }
                chosen["hash"]!!.jsonPrimitive.content
            }.getOrElse {
                Log.w(TAG, "metadata step failed for $songId/$revisionId", it)
                return@withContext if (it is IOException) FetchResult.NetworkError
                    else FetchResult.ScrapeFailure("metadata parse: ${it.message}")
            }

            // Step 3: track data — URL pattern from docs/superpowers/notes/2026-05-23-songsterr-track-data-endpoint.md
            val trackBody = runCatching {
                getJsonOrNull(buildTrackDataUrl(songId, revisionId, drumHash))
            }.getOrNull() ?: return@withContext FetchResult.NetworkError

            val root = runCatching { json.parseToJsonElement(trackBody).jsonObject }.getOrElse {
                return@withContext FetchResult.ScrapeFailure("track-data parse: ${it.message}")
            }

            FetchResult.Success(RevisionJson(songId, revisionId, drumHash, root))
        }

    /**
     * Build the track-data URL. Uses the pattern discovered in Task 8 and
     * documented in docs/superpowers/notes/2026-05-23-songsterr-track-data-endpoint.md.
     *
     * The implementer fills this in from the discovery note. Example shape
     * (replace with the actual one):
     *   "$baseUrl/api/songs/$songId/revisions/$revisionId/tracks/$drumHash"
     */
    private fun buildTrackDataUrl(songId: Long, revisionId: Long, drumHash: String): String =
        TODO("Fill in from the discovery note before this code compiles")

    private fun getJsonOrNull(url: String): String? {
        val req = Request.Builder()
            .url(url.toHttpUrlOrNull()!!)
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

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()

    private val kotlinx.serialization.json.JsonPrimitive.int: Int
        get() = content.toInt()
}
```

**Read the note from Task 8** and replace the `TODO("Fill in from the discovery note…")` body with the actual URL template — e.g. `return "$baseUrl/api/something/$songId/$revisionId/$drumHash"`. If extra headers are required, add them to `getJsonOrNull`.

- [ ] **Step 4: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.audio.songsterr.OkHttpSongsterrTabFetcherTest"
```

Expected: 5 tests pass. If `buildTrackDataUrl` still has the `TODO` body, those tests calling step 3 will fail with `kotlin.NotImplementedError` — fix the URL first.

- [ ] **Step 5: Add the fake**

Create `app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrTabFetcher.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

class FakeSongsterrTabFetcher : SongsterrTabFetcher {
    var result: FetchResult = FetchResult.NoDrumTrack
    var lastSongId: Long? = null

    override suspend fun fetchDrumTrack(songId: Long): FetchResult {
        lastSongId = songId
        return result
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrTabFetcher.kt \
        app/src/test/java/ph/nextbank/drums/audio/songsterr/OkHttpSongsterrTabFetcherTest.kt \
        app/src/test/java/ph/nextbank/drums/audio/songsterr/FakeSongsterrTabFetcher.kt
git commit -m "Implement OkHttp-based SongsterrTabFetcher + tests"
```

---

### Task 11: MidiPercussionMap

**Files:**
- Create: `app/src/main/java/ph/nextbank/drums/audio/songsterr/MidiPercussionMap.kt`

Static lookup table mapping MIDI percussion numbers (General MIDI) to our `DrumToken` enum. Exact mapping comes from the spec's "Drum-note mapping" table.

- [ ] **Step 1: Write the map**

Create `MidiPercussionMap.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import ph.nextbank.drums.data.model.DrumToken

/**
 * Maps MIDI percussion numbers (General MIDI standard) to our DrumToken enum.
 * Numbers not in this map are dropped during parsing — the parser logs them
 * at debug level so we know what we're losing.
 *
 * Source of truth: docs/superpowers/specs/2026-05-23-songsterr-tab-source-design.md
 */
object MidiPercussionMap {
    private val table: Map<Int, DrumToken> = mapOf(
        35 to DrumToken.KICK,         // Acoustic bass drum
        36 to DrumToken.KICK,         // Bass drum 1
        38 to DrumToken.SNARE,        // Acoustic snare
        40 to DrumToken.SNARE,        // Electric snare
        42 to DrumToken.HIHAT_CLOSED, // Closed hi-hat
        44 to DrumToken.HIHAT_CLOSED, // Pedal hi-hat
        46 to DrumToken.HIHAT_OPEN,   // Open hi-hat
        49 to DrumToken.CRASH,        // Crash 1
        57 to DrumToken.CRASH,        // Crash 2
        51 to DrumToken.RIDE,         // Ride 1
        53 to DrumToken.RIDE,         // Ride bell
        59 to DrumToken.RIDE,         // Ride 2
        48 to DrumToken.TOM_HI,       // Hi mid tom
        50 to DrumToken.TOM_HI,       // High tom
        45 to DrumToken.TOM_MID,      // Low tom
        47 to DrumToken.TOM_MID,      // Low-mid tom
        41 to DrumToken.TOM_FLOOR,    // Low floor tom
        43 to DrumToken.TOM_FLOOR,    // High floor tom
    )

    fun toToken(midi: Int): DrumToken? = table[midi]
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/songsterr/MidiPercussionMap.kt
git commit -m "Add MIDI percussion → DrumToken mapping table"
```

---

### Task 12: DrumTabParser (TDD)

**Files:**
- Create: `app/src/test/java/ph/nextbank/drums/audio/songsterr/DrumTabParserTest.kt`
- Create: `app/src/main/java/ph/nextbank/drums/audio/songsterr/DrumTabParser.kt`

Pure function — input is `RevisionJson`, output is `ParseResult`. The exact JSON traversal depends on the shape documented in Task 8's note. The TEST SCAFFOLD below is generic; **fill in fixture-loading + assertion details against the real shape** while writing the parser.

- [ ] **Step 1: Write the parser interface + result types**

Create `app/src/main/java/ph/nextbank/drums/audio/songsterr/DrumTabParser.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import ph.nextbank.drums.data.model.DrumToken

sealed interface ParseResult {
    data class Success(
        val bpm: Int,
        val timeSig: Pair<Int, Int>,
        val slotsPerBar: Int,
        /** Outer = bars; middle = slots; inner = drum tokens hit at that slot. */
        val bars: List<List<List<DrumToken>>>,
        val warnings: List<String>,
    ) : ParseResult

    data object NoDrumTrack : ParseResult
    data class ParseError(val reason: String) : ParseResult
}

interface DrumTabParser {
    fun parse(revision: RevisionJson): ParseResult
}
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/ph/nextbank/drums/audio/songsterr/DrumTabParserTest.kt`:

```kotlin
package ph.nextbank.drums.audio.songsterr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.nextbank.drums.data.model.DrumToken
import java.io.File

class DrumTabParserTest {

    private val parser: DrumTabParser = DefaultDrumTabParser()

    private fun rev(root: JsonObject) =
        RevisionJson(songId = 1, revisionId = 1, drumTrackHash = "drums_test", root = root)

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(File("src/test/resources/songsterr/$name").readText())
            .let { it as JsonObject }

    @Test fun `IATA real fixture parses without error and returns at least 1 bar`() {
        val result = parser.parse(
            RevisionJson(
                songId = 50420,
                revisionId = 5548296,
                drumTrackHash = "drums_OX64y6oG",
                root = fixture("revision-sample.json"),
            ),
        )
        assertTrue("expected Success, got $result", result is ParseResult.Success)
        val s = result as ParseResult.Success
        assertTrue("bars should be non-empty", s.bars.isNotEmpty())
        assertTrue("bpm should be > 0", s.bpm > 0)
        assertEquals("first bar slot count matches slotsPerBar", s.slotsPerBar, s.bars[0].size)
    }

    @Test fun `kick on beat 1 of a single 4-4 bar at 16ths lands on slot 0`() {
        // Build a minimal RevisionJson with the structure documented in
        // 2026-05-23-songsterr-track-data-endpoint.md — one bar, one beat, one note (MIDI 36 kick).
        val handcrafted: JsonObject = buildJsonObject {
            // FILL THIS IN against the discovered shape. Pseudocode shown below.
            //
            // put("tempo", 120)
            // put("timeSignature", buildJsonObject { put("numerator", 4); put("denominator", 4) })
            // putJsonArray("bars") { add(buildJsonObject { putJsonArray("beats") { add(...) } }) }
            //
            // Replace these comments + the empty object with the actual structure.
        }
        val result = parser.parse(rev(handcrafted))
        assertTrue("expected Success, got $result", result is ParseResult.Success)
        val s = result as ParseResult.Success
        assertEquals(1, s.bars.size)
        assertEquals(DrumToken.KICK, s.bars[0][0].firstOrNull())
    }

    @Test fun `unmapped MIDI percussion numbers are dropped without crashing`() {
        // Build a single-bar fixture with MIDI 56 (cowbell, unmapped) + 36 (kick, mapped).
        // Assert the bar contains exactly one KICK and no other tokens.
        // FILL THIS IN against the discovered shape.
    }

    @Test fun `no drum track in revision returns NoDrumTrack`() {
        // Pass a RevisionJson whose drumTrackHash references a track that
        // doesn't exist or whose track has zero notes. Adjust the assertion
        // based on what the discovered shape calls "empty".
        // FILL THIS IN against the discovered shape.
    }

    @Test fun `triplet-feel input produces slotsPerBar=24`() {
        // Build a single-bar fixture with at least one triplet (tuplet 3:2) note.
        // Assert s.slotsPerBar == 24.
        // FILL THIS IN against the discovered shape.
    }

    @Test fun `mid-song time-signature change uses first meter and emits a warning`() {
        // Build a 2-bar fixture: bar 1 = 4/4, bar 2 = 3/4.
        // Assert s.timeSig == 4 to 4 and s.warnings contains a "meter change" message.
        // FILL THIS IN against the discovered shape.
    }
}
```

The hand-crafted-fixture tests use `FILL THIS IN` because they depend on the exact JSON shape from Task 8. **Replace each `// FILL THIS IN` comment with concrete `buildJsonObject { ... }` calls** that match the discovery note's shape; do NOT commit a test file with `FILL THIS IN` still in it.

- [ ] **Step 3: Run the tests — expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.audio.songsterr.DrumTabParserTest"
```

Expected: COMPILATION FAILED — `DefaultDrumTabParser` not found.

- [ ] **Step 4: Implement DefaultDrumTabParser**

Replace the entire contents of `app/src/main/java/ph/nextbank/drums/audio/songsterr/DrumTabParser.kt` with (the interface + sealed types from Step 1 are kept, the impl class is added below):

```kotlin
package ph.nextbank.drums.audio.songsterr

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ph.nextbank.drums.data.model.DrumToken

private const val TAG = "DrumsSgs"

sealed interface ParseResult {
    data class Success(
        val bpm: Int,
        val timeSig: Pair<Int, Int>,
        val slotsPerBar: Int,
        val bars: List<List<List<DrumToken>>>,
        val warnings: List<String>,
    ) : ParseResult

    data object NoDrumTrack : ParseResult
    data class ParseError(val reason: String) : ParseResult
}

interface DrumTabParser {
    fun parse(revision: RevisionJson): ParseResult
}

class DefaultDrumTabParser : DrumTabParser {

    override fun parse(revision: RevisionJson): ParseResult {
        val root = revision.root
        val warnings = mutableListOf<String>()

        // Read top-level metadata. Field names below come from the discovery note —
        // change them to match the actual shape.
        val bpm = root.intAtPath("tempo") ?: root.intAtPath("bpm")
            ?: return ParseResult.ParseError("no tempo/bpm at root")
        val timeSig = readFirstMeter(root) ?: return ParseResult.ParseError("no time signature")

        val barsJson = root["bars"]?.jsonArray
            ?: return ParseResult.ParseError("no 'bars' array at root")
        if (barsJson.isEmpty()) return ParseResult.NoDrumTrack

        // Detect triplet feel from any tuplet markers in the first ~8 bars.
        val slotsPerBar = if (hasTripletFeel(barsJson)) 24 else 16

        val bars = mutableListOf<List<List<DrumToken>>>()
        var sawMeterChange = false

        for ((i, barEl) in barsJson.withIndex()) {
            val bar = barEl.jsonObject
            val barMeter = readMeter(bar)
            if (barMeter != null && barMeter != timeSig && !sawMeterChange) {
                warnings += "bar ${i + 1}: meter changes to ${barMeter.first}/${barMeter.second} — using first meter"
                sawMeterChange = true
            }
            bars += buildSlotsForBar(bar, slotsPerBar, timeSig, warnings)
        }

        if (bars.all { it.all(List<DrumToken>::isEmpty) }) return ParseResult.NoDrumTrack

        return ParseResult.Success(
            bpm = bpm,
            timeSig = timeSig,
            slotsPerBar = slotsPerBar,
            bars = bars,
            warnings = warnings,
        )
    }

    // ─── Helpers — adapt field names to the discovery note ─────────────────

    /** Look up an integer field by dot-path. Returns null on any miss. */
    private fun JsonObject.intAtPath(path: String): Int? {
        val parts = path.split(".")
        var cur: JsonElement = this
        for (p in parts) {
            cur = (cur as? JsonObject)?.get(p) ?: return null
        }
        return runCatching { (cur as JsonPrimitive).content.toInt() }.getOrNull()
    }

    private fun readFirstMeter(root: JsonObject): Pair<Int, Int>? {
        // Replace with the actual JSON path from the discovery note.
        val ts = root["timeSignature"]?.jsonObject ?: return null
        val n = ts["numerator"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val d = ts["denominator"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        return n to d
    }

    private fun readMeter(bar: JsonObject): Pair<Int, Int>? {
        val ts = bar["timeSignature"]?.jsonObject ?: return null
        val n = ts["numerator"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val d = ts["denominator"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        return n to d
    }

    private fun hasTripletFeel(barsJson: JsonArray): Boolean {
        for (barEl in barsJson.take(8)) {
            val beats = barEl.jsonObject["beats"]?.jsonArray ?: continue
            for (beatEl in beats) {
                val tuplet = beatEl.jsonObject["tuplet"]?.jsonPrimitive?.content
                if (tuplet == "3" || tuplet == "6" || tuplet == "12") return true
            }
        }
        return false
    }

    private fun buildSlotsForBar(
        bar: JsonObject,
        slotsPerBar: Int,
        timeSig: Pair<Int, Int>,
        warnings: MutableList<String>,
    ): List<List<DrumToken>> {
        val slots = MutableList(slotsPerBar) { mutableListOf<DrumToken>() }
        val beats = bar["beats"]?.jsonArray ?: return slots
        for (beatEl in beats) {
            val beat = beatEl.jsonObject
            // The 'position' field is in fractions of a beat from bar start (0.0 to timeSig.first).
            // Adjust the field name to match the discovery note.
            val posFrac = beat["position"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val slotIdx = ((posFrac / timeSig.first) * slotsPerBar).toInt().coerceIn(0, slotsPerBar - 1)
            val notes = beat["notes"]?.jsonArray ?: continue
            for (noteEl in notes) {
                val midi = noteEl.jsonObject["midi"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                val token = MidiPercussionMap.toToken(midi)
                if (token == null) {
                    Log.d(TAG, "dropping unmapped MIDI percussion $midi")
                    continue
                }
                if (token !in slots[slotIdx]) slots[slotIdx].add(token)
            }
        }
        return slots
    }
}
```

The helper functions (`readFirstMeter`, `readMeter`, `hasTripletFeel`, `buildSlotsForBar`) reference field names like `timeSignature`, `numerator`, `denominator`, `bars`, `beats`, `position`, `notes`, `midi`, `tuplet`. **Update these to match the exact field names recorded in `docs/superpowers/notes/2026-05-23-songsterr-track-data-endpoint.md`** before running the tests.

- [ ] **Step 5: Run the tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.audio.songsterr.DrumTabParserTest"
```

Expected: 6 tests pass. The first test (real-fixture) catches if the field names in the parser don't match the actual JSON shape — that's the canary.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/audio/songsterr/DrumTabParser.kt \
        app/src/test/java/ph/nextbank/drums/audio/songsterr/DrumTabParserTest.kt
git commit -m "Implement DrumTabParser with fixture-based regression coverage"
```

---

### Task 13: Provide fetcher + parser from Hilt

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/di/AppModule.kt`

- [ ] **Step 1: Add imports**

In `AppModule.kt`, alongside the other Songsterr imports:

```kotlin
import ph.nextbank.drums.audio.songsterr.DefaultDrumTabParser
import ph.nextbank.drums.audio.songsterr.DrumTabParser
import ph.nextbank.drums.audio.songsterr.OkHttpSongsterrTabFetcher
import ph.nextbank.drums.audio.songsterr.SongsterrTabFetcher
```

- [ ] **Step 2: Add the providers**

Below the existing `provideSongsterrSearchService`:

```kotlin
    @Provides @Singleton
    fun provideSongsterrTabFetcher(): SongsterrTabFetcher = OkHttpSongsterrTabFetcher()

    @Provides @Singleton
    fun provideDrumTabParser(): DrumTabParser = DefaultDrumTabParser()
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/di/AppModule.kt
git commit -m "Provide SongsterrTabFetcher + DrumTabParser via Hilt"
```

---

# Milestone 3 — Wire it together + drop bundled (Tasks 14–18)

End-to-end: a Songsterr search result → tap → real bars in the DB → existing YouTube search runs → song appears in the library + plays with real audio.

### Task 14: AddSongViewModel orchestration + tests

**Files:**
- Create: `app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt`

The full flow: search → user picks → `fetchDrumTrack` → `parse` → persist as `Song` → emit a "song added, songId=X" event for navigation.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt`:

```kotlin
package ph.nextbank.drums.ui.add

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ph.nextbank.drums.audio.songsterr.FakeSongsterrSearchService
import ph.nextbank.drums.audio.songsterr.FakeSongsterrTabFetcher
import ph.nextbank.drums.audio.songsterr.FetchResult
import ph.nextbank.drums.audio.songsterr.ParseResult
import ph.nextbank.drums.audio.songsterr.RevisionJson
import ph.nextbank.drums.audio.songsterr.SongsterrResult
import ph.nextbank.drums.audio.songsterr.SongsterrTrack
import ph.nextbank.drums.audio.songsterr.DrumTabParser
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.repo.FakeSongRepository

class AddSongViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private val sampleResult = SongsterrResult(
        songId = 50420,
        artistId = 1583,
        artist = "Phil Collins",
        title = "In The Air Tonight",
        tracks = listOf(SongsterrTrack(1024, "Drums", "Drums", 1000, null, "drums_X")),
        popularTrackDrum = 0,
    )

    private val successResult = ParseResult.Success(
        bpm = 95,
        timeSig = 4 to 4,
        slotsPerBar = 16,
        bars = listOf(List(16) { idx -> if (idx == 0) listOf(DrumToken.KICK) else emptyList() }),
        warnings = emptyList(),
    )

    private fun mkVm(
        searchResults: List<SongsterrResult> = listOf(sampleResult),
        fetchResult: FetchResult = FetchResult.Success(RevisionJson(50420, 1, "drums_X", buildJsonObject {})),
        parseResult: ParseResult = successResult,
    ): Triple<AddSongViewModel, FakeSongRepository, FakeSongsterrTabFetcher> {
        val repo = FakeSongRepository()
        val fetcher = FakeSongsterrTabFetcher().apply { result = fetchResult }
        val search = FakeSongsterrSearchService().apply { results = searchResults }
        val parser = object : DrumTabParser {
            override fun parse(revision: RevisionJson): ParseResult = parseResult
        }
        val vm = AddSongViewModel(search, fetcher, parser, repo)
        return Triple(vm, repo, fetcher)
    }

    @Test fun `debounced search populates results`() = runTest {
        val (vm, _, _) = mkVm()
        vm.onQueryChanged("in the air")
        advanceUntilIdle()
        assertEquals(1, vm.state.first().results.size)
    }

    @Test fun `clicking a result persists a Song with parsed bars`() = runTest {
        val (vm, repo, fetcher) = mkVm()
        vm.onResultClicked(sampleResult)
        advanceUntilIdle()
        assertEquals(50420L, fetcher.lastSongId)
        val snap = repo.allSnapshot()
        assertEquals(1, snap.size)
        val saved = snap.first()
        assertEquals("Phil Collins", saved.artist)
        assertEquals(95, saved.bpm)
        assertEquals(1, saved.bars.size)
        assertEquals(DrumToken.KICK, saved.bars[0][0].first())
    }

    @Test fun `NoDrumTrack from fetcher does not persist a song`() = runTest {
        val (vm, repo, _) = mkVm(fetchResult = FetchResult.NoDrumTrack)
        vm.onResultClicked(sampleResult)
        advanceUntilIdle()
        assertTrue(repo.allSnapshot().isEmpty())
        // ViewModel should emit a user-facing error/event
        val ev = vm.events.replayCache.firstOrNull() ?: vm.events.first()
        assertTrue(ev is AddSongEvent.Toast)
        assertTrue("toast: ${(ev as AddSongEvent.Toast).message}", ev.message.contains("drum tab", ignoreCase = true))
    }

    @Test fun `NetworkError from fetcher surfaces a connection toast`() = runTest {
        val (vm, repo, _) = mkVm(fetchResult = FetchResult.NetworkError)
        vm.onResultClicked(sampleResult)
        advanceUntilIdle()
        assertTrue(repo.allSnapshot().isEmpty())
        val ev = vm.events.replayCache.firstOrNull() ?: vm.events.first()
        assertTrue(ev is AddSongEvent.Toast)
        assertTrue("toast: ${(ev as AddSongEvent.Toast).message}", ev.message.contains("connection", ignoreCase = true))
    }

    @Test fun `ParseError surfaces a generic parse toast`() = runTest {
        val (vm, repo, _) = mkVm(parseResult = ParseResult.ParseError("bad shape"))
        vm.onResultClicked(sampleResult)
        advanceUntilIdle()
        assertTrue(repo.allSnapshot().isEmpty())
        val ev = vm.events.replayCache.firstOrNull() ?: vm.events.first()
        assertTrue(ev is AddSongEvent.Toast)
    }
}
```

The test uses `FakeSongRepository.allSnapshot()` — verify that helper exists on the fake. If `FakeSongRepository` doesn't have it, add it:

In `app/src/test/java/ph/nextbank/drums/data/repo/FakeSongRepository.kt` (existing file from Phase 2), add:

```kotlin
    /** Test helper — returns all currently-stored songs. */
    fun allSnapshot(): List<Song> = songs.value.values.toList()
```

- [ ] **Step 2: Run the tests — expect failure**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.ui.add.AddSongViewModelTest"
```

Expected: COMPILATION FAILED — `AddSongEvent`, `vm.events`, and the new 4-arg constructor don't exist yet.

- [ ] **Step 3: Replace AddSongViewModel with the orchestrating version**

Replace the entire contents of `app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt` with:

```kotlin
package ph.nextbank.drums.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.songsterr.DrumTabParser
import ph.nextbank.drums.audio.songsterr.FetchResult
import ph.nextbank.drums.audio.songsterr.ParseResult
import ph.nextbank.drums.audio.songsterr.SongsterrResult
import ph.nextbank.drums.audio.songsterr.SongsterrSearchService
import ph.nextbank.drums.audio.songsterr.SongsterrTabFetcher
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.data.repo.SongRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 400L

data class AddSongUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isAdding: Boolean = false,
    val results: List<SongsterrResult> = emptyList(),
)

sealed interface AddSongEvent {
    data class Toast(val message: String) : AddSongEvent
    data class SongAdded(val songId: String) : AddSongEvent
}

@HiltViewModel
class AddSongViewModel @Inject constructor(
    private val searchService: SongsterrSearchService,
    private val tabFetcher: SongsterrTabFetcher,
    private val parser: DrumTabParser,
    private val repo: SongRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddSongUiState())
    val state: StateFlow<AddSongUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AddSongEvent>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<AddSongEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.value = _state.value.copy(isSearching = false, results = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.value = _state.value.copy(isSearching = true)
            val res = searchService.search(q).take(10)
            _state.value = _state.value.copy(isSearching = false, results = res)
        }
    }

    fun onResultClicked(result: SongsterrResult) {
        if (_state.value.isAdding) return
        _state.value = _state.value.copy(isAdding = true)
        viewModelScope.launch {
            val outcome = runCatching {
                val fetched = tabFetcher.fetchDrumTrack(result.songId)
                handleFetchResult(result, fetched)
            }.getOrElse {
                _events.tryEmit(AddSongEvent.Toast("Couldn't load tab — try a different result"))
            }
            _state.value = _state.value.copy(isAdding = false)
        }
    }

    private suspend fun handleFetchResult(result: SongsterrResult, fetched: FetchResult) {
        when (fetched) {
            is FetchResult.Success -> {
                when (val parsed = parser.parse(fetched.data)) {
                    is ParseResult.Success -> persistSong(result, fetched.data.revisionId, parsed)
                    is ParseResult.NoDrumTrack ->
                        _events.tryEmit(AddSongEvent.Toast("This song doesn't have a drum tab on Songsterr."))
                    is ParseResult.ParseError ->
                        _events.tryEmit(AddSongEvent.Toast("Couldn't read the tab data."))
                }
            }
            FetchResult.NoDrumTrack ->
                _events.tryEmit(AddSongEvent.Toast("This song doesn't have a drum tab on Songsterr."))
            is FetchResult.ScrapeFailure ->
                _events.tryEmit(AddSongEvent.Toast("Couldn't load tab from Songsterr — try a different result"))
            FetchResult.NetworkError ->
                _events.tryEmit(AddSongEvent.Toast("Check your connection."))
        }
    }

    private suspend fun persistSong(
        result: SongsterrResult,
        revisionId: Long,
        parsed: ParseResult.Success,
    ) {
        val coverInitials = (result.artist.take(1) + result.title.take(1)).uppercase().ifEmpty { "??" }
        val song = Song(
            id = UUID.randomUUID().toString(),
            title = result.title,
            artist = result.artist,
            bpm = parsed.bpm,
            timeSig = parsed.timeSig,
            bars = parsed.bars,
            coverInitials = coverInitials,
            importedFrom = ImportSource.BUNDLED, // semantics: app-added; Phase 4 can introduce a SONGSTERR enum value if needed
            lastPlayed = null,
            youtubeVideoId = null,
            songsterrId = result.songId,
            songsterrRevisionId = revisionId.toString(),
        )
        repo.upsertAll(listOf(song))
        _events.tryEmit(AddSongEvent.SongAdded(song.id))
    }
}
```

`Song`'s constructor doesn't accept `songsterrId` / `songsterrRevisionId` yet — that's Task 15. The compile will fail here; that's expected, and Task 15 unblocks it.

- [ ] **Step 4: Don't run tests yet** — Task 15 finishes the `Song` shape. Move on.

---

### Task 15: Add Songsterr fields to Song domain model

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/data/model/Song.kt`

- [ ] **Step 1: Add the two new optional fields**

Replace the contents of `Song.kt` with:

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
    /** Songsterr songId for songs added via the Add Song flow. null for legacy / bundled songs. */
    val songsterrId: Long? = null,
    /** Songsterr revisionId for re-fetching. null for legacy / bundled songs. */
    val songsterrRevisionId: String? = null,
) {
    val totalBars: Int get() = bars.size
    val slotsPerBar: Int get() = bars.firstOrNull()?.size ?: 16
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD FAILED — `SongEntity.toSong()` and `fromSong()` don't map the new fields. That's Task 16's job. Move on.

- [ ] **Step 3: Do not commit yet** — Task 16 completes the data-layer change.

---

### Task 16: SongEntity columns + DB migration v4

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt`

- [ ] **Step 1: Add columns to SongEntity**

Replace the contents of `SongEntity.kt` with the existing file's structure, adding the two new columns + mapping them in `toSong` / `fromSong`:

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
    val songsterrId: Long?,
    val songsterrRevisionId: String?,
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
        songsterrId = songsterrId,
        songsterrRevisionId = songsterrRevisionId,
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
            songsterrId = s.songsterrId,
            songsterrRevisionId = s.songsterrRevisionId,
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

- [ ] **Step 2: Bump DB version + add MIGRATION_3_4**

In `AppDatabase.kt`, change `@Database(entities = [SongEntity::class], version = 3, exportSchema = false)` to:

```kotlin
@Database(entities = [SongEntity::class], version = 4, exportSchema = false)
```

After the existing `MIGRATION_2_3` block, add:

```kotlin
        /** v3 → v4: added songsterrId + songsterrRevisionId for Songsterr-sourced tabs. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN songsterrId INTEGER")
                db.execSQL("ALTER TABLE songs ADD COLUMN songsterrRevisionId TEXT")
            }
        }
```

And update the `addMigrations` call:

```kotlin
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the AddSongViewModel tests**

```bash
./gradlew :app:testDebugUnitTest --tests "ph.nextbank.drums.ui.add.AddSongViewModelTest"
```

Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/data/model/Song.kt \
        app/src/main/java/ph/nextbank/drums/data/db/SongEntity.kt \
        app/src/main/java/ph/nextbank/drums/data/db/AppDatabase.kt \
        app/src/main/java/ph/nextbank/drums/ui/add/AddSongViewModel.kt \
        app/src/test/java/ph/nextbank/drums/ui/add/AddSongViewModelTest.kt \
        app/src/test/java/ph/nextbank/drums/data/repo/FakeSongRepository.kt
git commit -m "Add Songsterr ID columns + DB migration v4; wire AddSongViewModel persistence"
```

---

### Task 17: AddSongScreen reacts to events + navigates to player

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/ui/nav/NavGraph.kt`

- [ ] **Step 1: Add an onSongAdded callback to AddSongScreen + collect events**

Replace the existing `AddSongScreen` Composable signature and add an event-collection block at the top:

```kotlin
@Composable
fun AddSongScreen(
    onBack: () -> Unit,
    onSongAdded: (String) -> Unit,
    vm: AddSongViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is AddSongEvent.Toast -> Toast.makeText(ctx, ev.message, Toast.LENGTH_SHORT).show()
                is AddSongEvent.SongAdded -> onSongAdded(ev.songId)
            }
        }
    }
    // …existing screen body unchanged
}
```

Add these imports at the top of `AddSongScreen.kt`:

```kotlin
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
```

If `isAdding` is true, dim the result list — find the `LazyColumn(...)` and wrap or modify its enabled state:

```kotlin
LazyColumn(modifier = Modifier.fillMaxWidth()) {
    items(state.results, key = { it.songId }) { result ->
        SongsterrResultRow(
            result = result,
            enabled = !state.isAdding,
            onClick = { vm.onResultClicked(result) },
        )
        Spacer(Modifier.height(8.dp))
    }
}
```

Update `SongsterrResultRow` to take an `enabled: Boolean` and pass it through to `clickable(enabled = hasDrums && enabled, …)`.

- [ ] **Step 2: Pass onSongAdded from NavGraph**

In `NavGraph.kt`, change:

```kotlin
composable(Route.Upload.path) {
    AddSongScreen(onBack = { nav.popBackStack() })
}
```

to:

```kotlin
composable(Route.Upload.path) {
    AddSongScreen(
        onBack = { nav.popBackStack() },
        onSongAdded = { id ->
            nav.popBackStack()  // close AddSong screen
            nav.navigate(Route.Player(id).path)
        },
    )
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt \
        app/src/main/java/ph/nextbank/drums/ui/nav/NavGraph.kt
git commit -m "Wire AddSongScreen events to navigation + toasts"
```

---

### Task 18: Drop bundled songs

**Files:**
- Delete: `app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt`
- Modify: `app/src/main/java/ph/nextbank/drums/di/AppModule.kt`

- [ ] **Step 1: Remove the seed call**

In `AppModule.kt`, find:

```kotlin
    @Provides @Singleton
    fun provideSongRepository(dao: SongDao, scope: CoroutineScope): SongRepository {
        val repo: SongRepository = RoomSongRepository(dao)
        // Seed bundled songs on first launch only — preserves user-set youtubeVideoId / offset / blocklist on subsequent starts.
        scope.launch { repo.seedNew(SAMPLE_SONGS) }
        return repo
    }
```

Replace with:

```kotlin
    @Provides @Singleton
    fun provideSongRepository(dao: SongDao): SongRepository = RoomSongRepository(dao)
```

Also remove the import:

```kotlin
import ph.nextbank.drums.data.samples.SAMPLE_SONGS
```

If the function no longer references `scope`, drop the `scope: CoroutineScope` parameter — Hilt regenerates.

- [ ] **Step 2: Delete the file**

```bash
rm app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt
rmdir app/src/main/java/ph/nextbank/drums/data/samples 2>/dev/null || true
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If any file still imports `SAMPLE_SONGS` or `data.samples.*`, delete that import.

- [ ] **Step 4: Manual smoke test on device**

```bash
$ANDROID_HOME/platform-tools/adb shell pm clear ph.nextbank.drums
./gradlew :app:installDebug
$ANDROID_HOME/platform-tools/adb shell am start -n ph.nextbank.drums/.MainActivity
```

Expected: empty library with "Tap the + button to add a song from Songsterr." copy. Tap `+`, search a song, pick it, wait for the toast + nav to player. The player should now show the real drum tab (recognizable groove, NOT the GROOVE_A placeholder pattern). The existing Phase 2 YouTube flow kicks in — confirm a video, hear audio.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/di/AppModule.kt
git rm app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt
git commit -m "Drop bundled songs — library now starts empty"
```

---

# Milestone 4 — Polish (Tasks 19–20)

### Task 19: Empty-results + loading polish on AddSongScreen

**Files:**
- Modify: `app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt`

After Task 17, the screen has results + searching state but no explicit "no results found" message and no disabled visual for the add-in-progress state.

- [ ] **Step 1: Add an empty-results state**

After the `if (state.isSearching) { ... } else { LazyColumn { ... } }` block, insert (or fold into the else branch):

```kotlin
} else if (state.query.isNotBlank() && state.results.isEmpty()) {
    Text("No results.", color = DrumsColors.Dim, style = DrumsType.caption)
} else {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(state.results, key = { it.songId }) { result ->
            SongsterrResultRow(
                result = result,
                enabled = !state.isAdding,
                onClick = { vm.onResultClicked(result) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 2: Add a global "adding song…" overlay**

Below the entire `Column(...)`, add an `if (state.isAdding)` that overlays a centered spinner + "Loading tab…" text. Simplest implementation: wrap the whole body in a `Box` and draw the overlay on top.

Concrete patch — replace the outer `Column(...) { ... }` with:

```kotlin
Box(Modifier.fillMaxSize().background(DrumsColors.Bg)) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        // …existing screen body
    }
    if (state.isAdding) {
        Box(
            Modifier
                .fillMaxSize()
                .background(DrumsColors.Bg.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = DrumsColors.Accent)
                Spacer(Modifier.height(12.dp))
                Text("Loading tab…", color = DrumsColors.Text, style = DrumsType.body)
            }
        }
    }
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ph/nextbank/drums/ui/add/AddSongScreen.kt
git commit -m "Empty-results message + adding-song overlay on AddSongScreen"
```

---

### Task 20: End-to-end smoke regression

**Files:** (none — verification only)

- [ ] **Step 1: Fresh-install run**

```bash
$ANDROID_HOME/platform-tools/adb shell pm clear ph.nextbank.drums
./gradlew :app:installDebug
$ANDROID_HOME/platform-tools/adb shell am start -n ph.nextbank.drums/.MainActivity
```

- [ ] **Step 2: Walk the golden path**

In the app:
1. Library is empty (just the "+ to add a song from Songsterr" message).
2. Tap `+`.
3. Search "in the air tonight". Wait for results.
4. Tap "In The Air Tonight" by Phil Collins. Watch the "Loading tab…" overlay.
5. Navigation lands on the player. Staff shows a recognizable Phil Collins groove (NOT the GROOVE_A 4-on-the-floor placeholder).
6. YouTube confirmation dialog appears. Accept. Audio starts.

Expected: no toasts about errors, no crashes, audio plays in sync with the staff.

- [ ] **Step 3: Walk an error path**

1. Tap `+` again.
2. Search "asdf asdf asdf zzz". Wait.
3. Either "No results." appears, OR results show a mix — some clickable, some with "· no drum tab" in dim text.

For any row labeled "no drum tab": confirm it's visually dim and tapping it does nothing (rows are disabled in the UI — `AddSongScreen.kt:164` gates `.clickable` on `hasDrums`). Stay on the screen.

For a clickable row whose fetch turns out to have no drum track (a rarer case than no-drum-tab-in-search), the `AddSongViewModel` still emits the "This song doesn't have a drum tab on Songsterr." toast — covered by unit tests, not exercised here.

- [ ] **Step 4: Run the full unit-test suite as a final regression**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit nothing if no code changed**

If the smoke test surfaces a bug, fix it in a follow-up task; this task is verification.

---

## Risks and mitigations (reference)

| Risk | Mitigation |
|------|------------|
| Songsterr changes endpoint paths between Task 8 and shipping | Run Task 8's discovery again; update `OkHttpSongsterrTabFetcher.buildTrackDataUrl` and `DefaultDrumTabParser` field paths. Fixture-based tests catch regressions. |
| Track-data JSON shape varies between songs (some have triplets, some have meter changes, etc.) | Capture multiple fixtures over time (under `app/src/test/resources/songsterr/`), one per pathological case. |
| YouTube auto-match picks the wrong audio video | Existing Phase 2 "Try another video" affordance still works. |
| Songsterr rate-limits or blocks our User-Agent | Hard to detect ahead of time; if it happens, lower the search debounce + use a more common User-Agent. |
