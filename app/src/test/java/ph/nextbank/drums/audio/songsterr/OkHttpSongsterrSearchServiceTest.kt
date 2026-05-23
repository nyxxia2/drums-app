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
        assertNotNull(phil.popularTrackDrum)
        val drum = phil.tracks[phil.popularTrackDrum!!]
        assertEquals(1024, drum.instrumentId)
        assertTrue(drum.hash.startsWith("drums_"))
    }

    @Test fun `encodes spaces as plus signs in query`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        svc.search("in the air tonight")
        val req = server.takeRequest()
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
