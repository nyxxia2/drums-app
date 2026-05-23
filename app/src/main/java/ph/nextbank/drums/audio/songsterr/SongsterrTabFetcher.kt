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
