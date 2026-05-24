package ph.nextbank.drums.audio.playback

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ph.nextbank.drums.audio.ConstantBpmTimeMap
import ph.nextbank.drums.audio.PointsBasedTimeMap

class YouTubePlaybackSourceTest {

    @Test
    fun `slot computed from currentSecond using bpm and offset`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            timeMap = ConstantBpmTimeMap(bpm = 120, totalSlots = 128, slotsPerBeat = 4),
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
            timeMap = ConstantBpmTimeMap(bpm = 120, totalSlots = 128, slotsPerBeat = 4),
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
        val source = YouTubePlaybackSource(
            timeMap = ConstantBpmTimeMap(bpm = 120, totalSlots = 128, slotsPerBeat = 4),
            adapter = fake,
            initialOffsetMs = 0,
        )
        assertEquals(PlaybackState.Idle, source.state.first())
        fake.simulateReady()
        assertEquals(PlaybackState.Ready, source.state.first())
    }

    @Test
    fun `play calls adapter play and transitions to Playing on onPlay`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            timeMap = ConstantBpmTimeMap(bpm = 120, totalSlots = 128, slotsPerBeat = 4),
            adapter = fake,
            initialOffsetMs = 0,
        )
        fake.simulateReady()
        source.play()
        assertEquals(1, fake.playCalls.size)
        fake.simulatePlay()
        assertEquals(PlaybackState.Playing, source.state.first())
    }

    @Test
    fun `onEnded transitions to Finished`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            timeMap = ConstantBpmTimeMap(bpm = 120, totalSlots = 128, slotsPerBeat = 4),
            adapter = fake,
            initialOffsetMs = 0,
        )
        fake.simulateReady()
        source.play()
        fake.simulatePlay()
        fake.simulateEnded()
        assertEquals(PlaybackState.Finished, source.state.first())
    }

    @Test
    fun `onError transitions to Error`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            timeMap = ConstantBpmTimeMap(bpm = 120, totalSlots = 128, slotsPerBeat = 4),
            adapter = fake,
            initialOffsetMs = 0,
        )
        fake.simulateError("video unavailable")
        assertEquals(PlaybackState.Error, source.state.first())
    }

    @Test
    fun `nudgeOffset adjusts the slot derivation on next onCurrentSecond`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            timeMap = ConstantBpmTimeMap(bpm = 120, totalSlots = 128, slotsPerBeat = 4),
            adapter = fake,
            initialOffsetMs = 0,
        )
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(1.0f)
        assertEquals(8f, source.currentSlot.first(), 0.01f)
        source.nudgeOffset(125)
        fake.simulateSecond(1.0f)
        assertEquals(9f, source.currentSlot.first(), 0.01f)
    }

    @Test
    fun `youtubeOffsetMs applies uniformly before PointsBasedTimeMap`() = runTest {
        // points=[0.0,2.0,4.0,6.0], slotsPerBar=16, totalSlots=64, offsetMs=500
        // At video-time 0.5s with offset 500ms: effectiveSec = 0.5 + 0.5 = 1.0
        // bar 0 spans [0.0, 2.0], fraction = 1.0/2.0 = 0.5 → slot = 0.5 * 16 = 8
        // Equivalently: at video-time 1.0s with no offset, slot is also 8.
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            timeMap = PointsBasedTimeMap(
                points = listOf(0.0, 2.0, 4.0, 6.0),
                slotsPerBar = 16,
                totalSlots = 64,
            ),
            adapter = fake,
            initialOffsetMs = 500,
        )
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(0.5f)
        assertEquals(8f, source.currentSlot.first(), 0.01f)
    }

    @Test
    fun `activeSlotIndex is currentSlot truncated to int`() = runTest {
        val fake = FakeYouTubeAdapter()
        val source = YouTubePlaybackSource(
            timeMap = ConstantBpmTimeMap(bpm = 120, totalSlots = 128, slotsPerBeat = 4),
            adapter = fake,
            initialOffsetMs = 0,
        )
        fake.simulateReady()
        fake.simulatePlay()
        fake.simulateSecond(1.07f)
        assertEquals(8, source.activeSlotIndex.first())
    }
}
