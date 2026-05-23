package ph.nextbank.drums.audio.playback

/**
 * Creates [YouTubeAdapter] instances from a directly-playable audio stream URL.
 * Production binds this to an ExoPlayer-backed adapter; tests bind it to a fake.
 */
interface YouTubeAdapterFactory {
    fun create(streamUrl: String): YouTubeAdapter
}
