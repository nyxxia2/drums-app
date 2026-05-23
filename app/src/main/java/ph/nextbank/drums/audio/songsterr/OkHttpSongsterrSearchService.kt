package ph.nextbank.drums.audio.songsterr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "DrumsSgs"
private const val DEFAULT_BASE_URL = "https://www.songsterr.com"

class OkHttpSongsterrSearchService(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : SongsterrSearchService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): List<SongsterrResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$baseUrl/api/songs?pattern=".toHttpUrlOrNull()!!
                    .newBuilder()
                    .removeAllQueryParameters("pattern")
                    .addQueryParameter("pattern", query)
                    .build()
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "DrumsApp/0.1 (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "search '$query' returned HTTP ${resp.code}")
                        return@withContext emptyList()
                    }
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    json.decodeFromString<List<SongsterrResult>>(body)
                }
            }.onFailure { Log.w(TAG, "search '$query' failed", it) }
                .getOrElse { emptyList() }
        }
}
