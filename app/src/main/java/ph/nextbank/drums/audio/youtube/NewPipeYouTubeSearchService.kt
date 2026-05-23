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
                val handler = service.getSearchQHFactory().fromQuery(
                    query,
                    listOf("videos"),
                    "",
                )
                val extractor = service.getSearchExtractor(handler)
                extractor.fetchPage()
                val items = extractor.getInitialPage().items
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
