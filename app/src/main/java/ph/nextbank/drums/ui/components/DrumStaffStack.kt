package ph.nextbank.drums.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.data.model.DrumToken

/**
 * Renders a full song as a vertical stack of drum-staff lines, [barsPerLine] bars
 * per line. The current line is alpha 1.0; others fade to 0.55. Only the current
 * line shows the playhead.
 */
@Composable
fun DrumStaffStack(
    bars: List<List<List<DrumToken>>>,
    currentSlot: Float,
    timeSig: Pair<Int, Int> = 4 to 4,
    barsPerLine: Int = 2,
    widthDp: Int = 340,
    lineHeightDp: Int = 140,
    modifier: Modifier = Modifier,
) {
    val slotsPerBar = bars.firstOrNull()?.size ?: 16
    val totalBars = bars.size
    val lineCount = (totalBars + barsPerLine - 1) / barsPerLine
    val currentBar = (currentSlot / slotsPerBar).toInt()
    val currentLine = currentBar / barsPerLine

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (li in 0 until lineCount) {
            val startBar = li * barsPerLine
            val lineBars = bars.subList(startBar, minOf(startBar + barsPerLine, totalBars))
            val isCurrent = li == currentLine
            val targetAlpha = if (isCurrent) 1f else 0.55f
            val alpha by animateFloatAsState(targetAlpha, tween(250), label = "lineAlpha")
            val localSlot = if (isCurrent) currentSlot - startBar * slotsPerBar else -1f
            DrumStaff(
                bars = lineBars,
                currentSlot = localSlot,
                showClef = li == 0,
                showPlayhead = isCurrent,
                timeSig = timeSig,
                widthDp = widthDp,
                heightDp = lineHeightDp,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
}
