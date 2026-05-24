package ph.nextbank.drums.audio.youtube

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ph.nextbank.drums.audio.songsterr.VideoPointEntry

class YouTubeCandidateResolverTest {

    private fun meta(id: String) = SearchResult(
        videoId = id,
        title = "Title $id",
        channelTitle = "Channel",
        durationSec = 200,
        thumbnailUrl = "thumb",
    )

    @Test
    fun `synced - first entry meta resolves - returns it`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf("aaaa1111111" to meta("aaaa1111111"))
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(VideoPointEntry("aaaa1111111", listOf(0.0, 2.0), null))

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet())

        assertEquals("aaaa1111111", out?.videoId)
    }

    @Test
    fun `synced - first entry meta null - falls through to second`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf("bbbb2222222" to meta("bbbb2222222"))
            // "aaaa1111111" intentionally missing => fetchMeta returns null
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
        )

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet())

        assertEquals("bbbb2222222", out?.videoId)
    }

    @Test
    fun `synced - all metas null - returns null`() = runTest {
        val search = FakeYouTubeSearchService()  // metaResults empty
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
        )

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet())

        assertNull(out)
    }

    @Test
    fun `synced - blocklisted entries are skipped`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf(
                "aaaa1111111" to meta("aaaa1111111"),
                "bbbb2222222" to meta("bbbb2222222"),
            )
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
        )

        val out = resolver.resolveInitial("T", "A", entries, blocklist = setOf("aaaa1111111"))

        assertEquals("bbbb2222222", out?.videoId)
    }

    @Test
    fun `unsynced - delegates to findFor`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(meta("xxxx9999999"))
        }
        val resolver = YouTubeCandidateResolver(search)

        val out = resolver.resolveInitial("Song", "Artist", videoPoints = null, blocklist = emptySet())

        assertEquals("xxxx9999999", out?.videoId)
        assertEquals("Song Artist", search.lastQuery)
    }

    @Test
    fun `unsynced - empty videoPoints list also routes to findFor`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(meta("yyyy8888888"))
        }
        val resolver = YouTubeCandidateResolver(search)

        val out = resolver.resolveInitial("S", "A", videoPoints = emptyList(), blocklist = emptySet())

        assertEquals("yyyy8888888", out?.videoId)
        assertEquals(1, search.findForCalls)
    }

    @Test
    fun `unsynced - search returns null - resolver returns null`() = runTest {
        val search = FakeYouTubeSearchService().apply { shouldReturnNull = true }
        val resolver = YouTubeCandidateResolver(search)

        val out = resolver.resolveInitial("S", "A", videoPoints = null, blocklist = emptySet())

        assertNull(out)
    }

    @Test
    fun `unsynced - passes blocklist through to findFor`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(meta("yyyy8888888"))
        }
        val resolver = YouTubeCandidateResolver(search)

        resolver.resolveInitial("S", "A", videoPoints = null, blocklist = setOf("zz"))

        assertEquals(setOf("zz"), search.lastBlocklist)
    }
}
