package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubePlaybackSourceTest {

    @Test
    fun `slot computed from currentSecond using bpm and offset`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            songBpm = 120,
            slotsPerBeat = 4,
            totalSlots = 128,
            adapter = fake,
            initialOffsetMs = 0,
        )
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(1.0f)
        assertEquals(8f, source.currentSlot.first(), 0.01f)
    }

    @Test
    fun `positive offset shifts video earlier — slot is larger at the same currentSecond`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            songBpm = 120,
            slotsPerBeat = 4,
            totalSlots = 128,
            adapter = fake,
            initialOffsetMs = 250,
        )
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(1.0f)
        assertEquals(10f, source.currentSlot.first(), 0.01f)
    }

    @Test
    fun `onReady transitions Idle to Ready`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        assertEquals(PlaybackState.Idle, source.state.first())
        fake.simulateReady()
        assertEquals(PlaybackState.Ready, source.state.first())
    }

    @Test
    fun `play calls adapter play and transitions to Playing on onPlay`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateReady()
        source.play()
        assertEquals(1, fake.playCalls.size)
        fake.simulatePlay()
        assertEquals(PlaybackState.Playing, source.state.first())
    }

    @Test
    fun `onEnded transitions to Finished`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateReady()
        source.play()
        fake.simulatePlay()
        fake.simulateEnded()
        assertEquals(PlaybackState.Finished, source.state.first())
    }

    @Test
    fun `onError transitions to Error`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateError("video unavailable")
        assertEquals(PlaybackState.Error, source.state.first())
    }

    @Test
    fun `nudgeOffset adjusts the slot derivation on next onCurrentSecond`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(1.0f)
        assertEquals(8f, source.currentSlot.first(), 0.01f)
        source.nudgeOffset(125)
        fake.simulateSecond(1.0f)
        assertEquals(9f, source.currentSlot.first(), 0.01f)
    }

    @Test
    fun `activeSlotIndex is currentSlot truncated to int`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(120, 4, 128, fake, 0)
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(1.07f)
        assertEquals(8, source.activeSlotIndex.first())
    }
}
