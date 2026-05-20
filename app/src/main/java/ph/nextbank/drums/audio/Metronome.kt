package ph.nextbank.drums.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Metronome @Inject constructor(@ApplicationContext ctx: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()

    private val high: Int = runCatching {
        ctx.assets.openFd("samples/click_high.wav").use { pool.load(it, 1) }
    }.getOrDefault(0)

    private val low: Int = runCatching {
        ctx.assets.openFd("samples/click_low.wav").use { pool.load(it, 1) }
    }.getOrDefault(0)

    /** Beat index 0 plays the downbeat (high click). Other beats play the low click. */
    fun click(beatIndex: Int) {
        val id = if (beatIndex == 0) high else low
        if (id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
