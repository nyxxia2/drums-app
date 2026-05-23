package ph.nextbank.drums.ui.player

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ph.nextbank.drums.audio.playback.YouTubeAdapter
import ph.nextbank.drums.audio.playback.YouTubeAdapterListener

private const val TAG = "DrumsYT"

/**
 * YouTube player embed implemented as a raw WebView loading YouTube's IFrame API.
 *
 * Previous attempts using the `androidyoutubeplayer` lib (v12.1.0–12.1.2) reliably
 * fired `onError(UNKNOWN)` ~2 ms after `onReady` on this device's WebView, regardless
 * of cueVideo/loadVideo/initialization style. That lib creates a "blank" YT.Player
 * and then calls cueVideoById, which is what was failing.
 *
 * This implementation constructs the `YT.Player` with the `videoId` already in the
 * constructor — no separate cue/load call — and uses a `@JavascriptInterface` bridge
 * to forward player events back to Kotlin via [YouTubeAdapter].
 */
@Composable
fun YouTubeEmbed(
    videoId: String,
    modifier: Modifier = Modifier,
    onAdapterReady: (YouTubeAdapter) -> Unit,
) {
    val adapter = remember(videoId) { WebViewYouTubeAdapter() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Log.d(TAG, "factory: creating WebView for videoId=$videoId")
            @SuppressLint("SetJavaScriptEnabled")
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webChromeClient = WebChromeClient()
                setBackgroundColor(0x00000000)

                adapter.bind(this)
                addJavascriptInterface(JsBridge(adapter), "AndroidBridge")

                val html = buildHtml(videoId)
                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )

    DisposableEffect(adapter) {
        onAdapterReady(adapter)
        onDispose {
            adapter.destroy()
        }
    }
}

private fun buildHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  html, body { margin: 0; padding: 0; background: #000; overflow: hidden; height: 100%; }
  #player { width: 100%; height: 100%; }
</style>
</head>
<body>
  <div id="player"></div>
  <script>
    var player;
    var tag = document.createElement('script');
    tag.src = "https://www.youtube.com/iframe_api";
    document.head.appendChild(tag);

    function onYouTubeIframeAPIReady() {
      player = new YT.Player('player', {
        videoId: '$videoId',
        playerVars: {
          autoplay: 0,
          controls: 1,
          playsinline: 1,
          rel: 0,
          modestbranding: 1
        },
        events: {
          onReady: function(e) {
            AndroidBridge.log('YT onReady');
            AndroidBridge.onReady();
            startPolling();
          },
          onStateChange: function(e) {
            AndroidBridge.log('YT onStateChange: ' + e.data);
            AndroidBridge.onStateChange(e.data);
          },
          onError: function(e) {
            AndroidBridge.log('YT onError: ' + e.data);
            AndroidBridge.onError(String(e.data));
          }
        }
      });
    }

    var pollHandle;
    function startPolling() {
      pollHandle = setInterval(function() {
        try {
          var t = player.getCurrentTime();
          if (typeof t === 'number' && !isNaN(t)) AndroidBridge.onCurrentSecond(t);
        } catch (e) { }
      }, 100);
    }

    function ytPlay() { try { player.playVideo(); } catch (e) { } }
    function ytPause() { try { player.pauseVideo(); } catch (e) { } }
    function ytSeek(s) { try { player.seekTo(parseFloat(s), true); } catch (e) { } }
  </script>
</body>
</html>
""".trimIndent()

/** Bridge between the WebView's JavaScript and our YouTubeAdapter. */
private class JsBridge(private val adapter: WebViewYouTubeAdapter) {
    @JavascriptInterface
    fun log(message: String) {
        Log.d(TAG, "[JS] $message")
    }

    @JavascriptInterface
    fun onReady() {
        adapter.dispatchReady()
    }

    @JavascriptInterface
    fun onStateChange(state: Int) {
        // YT player state codes: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued
        when (state) {
            1 -> adapter.dispatchPlay()
            2 -> adapter.dispatchPause()
            0 -> adapter.dispatchEnded()
        }
    }

    @JavascriptInterface
    fun onError(code: String) {
        adapter.dispatchError(code)
    }

    @JavascriptInterface
    fun onCurrentSecond(seconds: Double) {
        adapter.dispatchCurrentSecond(seconds.toFloat())
    }
}

/**
 * [YouTubeAdapter] backed by a raw WebView running YouTube's IFrame Player API.
 * Forwards events from JS to the listener on the main thread.
 */
class WebViewYouTubeAdapter : YouTubeAdapter {
    private var listener: YouTubeAdapterListener? = null
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun bind(w: WebView) { webView = w }

    fun destroy() {
        mainHandler.post {
            webView?.let {
                it.loadUrl("about:blank")
                it.removeJavascriptInterface("AndroidBridge")
                it.destroy()
            }
            webView = null
        }
    }

    fun dispatchReady() = onMain { listener?.onReady() }
    fun dispatchPlay() = onMain { listener?.onPlay() }
    fun dispatchPause() = onMain { listener?.onPause() }
    fun dispatchEnded() = onMain { listener?.onEnded() }
    fun dispatchError(msg: String) = onMain { listener?.onError(msg) }
    fun dispatchCurrentSecond(s: Float) = onMain { listener?.onCurrentSecond(s) }

    override fun play() = runJs("ytPlay()")
    override fun pause() = runJs("ytPause()")
    override fun stop() = runJs("ytPause(); ytSeek(0);")
    override fun seekToSeconds(seconds: Float) = runJs("ytSeek($seconds)")
    override fun setListener(listener: YouTubeAdapterListener) { this.listener = listener }

    private fun runJs(code: String) {
        onMain { webView?.evaluateJavascript(code, null) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }
}
