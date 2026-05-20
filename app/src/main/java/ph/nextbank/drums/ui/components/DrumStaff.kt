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
 */
@Composable
fun DrumStaff(
    bars: List<List<List<DrumToken>>>,
    currentSlot: Float = 0f,
    showClef: Boolean = true,
    showPlayhead: Boolean = true,
    timeSig: Pair<Int, Int> = 4 to 4,
    widthDp: Int = 320,
    heightDp: Int = 100,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    val barCount = bars.size

    val layout = remember(widthPx, heightPx, showClef, barCount) {
        DrumStaffLayout(
            width = widthPx,
            height = heightPx,
            showClef = showClef,
            barCount = barCount,
        )
    }
    val upStems = remember(bars) { layout.upStems(bars) }
    val downStems = remember(bars) { layout.downStems(bars) }
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
                strokeWidth = 0.9f,
            )
        }

        // percussion clef
        if (showClef) {
            val tx = layout.clefW - 12f
            drawRect(
                color = lineCol,
                topLeft = Offset(tx, layout.staffTopY),
                size = androidx.compose.ui.geometry.Size(4f, layout.staffBottomY - layout.staffTopY),
            )
            drawRect(
                color = lineCol,
                topLeft = Offset(tx + 6f, layout.staffTopY),
                size = androidx.compose.ui.geometry.Size(1.5f, layout.staffBottomY - layout.staffTopY),
            )
            // Time signature is text — drawn via native canvas
            drawIntoCanvas { c ->
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#3A3A44")
                    isAntiAlias = true
                    textSize = 17f
                    typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
                }
                c.nativeCanvas.drawText("${timeSig.first}", layout.clefW + 2f, layout.staffTopY + 16f, p)
                c.nativeCanvas.drawText("${timeSig.second}", layout.clefW + 2f, layout.staffBottomY - 1f, p)
            }
        }

        // bar lines
        for (bi in 0..barCount) {
            val x = layout.innerX0 + bi * layout.barW
            drawLine(lineCol, Offset(x, layout.staffTopY), Offset(x, layout.staffBottomY), 0.9f)
        }

        // stems
        upStems.forEach { s ->
            drawLine(noteCol, Offset(s.x + 4f, s.y1), Offset(s.x + 4f, s.y2), 1.1f)
        }
        downStems.forEach { s ->
            drawLine(noteCol, Offset(s.x - 4f, s.y1), Offset(s.x - 4f, s.y2), 1.1f)
        }

        // beams
        beams.forEach { group ->
            val x1 = group.first().x + 4f
            val x2 = group.last().x + 4f
            val yy = group.minOf { it.y2 }
            drawRect(noteCol, topLeft = Offset(x1, yy), size = androidx.compose.ui.geometry.Size(x2 - x1, 2.5f))
        }

        // noteheads
        bars.forEachIndexed { bi, bar ->
            bar.forEachIndexed { si, slot ->
                if (slot.isEmpty()) return@forEachIndexed
                val cx = layout.slotX(bi * layout.slotsPerBar + si)
                slot.forEach { token ->
                    val cy = layout.yOf(token)
                    when (token) {
                        DrumToken.HIHAT_CLOSED -> drawNoteX(cx, cy, noteCol)
                        DrumToken.HIHAT_OPEN -> drawOpenHat(cx, cy, noteCol)
                        DrumToken.CRASH, DrumToken.RIDE -> drawNoteX(cx, cy, accent)
                        else -> drawNoteOval(cx, cy, noteCol)
                    }
                }
            }
        }

        // playhead
        if (showPlayhead && currentSlot in 0f..layout.slotsTotal.toFloat()) {
            val phX = layout.playheadX(currentSlot)
            drawLine(
                color = playheadCol,
                start = Offset(phX, layout.staffTopY - 22f),
                end = Offset(phX, layout.staffBottomY + 14f),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round,
            )
            drawCircle(playheadCol, radius = 3f, center = Offset(phX, layout.staffTopY - 24f))
        }
    }
}

private fun DrawScope.drawNoteOval(cx: Float, cy: Float, color: Color) {
    rotate(degrees = -22f, pivot = Offset(cx, cy)) {
        drawOval(
            color = color,
            topLeft = Offset(cx - 4.2f, cy - 3.1f),
            size = androidx.compose.ui.geometry.Size(8.4f, 6.2f),
        )
    }
}

private fun DrawScope.drawNoteX(cx: Float, cy: Float, color: Color) {
    drawLine(color, Offset(cx - 4f, cy - 4f), Offset(cx + 4f, cy + 4f), 1.6f, cap = StrokeCap.Round)
    drawLine(color, Offset(cx - 4f, cy + 4f), Offset(cx + 4f, cy - 4f), 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawOpenHat(cx: Float, cy: Float, color: Color) {
    drawNoteX(cx, cy, color)
    drawCircle(color, radius = 2.8f, center = Offset(cx, cy - 8f), style = Stroke(width = 1.6f))
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF131318)
@Composable
private fun DrumStaffPreview() {
    val bars = ph.nextbank.drums.data.samples.SAMPLE_SONGS[0].bars.take(2)
    ph.nextbank.drums.ui.theme.DrumsTheme {
        DrumStaff(bars = bars, currentSlot = 11f, widthDp = 340, heightDp = 110)
    }
}
