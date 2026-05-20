package ph.nextbank.drums.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ph.nextbank.drums.data.model.DrumToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrumSampleBank @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: MutableMap<DrumToken, Int> = mutableMapOf()
    private val loaded: MutableSet<Int> = mutableSetOf()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
            else Log.w(TAG, "SoundPool load failed for sample $sampleId (status=$status)")
        }
        load()
    }

    private fun load() {
        DrumToken.values().forEach { tok ->
            val asset = assetFor(tok) ?: return@forEach
            try {
                val afd = ctx.assets.openFd("samples/$asset")
                val id = pool.load(afd, /* priority = */ 1)
                ids[tok] = id
                afd.close()
            } catch (e: Exception) {
                Log.w(TAG, "Missing sample $asset for $tok: ${e.message}")
            }
        }
    }

    fun play(token: DrumToken, volume: Float = 1f) {
        val id = ids[token] ?: return
        if (id !in loaded) return
        pool.play(id, volume, volume, /* priority = */ 1, /* loop = */ 0, /* rate = */ 1f)
    }

    fun release() {
        pool.release()
    }

    private fun assetFor(t: DrumToken): String? = when (t) {
        DrumToken.KICK -> "kick.wav"
        DrumToken.SNARE -> "snare.wav"
        DrumToken.HIHAT_CLOSED -> "hihat_closed.wav"
        DrumToken.HIHAT_OPEN -> "hihat_open.wav"
        DrumToken.CRASH -> "crash.wav"
        DrumToken.RIDE -> "ride.wav"
        DrumToken.TOM_HI -> "tom_hi.wav"
        DrumToken.TOM_MID -> "tom_mid.wav"
        DrumToken.TOM_FLOOR -> "tom_floor.wav"
    }

    companion object { private const val TAG = "DrumSampleBank" }
}
