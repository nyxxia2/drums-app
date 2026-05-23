package ph.nextbank.drums.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import ph.nextbank.drums.audio.playback.YouTubeAdapter
import ph.nextbank.drums.audio.playback.YouTubeAdapterListener

@Composable
fun YouTubeEmbed(
    videoId: String,
    modifier: Modifier = Modifier,
    onAdapterReady: (YouTubeAdapter) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(videoId) { mutableHolder<YouTubePlayerView>() }
    val adapter = remember(videoId) { YouTubePlayerAdapter() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // Use the lib's automatic-initialization pattern: register the view as a
            // lifecycle observer and let the lib initialize the iframe API once the
            // view is attached + STARTED. Manual initialize() from inside the
            // AndroidView factory was racing the view attach and yielding UNKNOWN.
            YouTubePlayerView(ctx).apply {
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        adapter.bind(youTubePlayer)
                        adapter.notifyReady()
                        youTubePlayer.cueVideo(videoId, 0f)
                    }
                    override fun onStateChange(
                        youTubePlayer: YouTubePlayer,
                        s: PlayerConstants.PlayerState,
                    ) {
                        when (s) {
                            PlayerConstants.PlayerState.PLAYING -> adapter.notifyPlay()
                            PlayerConstants.PlayerState.PAUSED -> adapter.notifyPause()
                            PlayerConstants.PlayerState.ENDED -> adapter.notifyEnded()
                            else -> { }
                        }
                    }
                    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                        adapter.notifyCurrentSecond(second)
                    }
                    override fun onError(
                        youTubePlayer: YouTubePlayer,
                        error: PlayerConstants.PlayerError,
                    ) {
                        adapter.notifyError(error.name)
                    }
                })
                view.value = this
                onAdapterReady(adapter)
            }
        },
    )

    DisposableEffect(lifecycleOwner, view) {
        // Lib auto-initializes on lifecycle START + attached. Register and let it run.
        val yt = view.value
        yt?.let { lifecycleOwner.lifecycle.addObserver(it) }
        onDispose {
            yt?.let {
                lifecycleOwner.lifecycle.removeObserver(it)
                it.release()
            }
        }
    }
}

private class Holder<T>(var value: T? = null)
private fun <T> mutableHolder() = Holder<T>()

/**
 * Production [YouTubeAdapter] backed by the YouTubePlayer SDK. The actual `YouTubePlayer`
 * reference is bound asynchronously via [bind] once `onReady` fires.
 */
class YouTubePlayerAdapter : YouTubeAdapter {
    private var listener: YouTubeAdapterListener? = null
    private var player: YouTubePlayer? = null

    fun bind(p: YouTubePlayer) { player = p }

    fun notifyReady() { listener?.onReady() }
    fun notifyPlay() { listener?.onPlay() }
    fun notifyPause() { listener?.onPause() }
    fun notifyEnded() { listener?.onEnded() }
    fun notifyError(message: String) { listener?.onError(message) }
    fun notifyCurrentSecond(seconds: Float) { listener?.onCurrentSecond(seconds) }

    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun stop() {
        player?.pause()
        player?.seekTo(0f)
    }
    override fun seekToSeconds(seconds: Float) { player?.seekTo(seconds) }
    override fun setListener(listener: YouTubeAdapterListener) { this.listener = listener }
}
