package ph.nextbank.drums.audio.youtube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private const val TAG = "DrumsYT"

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
                val streamItems = items.filterIsInstance<StreamInfoItem>()
                Log.d(TAG, "search '$query' returned ${streamItems.size} video items")

                streamItems
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
                    // Sort: prefer user uploads (more likely to allow embedding) over
                    // VEVO / Topic / Official channels which routinely block embeds.
                    .sortedBy { embedRestrictionScore(it.channelTitle) }
                    .toList()
                    .also { ranked ->
                        Log.d(TAG, "after filtering blocklist=${blocklist.size}, ranked ${ranked.size} results; top channel='${ranked.firstOrNull()?.channelTitle}'")
                    }
                    .firstOrNull()
            }.getOrNull()
        }

    /**
     * Heuristic: lower score = more likely to allow embedding.
     * Vevo and YouTube Music Topic channels almost always block embeds;
     * "Official" channels are usually owned by labels and block embeds too.
     * User uploads / covers / lyric video channels get score 0.
     */
    private fun embedRestrictionScore(channel: String): Int {
        val c = channel.lowercase()
        return when {
            c.endsWith("vevo") -> 100
            c.endsWith(" - topic") -> 100
            c.contains("official") -> 80
            c.contains("records") -> 60   // record labels usually restrict
            else -> 0
        }
    }

    override suspend fun getAudioStreamUrl(videoId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)
                // Pick the highest-bitrate progressive (single-file) audio stream — most
                // compatible with ExoPlayer and avoids DASH/HLS adaptive complexity.
                val best = info.audioStreams
                    .filter { !it.content.isNullOrEmpty() }
                    .maxByOrNull { it.averageBitrate }
                Log.d(
                    TAG,
                    "audio stream for $videoId: ${best?.averageBitrate} bps, " +
                        "format=${best?.format?.name}",
                )
                best?.content
            }.onFailure { Log.e(TAG, "audio stream extraction failed for $videoId", it) }
                .getOrNull()
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
