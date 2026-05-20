package ph.nextbank.drums.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongClockTest {

    private fun clock(bpm: Int = 120, totalSlots: Int = 128, slotsPerBeat: Int = 4) =
        SongClock(bpmProvider = { bpm }, totalSlots = totalSlots, slotsPerBeat = slotsPerBeat)

    @Test fun idle_clock_reports_slot_0_and_not_playing() {
        val c = clock()
        assertFalse(c.isPlaying)
        assertEquals(0f, c.currentSlot(nowMs = 0L), 0.0001f)
    }

    @Test fun play_advances_slot_at_bpm_rate() {
        val c = clock(bpm = 120, slotsPerBeat = 4)
        c.play(nowMs = 0L)
        // 120 BPM → 500 ms per beat → 125 ms per 16th-note slot.
        assertEquals(0f, c.currentSlot(nowMs = 0L), 0.0001f)
        assertEquals(1f, c.currentSlot(nowMs = 125L), 0.0001f)
        assertEquals(4f, c.currentSlot(nowMs = 500L), 0.0001f)
        assertEquals(0.5f, c.currentSlot(nowMs = 62L + 1L), 0.05f) // ~halfway through slot 0
    }

    @Test fun pause_freezes_slot_resume_continues() {
        val c = clock(bpm = 120)
        c.play(nowMs = 0L)
        // advance to slot 2 at t=250
        c.pause(nowMs = 250L)
        // time passing while paused must not advance the slot
        assertEquals(2f, c.currentSlot(nowMs = 1_000L), 0.0001f)
        c.play(nowMs = 1_000L)
        // 125ms later, we're 1 slot beyond pause point
        assertEquals(3f, c.currentSlot(nowMs = 1_125L), 0.0001f)
    }

    @Test fun stop_resets_slot_and_pauses() {
        val c = clock(bpm = 120)
        c.play(nowMs = 0L)
        c.stop()
        assertFalse(c.isPlaying)
        assertEquals(0f, c.currentSlot(nowMs = 9_999L), 0.0001f)
    }

    @Test fun slot_wraps_when_reaching_totalSlots() {
        val c = clock(bpm = 240, totalSlots = 16, slotsPerBeat = 4) // 62.5ms per slot
        c.play(nowMs = 0L)
        // At t=1000ms → 16 slots → wraps to 0
        assertEquals(0f, c.currentSlot(nowMs = 1_000L), 0.01f)
        // At t=1062.5ms → 17 slots → 1
        assertEquals(1f, c.currentSlot(nowMs = 1_062L + 1L), 0.05f)
    }

    @Test fun slotJustEntered_fires_once_per_integer_slot() {
        val c = clock(bpm = 120)
        c.play(nowMs = 0L)
        // poll forward; collect which integer slots were "just entered"
        val entered = mutableListOf<Int>()
        for (t in 0..500 step 10) {
            val justEntered = c.slotJustEntered(nowMs = t.toLong())
            if (justEntered != null) entered += justEntered
        }
        // Expected: slots 0, 1, 2, 3, 4 all entered (within 500ms at 125ms/slot)
        assertEquals(listOf(0, 1, 2, 3, 4), entered)
    }
}
