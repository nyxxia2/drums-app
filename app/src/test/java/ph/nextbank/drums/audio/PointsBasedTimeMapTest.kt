package ph.nextbank.drums.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PointsBasedTimeMapTest {

    private fun map(
        points: List<Double>,
        slotsPerBar: Int = 16,
    ): PointsBasedTimeMap = PointsBasedTimeMap(
        points = points,
        slotsPerBar = slotsPerBar,
        totalSlots = (points.size) * slotsPerBar,
    )

    @Test fun `slot at exactly points i equals i times slotsPerBar`() {
        val tm = map(listOf(0.0, 2.0, 4.0, 6.0))
        assertEquals(0f, tm.slotAt(0f), 0.001f)
        assertEquals(16f, tm.slotAt(2f), 0.001f)
        assertEquals(32f, tm.slotAt(4f), 0.001f)
    }

    @Test fun `slot halfway between points is interpolated`() {
        val tm = map(listOf(0.0, 2.0, 4.0))
        // Halfway between points[0] and points[1] → halfway through bar 0 → slot 8.
        assertEquals(8f, tm.slotAt(1f), 0.001f)
        // Halfway between points[1] and points[2] → halfway through bar 1 → slot 24.
        assertEquals(24f, tm.slotAt(3f), 0.001f)
    }

    @Test fun `slot before first point is clamped to 0`() {
        val tm = map(listOf(2.0, 4.0, 6.0))
        assertEquals(0f, tm.slotAt(-1f), 0.001f)
        assertEquals(0f, tm.slotAt(0f), 0.001f)
        assertEquals(0f, tm.slotAt(1.99f), 0.001f)
    }

    @Test fun `slot at or past last point is clamped to totalSlots minus 1`() {
        val tm = map(listOf(0.0, 2.0, 4.0))  // 3 points × 16 slots/bar = 48 totalSlots
        assertEquals(47f, tm.slotAt(4f), 0.001f)
        assertEquals(47f, tm.slotAt(100f), 0.001f)
    }

    @Test fun `negative first point like Teen Spirit's minus point-15 is handled`() {
        val tm = map(listOf(-0.15, 2.5, 4.68))
        // t = 0 is between points[0]=-0.15 and points[1]=2.5.
        // fraction = (0 - (-0.15)) / (2.5 - (-0.15)) = 0.15 / 2.65 ≈ 0.0566
        // slot = (0 + 0.0566) * 16 ≈ 0.906
        assertEquals(0.906f, tm.slotAt(0f), 0.01f)
    }

    @Test fun `videoSecAt is inverse of slotAt within rounding tolerance`() {
        val tm = map(listOf(0.0, 2.0, 4.0, 6.0, 8.0))
        for (sec in listOf(0.5f, 1.0f, 3.7f, 5.25f, 7.9f)) {
            val slot = tm.slotAt(sec)
            assertEquals("sec=$sec round-trip", sec, tm.videoSecAt(slot), 0.001f)
        }
    }

    @Test fun `two-point map handles the single-bar case without crashing`() {
        val tm = PointsBasedTimeMap(points = listOf(0.0, 2.0), slotsPerBar = 16, totalSlots = 16)
        assertEquals(0f, tm.slotAt(0f), 0.001f)
        assertEquals(8f, tm.slotAt(1f), 0.001f)
        assertEquals(15f, tm.slotAt(2f), 0.001f)
    }

    @Test fun `totalSlots is returned verbatim`() {
        val tm = PointsBasedTimeMap(points = listOf(0.0, 1.0), slotsPerBar = 16, totalSlots = 999)
        assertEquals(999, tm.totalSlots)
    }
}
