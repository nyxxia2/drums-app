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

class OkHttpSongsterrVideoPointsServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: OkHttpSongsterrVideoPointsService

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        service = OkHttpSongsterrVideoPointsService(
            client = OkHttpClient(),
            baseUrl = base,
        )
    }

    @After fun tearDown() { server.shutdown() }

    private fun fixture(name: String): String =
        File("src/test/resources/songsterr/$name").readText()

    @Test fun `happy path returns parsed entries`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("video-points-teen-spirit.json")))
        val result = service.fetch(songId = 269L, revisionId = 6953431L)
        assertEquals(3, result.size)
        assertEquals("zYxkezUr8MQ", result[0].youtubeVideoId)
        val expectedPoints: List<Double> = listOf(-0.15, 2.5, 4.68, 6.86, 8.95)
        assertEquals(expectedPoints, result[0].points)
        assertEquals("alternative", result[0].feature)
        assertEquals(null as String?, result[2].feature)
    }

    @Test fun `HTTP 404 returns empty list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = service.fetch(songId = 1L, revisionId = 2L)
        assertTrue("expected empty list, got $result", result.isEmpty())
    }

    @Test fun `malformed JSON returns empty list`() = runTest {
        server.enqueue(MockResponse().setBody("not json"))
        val result = service.fetch(songId = 1L, revisionId = 2L)
        assertTrue(result.isEmpty())
    }

    @Test fun `connection failure returns empty list`() = runTest {
        server.shutdown()
        val result = service.fetch(songId = 1L, revisionId = 2L)
        assertTrue(result.isEmpty())
    }

    @Test fun `URL is constructed correctly`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        service.fetch(songId = 269L, revisionId = 6953431L)
        val req = server.takeRequest()
        assertEquals("/api/video-points/269/6953431/list", req.path)
    }

    @Test fun `entry with missing optional fields still parses`() = runTest {
        // Songsterr sometimes omits feature; ignoreUnknownKeys also matters.
        server.enqueue(
            MockResponse().setBody(
                """[{"videoId":"abc","points":[0.0,1.0,2.0]}]""",
            ),
        )
        val result = service.fetch(songId = 1L, revisionId = 2L)
        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals("abc", entry.youtubeVideoId)
        val expectedPoints: List<Double> = listOf(0.0, 1.0, 2.0)
        assertEquals(expectedPoints, entry.points)
        assertEquals(null as String?, entry.feature)
    }
}
