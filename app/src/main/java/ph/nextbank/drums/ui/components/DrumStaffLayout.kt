package ph.nextbank.drums.ui.components

import ph.nextbank.drums.data.model.DrumToken

data class UpStem(val x: Float, val y1: Float, val y2: Float, val barIndex: Int, val slotIndex: Int)
data class DownStem(val x: Float, val y1: Float, val y2: Float)

class DrumStaffLayout(
    val width: Float,
    val height: Float,
    val top: Float = 40f,
    val lineGap: Float = 14f,
    val showClef: Boolean,
    val staffPaddingX: Float = 12f,
    val barCount: Int,
    val slotsPerBar: Int = 16,
    val slotsPerBeat: Int = 4,
) {
    val clefW: Float = if (showClef) 32f else 0f
    val innerX0: Float = clefW + staffPaddingX
    val innerX1: Float = width - staffPaddingX
    val innerW: Float = innerX1 - innerX0
    val slotsTotal: Int = slotsPerBar * barCount
    val slotW: Float = innerW / slotsTotal
    val barW: Float = slotW * slotsPerBar

    val staffLines: List<Float> = (0..4).map { top + it * lineGap }
    val staffTopY: Float = staffLines.first()
    val staffBottomY: Float = staffLines.last()

    fun slotX(globalSlot: Int): Float = innerX0 + (globalSlot + 0.5f) * slotW

    fun playheadX(currentBeat: Float): Float =
        innerX0 + (currentBeat / slotsTotal) * innerW

    fun yOf(token: DrumToken): Float = when (token) {
        DrumToken.HIHAT_CLOSED -> staffTopY - lineGap * 1.33f
        DrumToken.HIHAT_OPEN -> staffTopY - lineGap * 1.33f
        DrumToken.CRASH -> staffTopY - lineGap * 2.22f
        DrumToken.RIDE -> staffTopY - lineGap * 1.78f
        DrumToken.TOM_HI -> staffLines[1] - lineGap / 2f
        DrumToken.TOM_MID -> staffLines[2] - lineGap / 2f
        DrumToken.SNARE -> staffLines[2]
        DrumToken.TOM_FLOOR -> staffLines[3] + lineGap / 2f
        DrumToken.KICK -> staffLines[4] + lineGap
    }

    /** Stems pointing up from top-row hits (cymbals & hi-hat) or from snare/toms without kick. */
    fun upStems(bars: List<List<List<DrumToken>>>, stemLen: Float = 22f): List<UpStem> {
        val out = mutableListOf<UpStem>()
        bars.forEachIndexed { bi, bar ->
            bar.forEachIndexed { si, slot ->
                if (slot.isEmpty()) return@forEachIndexed
                val globalSlot = bi * slotsPerBar + si
                val x = slotX(globalSlot)
                val hasTop = slot.any { it in TOP_ROW }
                val hasMid = slot.any { it in MID_ROW }
                val hasKick = DrumToken.KICK in slot
                if (hasTop) {
                    val topY = slot.filter { it in TOP_ROW }.minOf(::yOf)
                    out += UpStem(x, topY, topY - stemLen * 0.73f, bi, si)
                } else if (hasMid && !hasKick) {
                    val midY = if (DrumToken.SNARE in slot) yOf(DrumToken.SNARE)
                               else yOf(slot.first { it in MID_ROW })
                    out += UpStem(x, midY, midY - stemLen, bi, si)
                }
            }
        }
        return out
    }

    fun downStems(bars: List<List<List<DrumToken>>>, stemLen: Float = 16f): List<DownStem> {
        val out = mutableListOf<DownStem>()
        bars.forEachIndexed { bi, bar ->
            bar.forEachIndexed { si, slot ->
                if (DrumToken.KICK !in slot) return@forEachIndexed
                val x = slotX(bi * slotsPerBar + si)
                out += DownStem(x, yOf(DrumToken.KICK), yOf(DrumToken.KICK) + stemLen)
            }
        }
        return out
    }

    /** Adjacent up-stems within the same beat (group of 4 slots) get beamed. */
    fun beamGroups(stems: List<UpStem>): List<List<UpStem>> {
        val groups = mutableListOf<List<UpStem>>()
        var current = mutableListOf<UpStem>()
        stems.forEach { s ->
            if (current.isEmpty()) current += s
            else {
                val last = current.last()
                val sameBar = s.barIndex == last.barIndex
                val sameBeat = last.slotIndex / slotsPerBeat == s.slotIndex / slotsPerBeat
                val adjacent = s.slotIndex - last.slotIndex <= 2
                if (sameBar && sameBeat && adjacent) current += s
                else {
                    if (current.size > 1) groups += current.toList()
                    current = mutableListOf(s)
                }
            }
        }
        if (current.size > 1) groups += current.toList()
        return groups
    }

    companion object {
        private val TOP_ROW = setOf(
            DrumToken.HIHAT_CLOSED, DrumToken.HIHAT_OPEN, DrumToken.CRASH, DrumToken.RIDE
        )
        private val MID_ROW = setOf(
            DrumToken.SNARE, DrumToken.TOM_HI, DrumToken.TOM_MID, DrumToken.TOM_FLOOR
        )

        /**
         * Compose's bit-packed Constraints can't represent dimensions above ~262k px. With the
         * preferred 380 dp/bar and a 480 dpi device (3 px/dp), that limit is hit at ~229 bars.
         * Long songs like Metallica's Master of Puppets blow past it and crash measurement.
         * Cap the total staff width and scale `barWidthDp` down proportionally so the staff
         * still lays out, with a floor so notes don't collapse to zero width.
         */
        const val MAX_TOTAL_STAFF_WIDTH_DP = 50_000
        const val MIN_BAR_WIDTH_DP = 40

        fun computeBarWidthDp(barCount: Int, preferredBarWidthDp: Int = 380): Int {
            if (barCount <= 0) return preferredBarWidthDp
            val unscaled = preferredBarWidthDp.toLong() * barCount
            if (unscaled <= MAX_TOTAL_STAFF_WIDTH_DP) return preferredBarWidthDp
            return (MAX_TOTAL_STAFF_WIDTH_DP / barCount).coerceAtLeast(MIN_BAR_WIDTH_DP)
        }
    }
}
