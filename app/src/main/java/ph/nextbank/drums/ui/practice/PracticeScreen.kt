package ph.nextbank.drums.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.ui.components.DrumStaff
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun PracticeScreen(songId: String, onBack: () -> Unit, vm: PracticeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val song = state.song ?: return

    Column(
        Modifier.fillMaxSize().background(DrumsColors.Bg).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp).clip(CircleShape).border(1.dp, DrumsColors.Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DrumsColors.Text) }
            Spacer(Modifier.width(12.dp))
            Text("Speed ramp", color = DrumsColors.Text, style = DrumsType.sectionTitle)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CURRENT TEMPO", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("${state.currentBpm}", color = DrumsColors.Accent, style = DrumsType.displayBpm)
            Text(
                "BPM · LOOP ${state.currentLoop} OF ${state.loops}",
                color = DrumsColors.Dim, style = DrumsType.allCapsLabel,
            )
            Spacer(Modifier.height(12.dp))
            RangeBar(state.startBpm, state.currentBpm, state.targetBpm)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
        ) {
            Text("LOOPING BARS 1–2", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Spacer(Modifier.height(8.dp))
            DrumStaff(bars = song.bars.take(2), currentSlot = 6f)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 1..state.loops) {
                val fill = when {
                    i < state.currentLoop -> DrumsColors.Accent.copy(alpha = 0.6f)
                    i == state.currentLoop -> DrumsColors.Accent
                    else -> DrumsColors.BarTrack
                }
                Box(
                    Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(fill),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp)),
        ) {
            ConfigRow("Start tempo", "${state.startBpm} BPM")
            ConfigRow("Target tempo", "${state.targetBpm} BPM")
            ConfigRow("Loops", "${state.loops}")
            ConfigRow("Step", "+${state.stepBpm} BPM / loop", isLast = true)
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Accent)
                .clickable { /* hook up later */ }
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Resume from loop ${state.currentLoop}", color = Color.White, style = DrumsType.buttonLabel)
        }
    }
}

@Composable
private fun RangeBar(start: Int, current: Int, target: Int) {
    val pct = (current - start).coerceAtLeast(0).toFloat() / (target - start).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$start", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("$current", color = DrumsColors.Accent, style = DrumsType.allCapsLabel)
            Text("$target", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(DrumsColors.BarTrack),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(pct).background(DrumsColors.Accent))
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String, isLast: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp, horizontal = 16.dp)
            .let { if (!isLast) it.drawBottomBorder() else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DrumsColors.Text, style = DrumsType.rowTitle, modifier = Modifier.weight(1f))
        Text(value, color = DrumsColors.Accent, style = DrumsType.cardTitle)
    }
}

private fun Modifier.drawBottomBorder(): Modifier = drawBehind {
    drawLine(
        color = DrumsColors.Line,
        start = Offset(0f, size.height - 0.5f),
        end = Offset(size.width, size.height - 0.5f),
        strokeWidth = 1f,
    )
}
