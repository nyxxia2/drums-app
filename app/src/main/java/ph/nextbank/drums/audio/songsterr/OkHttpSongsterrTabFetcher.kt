package ph.nextbank.drums.audio.songsterr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG = "DrumsSgs"
private const val DEFAULT_META_BASE_URL = "https://www.songsterr.com"
private const val DEFAULT_CDN_BASE_URL = "https://dqsljvtekg760.cloudfront.net"

class OkHttpSongsterrTabFetcher(
    private val client: OkHttpClient = OkHttpClient(),
    private val metaBaseUrl: String = DEFAULT_META_BASE_URL,
    private val cdnBaseUrl: String = DEFAULT_CDN_BASE_URL,
) : SongsterrTabFetcher {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchDrumTrack(songId: Long): FetchResult =
        withContext(Dispatchers.IO) {
            // Step 1: meta endpoint → revisionId, image slug, drum track partId + hash
            val metaBody = try {
                getOrNull("$metaBaseUrl/api/meta/$songId")
            } catch (e: IOException) {
                Log.w(TAG, "meta IOException for $songId", e)
                return@withContext FetchResult.NetworkError
            } ?: return@withContext FetchResult.ScrapeFailure("meta HTTP error")

            val meta = try {
                json.parseToJsonElement(metaBody).jsonObject
            } catch (e: Exception) {
                return@withContext FetchResult.ScrapeFailure("meta parse: ${e.message}")
            }

            val revisionId = meta["revisionId"]?.jsonPrimitive?.longOrNull
                ?: return@withContext FetchResult.ScrapeFailure("missing revisionId in meta")
            val image = meta["image"]?.jsonPrimitive?.content
                ?: return@withContext FetchResult.ScrapeFailure("missing image slug in meta")
            val tracks = meta["tracks"]?.jsonArray
                ?: return@withContext FetchResult.ScrapeFailure("missing tracks in meta")

            // Pick the drum track: prefer popularTrackDrum index; fall back to scanning by instrumentId.
            val drumIndex = meta["popularTrackDrum"]?.jsonPrimitive?.intOrNull
                ?: tracks.indexOfFirst {
                    it.jsonObject["instrumentId"]?.jsonPrimitive?.intOrNull == 1024
                }.takeIf { it >= 0 }
                ?: return@withContext FetchResult.NoDrumTrack

            if (drumIndex !in tracks.indices) {
                return@withContext FetchResult.ScrapeFailure("popularTrackDrum $drumIndex out of range")
            }
            val drumTrack = tracks[drumIndex].jsonObject
            val drumHash = drumTrack["hash"]?.jsonPrimitive?.content
                ?: return@withContext FetchResult.ScrapeFailure("missing hash on drum track")
            val partId = drumTrack["partId"]?.jsonPrimitive?.intOrNull ?: drumIndex

            // Step 2: track-data on the CDN
            val trackBody = try {
                getOrNull("$cdnBaseUrl/$songId/$revisionId/$image/$partId.json")
            } catch (e: IOException) {
                Log.w(TAG, "track-data IOException", e)
                return@withContext FetchResult.NetworkError
            } ?: return@withContext FetchResult.ScrapeFailure("track-data HTTP error")

            val root = try {
                json.parseToJsonElement(trackBody).jsonObject
            } catch (e: Exception) {
                return@withContext FetchResult.ScrapeFailure("track-data parse: ${e.message}")
            }

            FetchResult.Success(RevisionJson(songId, revisionId, drumHash, root))
        }

    /** Returns body string on 2xx, null on non-2xx. Throws IOException on connection failure. */
    private fun getOrNull(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "DrumsApp/0.1 (Android)")
            .header("Referer", "https://www.songsterr.com/")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "GET $url → HTTP ${resp.code}")
                null
            } else resp.body?.string()
        }
    }
}
