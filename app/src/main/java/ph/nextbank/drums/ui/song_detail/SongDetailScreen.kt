package ph.nextbank.drums.ui.song_detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun SongDetailScreen(
    songId: String,
    onBack: () -> Unit,
    onStartReading: (String) -> Unit,
    onStartPractice: (String) -> Unit,
    vm: SongDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val song = state.song ?: return

    Column(
        Modifier.fillMaxSize().background(DrumsColors.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(DrumsColors.CoverGradFrom, DrumsColors.CoverGradTo),
                        start = Offset(0f, 0f), end = Offset(size.width, size.height),
                    )
                )
                for (i in -10..30) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(i * 40f, 0f),
                        end = Offset(i * 40f + size.height, size.height),
                        strokeWidth = 2f,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White) }

            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(
                    "${song.totalBars} BARS · ${song.timeSig.first}/${song.timeSig.second}",
                    color = Color.White.copy(alpha = 0.85f), style = DrumsType.allCapsLabel,
                )
                Text(song.title, color = Color.White, style = DrumsType.coverTitle)
                Text(song.artist, color = Color.White.copy(alpha = 0.9f), style = DrumsType.body)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DrumsColors.Accent)
                    .clickable { onStartReading(song.id) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Start reading", color = Color.White, style = DrumsType.buttonLabel)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                    .clickable { onStartPractice(song.id) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Speed, contentDescription = "Practice", tint = DrumsColors.Text) }
        }

        Section("PLAYBACK") {
            SettingRow("Tempo", "${state.tempoOffset.signedString()} BPM under original", "${song.bpm} BPM")
            SettingRow("Count-in", "Click before playback starts", "${state.countInBars} bar")
            SettingRow("Metronome", null, state.metronomeMode)
            SettingRow("Drum kit", null, state.kit)
            SettingRow("Mute", "Practice the muted part live", state.mutedDrum)
        }

        Section("SOURCE") {
            SettingRow("Imported", "12 May 2026", "PDF · 4 pages")
            SettingRow("Tempo detection", null, "Auto · ±2 BPM")
        }
        Spacer(Modifier.height(40.dp))
    }
}

private fun Int.signedString(): String = if (this >= 0) "+$this" else "$this"

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(title, color = DrumsColors.Dim, style = DrumsType.allCapsLabel, modifier = Modifier.padding(vertical = 8.dp))
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingRow(label: String, sub: String?, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .drawBottomBorder(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = DrumsColors.Text, style = DrumsType.rowTitle)
            if (sub != null) Text(sub, color = DrumsColors.Dim, style = DrumsType.caption)
        }
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
