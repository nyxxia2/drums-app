package ph.nextbank.drums.audio.playback

class FakeYouTubeAdapter : YouTubeAdapter {
    private var _listener: YouTubeAdapterListener? = null
    val playCalls = mutableListOf<Unit>()
    val pauseCalls = mutableListOf<Unit>()
    val stopCalls = mutableListOf<Unit>()
    val seekCalls = mutableListOf<Float>()

    override fun play() { playCalls.add(Unit) }
    override fun pause() { pauseCalls.add(Unit) }
    override fun stop() { stopCalls.add(Unit) }
    override fun seekToSeconds(seconds: Float) { seekCalls.add(seconds) }
    override fun setListener(listener: YouTubeAdapterListener) { _listener = listener }

    fun simulateReady() = _listener!!.onReady()
    fun simulatePlay() = _listener!!.onPlay()
    fun simulatePause() = _listener!!.onPause()
    fun simulateEnded() = _listener!!.onEnded()
    fun simulateError(msg: String) = _listener!!.onError(msg)
    fun simulateSecond(s: Float) = _listener!!.onCurrentSecond(s)
}
