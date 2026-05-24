package ph.nextbank.drums.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstantBpmTimeMapTest {

    @Test fun `slotAt at t=0 returns 0`() {
        val tm = ConstantBpmTimeMap(bpm = 120, totalSlots = 64, slotsPerBeat = 4)
        assertEquals(0f, tm.slotAt(0f), 0.001f)
    }

    @Test fun `slotAt at one second at 120 BPM 4 slots-per-beat returns 8`() {
        // 120 BPM = 2 beats per second. 4 slots per beat = 8 slots per second.
        val tm = ConstantBpmTimeMap(bpm = 120, totalSlots = 64, slotsPerBeat = 4)
        assertEquals(8f, tm.slotAt(1f), 0.001f)
    }

    @Test fun `videoSecAt is inverse of slotAt`() {
        val tm = ConstantBpmTimeMap(bpm = 95, totalSlots = 256, slotsPerBeat = 4)
        val sec = 12.345f
        val slot = tm.slotAt(sec)
        assertEquals(sec, tm.videoSecAt(slot), 0.001f)
    }

    @Test fun `totalSlots is returned verbatim`() {
        val tm = ConstantBpmTimeMap(bpm = 100, totalSlots = 999, slotsPerBeat = 4)
        assertEquals(999, tm.totalSlots)
    }
}
