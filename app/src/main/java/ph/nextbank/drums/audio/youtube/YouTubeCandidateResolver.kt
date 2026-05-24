package ph.nextbank.drums.audio.youtube

import ph.nextbank.drums.audio.songsterr.VideoPointEntry
import javax.inject.Inject
import javax.inject.Singleton

/** Number of synced entries we fetch metadata for before sorting by content rank. */
private const val SYNCED_FETCH_LIMIT = 8

/**
 * Picks initial YouTube candidates for a song.
 *
 * Synced songs (videoPoints non-empty): pre-sort entries by Songsterr's `feature` label
 * (preferring main content over backing/alternative), fetchMeta on the top
 * [SYNCED_FETCH_LIMIT], then re-rank by title/channel content-mismatch (karaoke → bottom)
 * and take [maxResults]. Unsynced songs (videoPoints null or empty) delegate to
 * [YouTubeSearchService.findFor], which applies the same content ranking on search results.
 *
 * Returns an empty list when no candidate can be resolved.
 */
@Singleton
class YouTubeCandidateResolver @Inject constructor(
    private val searchService: YouTubeSearchService,
) {
    suspend fun resolveInitial(
        title: String,
        artist: String,
        videoPoints: List<VideoPointEntry>?,
        blocklist: Set<String> = emptySet(),
        maxResults: Int = 5,
    ): List<SearchResult> {
        if (!videoPoints.isNullOrEmpty()) {
            val ranked = videoPoints
                .filter { it.youtubeVideoId !in blocklist }
                .sortedBy { syncedFeatureScore(it.feature) }
                .take(SYNCED_FETCH_LIMIT)
                .mapNotNull { entry ->
                    searchService.fetchMeta(entry.youtubeVideoId)?.let { entry to it }
                }
                .sortedBy { (entry, meta) ->
                    syncedFeatureScore(entry.feature) +
                        NewPipeYouTubeSearchService.contentMismatchScore(meta.title, meta.channelTitle)
                }
                .take(maxResults)
                .map { it.second }
            if (ranked.isNotEmpty()) return ranked
            // All synced entries were blocklisted or failed metadata fetch; fall through.
        }
        return searchService.findFor("$title $artist", blocklist, maxResults)
    }

    /**
     * Songsterr labels each curated video with a `feature`: null for the canonical performance,
     * "backing" for drumless backing tracks (useless for drum practice), "alternative" for
     * covers / karaoke / live versions / etc. Lower score is better.
     */
    internal fun syncedFeatureScore(feature: String?): Int = when (feature?.lowercase()) {
        null -> 0
        "backing" -> 300       // drumless — band whose drummer you'd be replacing
        "alternative" -> 100   // often karaoke / cover / live
        else -> 50             // unrecognized label — slight penalty
    }
}
