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
        assertEquals(0.5f, c.currentSlot(nowMs = 62L + 1L), 0.05f)
    }

    @Test fun pause_freezes_slot_resume_continues() {
        val c = clock(bpm = 120)
        c.play(nowMs = 0L)
        c.pause(nowMs = 250L)
        assertEquals(2f, c.currentSlot(nowMs = 1_000L), 0.0001f)
        c.play(nowMs = 1_000L)
        assertEquals(3f, c.currentSlot(nowMs = 1_125L), 0.0001f)
    }

    @Test fun stop_resets_slot_and_pauses() {
        val c = clock(bpm = 120)
        c.play(nowMs = 0L)
        c.stop()
        assertFalse(c.isPlaying)
        assertEquals(0f, c.currentSlot(nowMs = 9_999L), 0.0001f)
    }

    @Test fun without_looping_slot_clamps_at_end_of_song() {
        val c = clock(bpm = 240, totalSlots = 16, slotsPerBeat = 4) // 62.5ms per slot
        c.play(nowMs = 0L)
        // After 16 slots of playback we should be parked at the end, not wrapped to 0.
        val end = c.currentSlot(nowMs = 1_000L)
        assertTrue("expected clamp to ~16, got $end", end >= 15.99f && end < 16f)
        // Time keeps passing, position stays clamped.
        assertEquals(end, c.currentSlot(nowMs = 5_000L), 0.001f)
    }

    @Test fun looping_wraps_when_reaching_totalSlots() {
        val c = clock(bpm = 240, totalSlots = 16, slotsPerBeat = 4)
        c.looping = true
        c.play(nowMs = 0L)
        assertEquals(0f, c.currentSlot(nowMs = 1_000L), 0.01f) // 16 slots → wrap to 0
        assertEquals(1f, c.currentSlot(nowMs = 1_062L + 1L), 0.05f)
    }

    @Test fun isFinished_reports_end_of_song_when_not_looping() {
        val c = clock(bpm = 240, totalSlots = 16, slotsPerBeat = 4)
        c.play(nowMs = 0L)
        assertFalse(c.isFinished(nowMs = 500L))
        assertTrue(c.isFinished(nowMs = 1_000L))
    }

    @Test fun isFinished_is_false_when_looping() {
        val c = clock(bpm = 240, totalSlots = 16, slotsPerBeat = 4)
        c.looping = true
        c.play(nowMs = 0L)
        assertFalse(c.isFinished(nowMs = 5_000L))
    }

    @Test fun replay_after_end_rewinds_to_slot_0() {
        val c = clock(bpm = 240, totalSlots = 16, slotsPerBeat = 4)
        c.play(nowMs = 0L)
        c.pause(nowMs = 2_000L) // way past end
        // Pressing play again should rewind.
        c.play(nowMs = 3_000L)
        assertEquals(0f, c.currentSlot(nowMs = 3_000L), 0.0001f)
    }

    @Test fun slotsJustEntered_fires_each_integer_slot_once() {
        val c = clock(bpm = 120)
        c.play(nowMs = 0L)
        val entered = mutableListOf<Int>()
        for (t in 0..500 step 10) entered += c.slotsJustEntered(nowMs = t.toLong())
        assertEquals(listOf(0, 1, 2, 3, 4), entered)
    }

    @Test fun slotsJustEntered_returns_all_skipped_slots_when_frame_jitters() {
        val c = clock(bpm = 120) // 125ms per slot
        c.play(nowMs = 0L)
        assertEquals(listOf(0), c.slotsJustEntered(nowMs = 0L))
        // Big jank: next frame at t=500ms → slot 4. Should fire 1, 2, 3, 4 — not just 4.
        assertEquals(listOf(1, 2, 3, 4), c.slotsJustEntered(nowMs = 500L))
    }

    @Test fun slotsJustEntered_handles_loop_wrap() {
        val c = clock(bpm = 240, totalSlots = 16, slotsPerBeat = 4) // 62.5ms per slot
        c.looping = true
        c.play(nowMs = 0L)
        c.slotsJustEntered(nowMs = 0L) // primes lastReported to 0
        val out = c.slotsJustEntered(nowMs = 1_125L)
        // Tail of cycle 1 (slots 1..15) then cycle 2's 0, 1, 2.
        assertEquals((1..15).toList() + listOf(0, 1, 2), out)
    }
}
