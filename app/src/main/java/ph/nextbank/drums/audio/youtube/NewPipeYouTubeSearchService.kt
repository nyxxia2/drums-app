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

    override suspend fun findFor(
        query: String,
        blocklist: Set<String>,
        maxResults: Int,
    ): List<SearchResult> =
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
                    .sortedBy { rankScore(it.title, it.channelTitle) }
                    .take(maxResults)
                    .toList()
                    .also { ranked ->
                        Log.d(TAG, "after filtering blocklist=${blocklist.size}, took top ${ranked.size}; first='${ranked.firstOrNull()?.title}' channel='${ranked.firstOrNull()?.channelTitle}'")
                    }
            }.getOrNull() ?: emptyList()
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

        /**
         * Combined ranking score: lower = better. Content-mismatch dominates so a VEVO/Topic
         * upload of the real song beats a user-uploaded karaoke version.
         */
        fun rankScore(title: String, channel: String): Int =
            contentMismatchScore(title, channel) + embedRestrictionScore(channel)

        /**
         * Penalize uploads whose content doesn't match what the user wants. For a drumming app,
         * karaoke and drumless tracks are useless (no drum audio), so they're effectively banned.
         * Covers/instrumentals are wrong but at least playable. Lyric videos often have the
         * original audio, so only a small penalty.
         */
        fun contentMismatchScore(title: String, channel: String): Int {
            val t = title.lowercase()
            val c = channel.lowercase()
            val both = "$t $c"
            var score = 0
            if ("karaoke" in both) score += 300
            // Drumless detection: explicit "drumless", "no drums", or any "drum(s)" + "minus"
            // combo (covers channels like "Drum Minus Tracks" and titles like "drums minus").
            val hasDrumsAndMinus = Regex("\\bdrums?\\b").containsMatchIn(both) &&
                Regex("\\bminus\\b").containsMatchIn(both)
            if ("drumless" in both ||
                Regex("\\bno drums?\\b").containsMatchIn(t) ||
                hasDrumsAndMinus
            ) score += 300
            if ("backing track" in both || "minus one" in t) score += 200
            if ("instrumental" in both) score += 200
            if (Regex("\\bcover(ed)?\\b").containsMatchIn(both)) score += 150
            if ("lyric video" in t || Regex("\\blyrics?\\b").containsMatchIn(t)) score += 50
            return score
        }

        /**
         * Heuristic: lower score = more likely to allow embedding. Vevo and YouTube Music Topic
         * channels almost always block embeds; "Official" / record-label channels usually restrict
         * embedding too. User uploads / covers / lyric video channels get score 0.
         */
        fun embedRestrictionScore(channel: String): Int {
            val c = channel.lowercase()
            return when {
                c.endsWith("vevo") -> 100
                c.endsWith(" - topic") -> 100
                c.contains("official") -> 80
                c.contains("records") -> 60
                else -> 0
            }
        }
    }

    override suspend fun getAudioStreamUrl(videoId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)
                Log.d(TAG, "extracted $videoId: ${info.audioStreams.size} audio streams")
                info.audioStreams.forEachIndexed { i, s ->
                    Log.d(
                        TAG,
                        "  [$i] format=${s.format?.name} bps=${s.averageBitrate} " +
                            "delivery=${s.deliveryMethod} contentLen=${s.content?.length} " +
                            "isUrl=${s.isUrl}",
                    )
                }
                // Prefer progressive (single-file URL) streams — ExoPlayer can play them
                // directly without DASH manifest handling.
                val best = info.audioStreams
                    .filter { it.isUrl && !it.content.isNullOrEmpty() }
                    .maxByOrNull { it.averageBitrate }
                Log.d(TAG, "best stream for $videoId: bps=${best?.averageBitrate} url=${best?.content?.take(80)}")
                best?.content
            }.onFailure { Log.e(TAG, "audio stream extraction failed for $videoId", it) }
                .getOrNull()
        }

    override suspend fun fetchMeta(videoId: String): SearchResult? =
        withContext(Dispatchers.IO) {
            runCatching {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)
                SearchResult(
                    videoId = videoId,
                    title = info.name ?: "",
                    channelTitle = info.uploaderName ?: "",
                    durationSec = info.duration.toInt(),
                    thumbnailUrl = info.thumbnails?.firstOrNull()?.url ?: "",
                )
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
}
