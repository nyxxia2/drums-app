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
        val base = server.url("/").toString().trimEnd('/')
        fetcher = OkHttpSongsterrTabFetcher(
            client = OkHttpClient(),
            metaBaseUrl = base,
            cdnBaseUrl = base,  // same MockWebServer for tests
        )
    }

    @After fun tearDown() { server.shutdown() }

    private fun fixture(name: String): String =
        File("src/test/resources/songsterr/$name").readText()

    @Test fun `happy path returns Success with parsed RevisionJson`() = runTest {
        // /api/meta/50420 — minimal valid shape with a drum track at index 1
        server.enqueue(
            MockResponse().setBody(
                """{"songId":50420,"revisionId":5548296,"image":"v5-test-slug","tracks":[
                    {"instrumentId":29,"instrument":"Guitar","name":"G","hash":"guitar_AAA","partId":0},
                    {"instrumentId":1024,"instrument":"Drums","name":"D","hash":"drums_BBB","partId":1}
                ],"popularTrackDrum":1}""",
            ),
        )
        // Track-data fixture
        server.enqueue(MockResponse().setBody(fixture("revision-sample.json")))

        val result = fetcher.fetchDrumTrack(50420L)
        assertTrue("expected Success, got $result", result is FetchResult.Success)
        val data = (result as FetchResult.Success).data
        assertEquals(50420L, data.songId)
        assertEquals(5548296L, data.revisionId)
        assertEquals("drums_BBB", data.drumTrackHash)
        // root JSON tree must be non-empty
        assertTrue(data.root.isNotEmpty())
    }

    @Test fun `returns NoDrumTrack when meta has no instrumentId 1024 track`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"songId":1,"revisionId":2,"image":"v5-x","tracks":[
                    {"instrumentId":29,"instrument":"Guitar","name":"G","hash":"guitar_X","partId":0}
                ]}""",
            ),
        )
        val result = fetcher.fetchDrumTrack(1L)
        assertEquals(FetchResult.NoDrumTrack, result)
    }

    @Test fun `falls back to scanning tracks when popularTrackDrum is missing`() = runTest {
        // No popularTrackDrum field — must find drum by scanning instrumentId == 1024
        server.enqueue(
            MockResponse().setBody(
                """{"songId":1,"revisionId":2,"image":"v5-x","tracks":[
                    {"instrumentId":29,"instrument":"Guitar","name":"G","hash":"guitar_X","partId":0},
                    {"instrumentId":1024,"instrument":"Drums","name":"D","hash":"drums_FOUND","partId":1,"views":500}
                ]}""",
            ),
        )
        server.enqueue(MockResponse().setBody(fixture("revision-sample.json")))
        val result = fetcher.fetchDrumTrack(1L)
        assertTrue("expected Success, got $result", result is FetchResult.Success)
        assertEquals("drums_FOUND", (result as FetchResult.Success).data.drumTrackHash)
    }

    @Test fun `returns ScrapeFailure when meta endpoint returns 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = fetcher.fetchDrumTrack(1L)
        assertTrue("expected ScrapeFailure, got $result", result is FetchResult.ScrapeFailure)
    }

    @Test fun `returns ScrapeFailure when track-data endpoint returns 404`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"songId":1,"revisionId":2,"image":"v5-x","tracks":[
                    {"instrumentId":1024,"instrument":"Drums","name":"D","hash":"drums_X","partId":0}
                ],"popularTrackDrum":0}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(404))
        val result = fetcher.fetchDrumTrack(1L)
        assertTrue("expected ScrapeFailure, got $result", result is FetchResult.ScrapeFailure)
    }

    @Test fun `returns NetworkError on connection failure`() = runTest {
        server.shutdown()
        val result = fetcher.fetchDrumTrack(1L)
        assertEquals(FetchResult.NetworkError, result)
    }

    @Test fun `meta endpoint URL is constructed correctly`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"songId":50420,"revisionId":5548296,"image":"v5-x","tracks":[
                    {"instrumentId":1024,"instrument":"Drums","name":"D","hash":"drums_X","partId":0}
                ],"popularTrackDrum":0}""",
            ),
        )
        server.enqueue(MockResponse().setBody(fixture("revision-sample.json")))
        fetcher.fetchDrumTrack(50420L)
        val req1 = server.takeRequest()
        assertEquals("/api/meta/50420", req1.path)
    }
}
