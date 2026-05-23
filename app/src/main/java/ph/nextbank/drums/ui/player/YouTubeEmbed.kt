package ph.nextbank.drums.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
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

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val opts = IFramePlayerOptions.Builder()
                .controls(0)
                .fullscreen(0)
                .autoplay(0)
                .build()
            YouTubePlayerView(ctx).apply {
                enableAutomaticInitialization = false
                val adapter = YouTubePlayerAdapter()
                initialize(object : AbstractYouTubePlayerListener() {
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
                }, opts)
                onAdapterReady(adapter)
                view.value = this
            }
        },
    )

    DisposableEffect(lifecycleOwner, view) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) view.value?.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.value?.release()
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
