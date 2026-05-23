package ph.nextbank.drums.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ph.nextbank.drums.audio.playback.YouTubeAdapter
import ph.nextbank.drums.audio.playback.YouTubeAdapterListener

private const val TAG = "DrumsYT"

/**
 * [YouTubeAdapter] backed by ExoPlayer playing a directly-extracted YouTube audio
 * stream URL (from NewPipe Extractor).
 *
 * No WebView, no iframe player, no embed restrictions — just plays the raw audio.
 * This replaces the previous WebView-based adapter which kept hitting error 152 on
 * this device's network/region.
 */
class ExoYouTubeAdapter(
    context: Context,
    private val streamUrl: String,
) : YouTubeAdapter {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var listener: YouTubeAdapterListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null
    private var notifiedReady = false

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Log.d(TAG, "Exo onPlaybackStateChanged: $state")
                when (state) {
                    Player.STATE_READY -> {
                        if (!notifiedReady) {
                            notifiedReady = true
                            listener?.onReady()
                        }
                        startPositionUpdates()
                    }
                    Player.STATE_ENDED -> {
                        listener?.onEnded()
                        stopPositionUpdates()
                    }
                    Player.STATE_BUFFERING, Player.STATE_IDLE -> { /* ignore */ }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "Exo onIsPlayingChanged: $isPlaying")
                if (isPlaying) listener?.onPlay() else listener?.onPause()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Exo onPlayerError: ${error.errorCodeName} (${error.message})")
                listener?.onError(error.errorCodeName)
            }
        })
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
    }

    private fun startPositionUpdates() {
        if (positionJob != null) return
        positionJob = scope.launch {
            while (true) {
                val posMs = player.currentPosition
                if (posMs >= 0) {
                    listener?.onCurrentSecond(posMs / 1000f)
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    override fun play() = onMain { player.play() }
    override fun pause() = onMain { player.pause() }
    override fun stop() = onMain {
        player.pause()
        player.seekTo(0)
    }
    override fun seekToSeconds(seconds: Float) = onMain {
        player.seekTo((seconds * 1000).toLong())
    }
    override fun setListener(listener: YouTubeAdapterListener) { this.listener = listener }

    override fun release() {
        stopPositionUpdates()
        scope.cancel()
        onMain { player.release() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }
}
