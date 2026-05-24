package ph.nextbank.drums.audio.youtube

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `synced - first entry meta resolves - returns it first`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf("aaaa1111111" to meta("aaaa1111111"))
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(VideoPointEntry("aaaa1111111", listOf(0.0, 2.0), null))

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet())

        assertEquals(listOf("aaaa1111111"), out.map { it.videoId })
    }

    @Test
    fun `synced - first entry meta null - falls through to second`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf("bbbb2222222" to meta("bbbb2222222"))
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
        )

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet())

        assertEquals(listOf("bbbb2222222"), out.map { it.videoId })
    }

    @Test
    fun `synced - multiple metas resolve - returns them in order up to maxResults`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf(
                "aaaa1111111" to meta("aaaa1111111"),
                "bbbb2222222" to meta("bbbb2222222"),
                "cccc3333333" to meta("cccc3333333"),
            )
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
            VideoPointEntry("cccc3333333", listOf(0.0), null),
        )

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet(), maxResults = 2)

        assertEquals(listOf("aaaa1111111", "bbbb2222222"), out.map { it.videoId })
    }

    @Test
    fun `synced - all metas null - falls through to findFor`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(meta("fallback111"))
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
        )

        val out = resolver.resolveInitial("T", "A", entries, blocklist = emptySet())

        // Synced fetchMeta returns null for both → fall through to general search.
        assertEquals(listOf("fallback111"), out.map { it.videoId })
        assertEquals("T A", search.lastQuery)
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

        assertEquals(listOf("bbbb2222222"), out.map { it.videoId })
    }

    @Test
    fun `synced - all blocklisted - falls through to findFor with blocklist`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            metaResults = mapOf(
                "aaaa1111111" to meta("aaaa1111111"),
                "bbbb2222222" to meta("bbbb2222222"),
            )
            queue = listOf(meta("fallback111"))
        }
        val resolver = YouTubeCandidateResolver(search)
        val entries = listOf(
            VideoPointEntry("aaaa1111111", listOf(0.0), null),
            VideoPointEntry("bbbb2222222", listOf(0.0), null),
        )

        val out = resolver.resolveInitial(
            "T",
            "A",
            entries,
            blocklist = setOf("aaaa1111111", "bbbb2222222"),
        )

        assertEquals(listOf("fallback111"), out.map { it.videoId })
        assertEquals(setOf("aaaa1111111", "bbbb2222222"), search.lastBlocklist)
    }

    @Test
    fun `unsynced - delegates to findFor`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(meta("xxxx9999999"))
        }
        val resolver = YouTubeCandidateResolver(search)

        val out = resolver.resolveInitial("Song", "Artist", videoPoints = null, blocklist = emptySet())

        assertEquals(listOf("xxxx9999999"), out.map { it.videoId })
        assertEquals("Song Artist", search.lastQuery)
    }

    @Test
    fun `unsynced - empty videoPoints list also routes to findFor`() = runTest {
        val search = FakeYouTubeSearchService().apply {
            queue = listOf(meta("yyyy8888888"))
        }
        val resolver = YouTubeCandidateResolver(search)

        val out = resolver.resolveInitial("S", "A", videoPoints = emptyList(), blocklist = emptySet())

        assertEquals(listOf("yyyy8888888"), out.map { it.videoId })
        assertEquals(1, search.findForCalls)
    }

    @Test
    fun `unsynced - search returns empty - resolver returns empty`() = runTest {
        val search = FakeYouTubeSearchService().apply { shouldReturnNull = true }
        val resolver = YouTubeCandidateResolver(search)

        val out = resolver.resolveInitial("S", "A", videoPoints = null, blocklist = emptySet())

        assertTrue(out.isEmpty())
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
