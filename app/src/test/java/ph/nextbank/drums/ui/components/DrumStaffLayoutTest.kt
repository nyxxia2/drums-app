package ph.nextbank.drums.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.nextbank.drums.data.model.DrumToken.*

class DrumStaffLayoutTest {

    private fun layout(width: Float = 300f, showClef: Boolean = true) = DrumStaffLayout(
        width = width, height = 100f, top = 28f, lineGap = 9f,
        showClef = showClef, staffPaddingX = 12f, barCount = 2, slotsPerBar = 16,
    )

    @Test fun staff_has_5_lines_9_apart_starting_at_top_28() {
        val l = layout()
        assertEquals(28f, l.staffTopY, 0.001f)
        assertEquals(28f + 4 * 9f, l.staffBottomY, 0.001f)
        assertEquals(listOf(28f, 37f, 46f, 55f, 64f), l.staffLines)
    }

    @Test fun snare_sits_on_middle_line() {
        val l = layout()
        assertEquals(l.staffLines[2], l.yOf(SNARE), 0.001f)
    }

    @Test fun kick_sits_below_the_bottom_line() {
        val l = layout()
        assertTrue(l.yOf(KICK) > l.staffBottomY)
    }

    @Test fun slot_0_of_bar_0_is_just_inside_the_clef() {
        val l = layout(width = 300f, showClef = true)
        val x = l.slotX(globalSlot = 0)
        // clefW=32, padX=12 → innerX0=44. innerW=300-32-2*12=244. slotW=244/32. center of slot 0 = 44 + 0.5*slotW
        val slotW = (300f - 32f - 2 * 12f) / 32f
        assertEquals(44f + 0.5f * slotW, x, 0.001f)
    }

    @Test fun playhead_x_is_linear_in_currentBeat() {
        val l = layout(width = 300f, showClef = false)
        // showClef=false → clefW=0, innerX0=12, innerW=300-2*12=276
        val phAtStart = l.playheadX(currentBeat = 0f)
        val phAtEnd = l.playheadX(currentBeat = 32f) // end of 2 bars × 16 slots
        assertEquals(12f, phAtStart, 0.001f)
        assertEquals(12f + 276f, phAtEnd, 0.001f)
        // halfway
        assertEquals(12f + 138f, l.playheadX(currentBeat = 16f), 0.001f)
    }

    @Test fun beam_groups_join_adjacent_hihat_8th_notes_within_same_beat() {
        val l = layout()
        // Two adjacent hi-hat hits in the same beat (slots 0 and 2 of bar 0)
        val bar = MutableList(16) { emptyList<ph.nextbank.drums.data.model.DrumToken>() }
        bar[0] = listOf(HIHAT_CLOSED)
        bar[2] = listOf(HIHAT_CLOSED)
        val stems = l.upStems(listOf(bar))
        val beams = l.beamGroups(stems)
        assertEquals(1, beams.size)
        assertEquals(2, beams[0].size)
    }

    @Test fun beam_groups_split_across_beat_boundaries() {
        val l = layout()
        // Hi-hat at slot 2 (still beat 0) and slot 4 (beat 1) — should NOT beam.
        val bar = MutableList(16) { emptyList<ph.nextbank.drums.data.model.DrumToken>() }
        bar[2] = listOf(HIHAT_CLOSED)
        bar[4] = listOf(HIHAT_CLOSED)
        val stems = l.upStems(listOf(bar))
        val beams = l.beamGroups(stems)
        assertEquals(0, beams.size)
    }
}
