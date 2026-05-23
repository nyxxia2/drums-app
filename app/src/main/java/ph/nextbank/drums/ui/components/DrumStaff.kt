package ph.nextbank.drums.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.ui.theme.DrumsColors

/**
 * Renders one or more drum-staff bars in a single horizontal row, with optional
 * percussion clef, time signature, and a violet playhead at `currentSlot`.
 *
 * Layout math lives in [DrumStaffLayout]; this Composable is the painter.
 * Staff geometry and note size scale with `heightDp` so a 280dp staff doesn't
 * end up with the same thin lines as a 100dp one.
 */
@Composable
fun DrumStaff(
    bars: List<List<List<DrumToken>>>,
    currentSlot: Float = 0f,
    showClef: Boolean = true,
    showPlayhead: Boolean = true,
    timeSig: Pair<Int, Int> = 4 to 4,
    widthDp: Int = 320,
    heightDp: Int = 140,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    val baseHeightPx = with(density) { 140.dp.toPx() }
    // Visual scale: 1.0 at heightDp=140 (matches the legacy DrumStaffLayout
    // defaults), grows with bigger canvases so notes, stems, and gaps all
    // remain proportional to the staff.
    val s = heightPx / baseHeightPx
    val barCount = bars.size

    val layout = remember(widthPx, heightPx, showClef, barCount, s) {
        DrumStaffLayout(
            width = widthPx,
            height = heightPx,
            top = 40f * s,
            lineGap = 14f * s,
            showClef = showClef,
            barCount = barCount,
        )
    }
    val upStems = remember(bars, s) { layout.upStems(bars, stemLen = 22f * s) }
    val downStems = remember(bars, s) { layout.downStems(bars, stemLen = 16f * s) }
    val beams = remember(upStems) { layout.beamGroups(upStems) }

    Canvas(modifier = modifier.size(widthDp.dp, heightDp.dp)) {
        val lineCol = DrumsColors.StaffLine
        val noteCol = DrumsColors.Note
        val accent = DrumsColors.Accent
        val playheadCol = DrumsColors.Playhead

        // staff lines
        layout.staffLines.forEach { yy ->
            drawLine(
                color = lineCol,
                start = Offset(layout.clefW, yy),
                end = Offset(widthPx, yy),
                strokeWidth = 1.0f * s,
            )
        }

        // percussion clef
        if (showClef) {
            val tx = layout.clefW - 12f * s
            drawRect(
                color = lineCol,
                topLeft = Offset(tx, layout.staffTopY),
                size = androidx.compose.ui.geometry.Size(4f * s, layout.staffBottomY - layout.staffTopY),
            )
            drawRect(
                color = lineCol,
                topLeft = Offset(tx + 6f * s, layout.staffTopY),
                size = androidx.compose.ui.geometry.Size(1.5f * s, layout.staffBottomY - layout.staffTopY),
            )
            drawIntoCanvas { c ->
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#3A3A44")
                    isAntiAlias = true
                    textSize = 22f * s
                    typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
                }
                c.nativeCanvas.drawText("${timeSig.first}", layout.clefW + 2f * s, layout.staffTopY + 20f * s, p)
                c.nativeCanvas.drawText("${timeSig.second}", layout.clefW + 2f * s, layout.staffBottomY - 1f * s, p)
            }
        }

        // bar lines
        for (bi in 0..barCount) {
            val x = layout.innerX0 + bi * layout.barW
            drawLine(lineCol, Offset(x, layout.staffTopY), Offset(x, layout.staffBottomY), 1.0f * s)
        }

        // stems
        upStems.forEach { stem ->
            drawLine(noteCol, Offset(stem.x + 4f * s, stem.y1), Offset(stem.x + 4f * s, stem.y2), 1.4f * s)
        }
        downStems.forEach { stem ->
            drawLine(noteCol, Offset(stem.x - 4f * s, stem.y1), Offset(stem.x - 4f * s, stem.y2), 1.4f * s)
        }

        // beams
        beams.forEach { group ->
            val x1 = group.first().x + 4f * s
            val x2 = group.last().x + 4f * s
            val yy = group.minOf { it.y2 }
            drawRect(noteCol, topLeft = Offset(x1, yy), size = androidx.compose.ui.geometry.Size(x2 - x1, 3f * s))
        }

        // noteheads
        bars.forEachIndexed { bi, bar ->
            bar.forEachIndexed { si, slot ->
                if (slot.isEmpty()) return@forEachIndexed
                val cx = layout.slotX(bi * layout.slotsPerBar + si)
                slot.forEach { token ->
                    val cy = layout.yOf(token)
                    when (token) {
                        DrumToken.HIHAT_CLOSED -> drawNoteX(cx, cy, noteCol, s)
                        DrumToken.HIHAT_OPEN -> drawOpenHat(cx, cy, noteCol, s)
                        DrumToken.CRASH, DrumToken.RIDE -> drawNoteX(cx, cy, accent, s)
                        else -> drawNoteOval(cx, cy, noteCol, s)
                    }
                }
            }
        }

        // playhead
        if (showPlayhead && currentSlot in 0f..layout.slotsTotal.toFloat()) {
            val phX = layout.playheadX(currentSlot)
            drawLine(
                color = playheadCol,
                start = Offset(phX, layout.staffTopY - 22f * s),
                end = Offset(phX, layout.staffBottomY + 14f * s),
                strokeWidth = 2.8f * s,
                cap = StrokeCap.Round,
            )
            drawCircle(playheadCol, radius = 4f * s, center = Offset(phX, layout.staffTopY - 24f * s))
        }
    }
}

private fun DrawScope.drawNoteOval(cx: Float, cy: Float, color: Color, s: Float) {
    rotate(degrees = -22f, pivot = Offset(cx, cy)) {
        drawOval(
            color = color,
            topLeft = Offset(cx - 5.2f * s, cy - 3.8f * s),
            size = androidx.compose.ui.geometry.Size(10.4f * s, 7.6f * s),
        )
    }
}

private fun DrawScope.drawNoteX(cx: Float, cy: Float, color: Color, s: Float) {
    drawLine(color, Offset(cx - 5f * s, cy - 5f * s), Offset(cx + 5f * s, cy + 5f * s), 2.0f * s, cap = StrokeCap.Round)
    drawLine(color, Offset(cx - 5f * s, cy + 5f * s), Offset(cx + 5f * s, cy - 5f * s), 2.0f * s, cap = StrokeCap.Round)
}

private fun DrawScope.drawOpenHat(cx: Float, cy: Float, color: Color, s: Float) {
    drawNoteX(cx, cy, color, s)
    drawCircle(color, radius = 3.5f * s, center = Offset(cx, cy - 10f * s), style = Stroke(width = 2.0f * s))
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF131318)
@Composable
private fun DrumStaffPreview() {
    val e: List<DrumToken> = emptyList()
    fun hit(vararg t: DrumToken): List<DrumToken> = t.toList()
    val bars = listOf(
        listOf(
            hit(DrumToken.KICK, DrumToken.CRASH), e, hit(DrumToken.HIHAT_CLOSED), e,
            hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
            hit(DrumToken.KICK, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.KICK, DrumToken.HIHAT_CLOSED), e,
            hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
        ),
        listOf(
            hit(DrumToken.KICK, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
            hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
            hit(DrumToken.KICK, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.HIHAT_CLOSED), e,
            hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e, hit(DrumToken.SNARE, DrumToken.HIHAT_CLOSED), e,
        ),
    )
    ph.nextbank.drums.ui.theme.DrumsTheme {
        DrumStaff(bars = bars, currentSlot = 11f, widthDp = 340, heightDp = 110)
    }
}
