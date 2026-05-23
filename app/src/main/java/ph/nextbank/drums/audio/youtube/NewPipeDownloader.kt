package ph.nextbank.drums.audio.youtube

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import java.util.concurrent.TimeUnit

class NewPipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NpRequest): NpResponse {
        val builder = Request.Builder().url(request.url())
        request.headers().forEach { (k, vs) ->
            vs.forEach { v -> builder.addHeader(k, v) }
        }
        val data = request.dataToSend()
        val body = data?.toRequestBody()
        val httpReq = when (request.httpMethod().uppercase()) {
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody()).build()
            "PUT" -> builder.put(body ?: ByteArray(0).toRequestBody()).build()
            "HEAD" -> builder.head().build()
            else -> builder.get().build()
        }
        client.newCall(httpReq).execute().use { resp ->
            val responseHeaders = resp.headers.toMultimap()
            val responseBody = resp.body?.string() ?: ""
            return NpResponse(
                resp.code,
                resp.message,
                responseHeaders,
                responseBody,
                resp.request.url.toString(),
            )
        }
    }
}
