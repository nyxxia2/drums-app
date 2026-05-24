package ph.nextbank.drums.audio.songsterr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG = "DrumsSgs"
private const val DEFAULT_BASE_URL = "https://www.songsterr.com"

class OkHttpSongsterrVideoPointsService(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : SongsterrVideoPointsService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(songId: Long, revisionId: Long): List<VideoPointEntry> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/video-points/$songId/$revisionId/list"
            val body = try {
                getOrNull(url)
            } catch (e: IOException) {
                Log.w(TAG, "video-points IOException for ($songId, $revisionId)", e)
                return@withContext emptyList()
            } ?: return@withContext emptyList()

            try {
                json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
                    parseEntry(el.jsonObject)
                }
            } catch (e: Exception) {
                Log.w(TAG, "video-points parse error", e)
                emptyList()
            }
        }

    private fun parseEntry(obj: JsonObject): VideoPointEntry? {
        val videoId = obj["videoId"]?.jsonPrimitive?.contentOrNull ?: return null
        val pointsArr = obj["points"]?.jsonArray ?: return null
        val points = pointsArr.mapNotNull { it.jsonPrimitive.doubleOrNull }
        if (points.size < 2) return null  // PointsBasedTimeMap requires at least 2.
        val feature = obj["feature"]?.jsonPrimitive?.contentOrNull
        return VideoPointEntry(youtubeVideoId = videoId, points = points, feature = feature)
    }

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
