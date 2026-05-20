package ph.nextbank.drums.ui.player

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.ui.components.DrumHitChips
import ph.nextbank.drums.ui.components.DrumStaffStack
import ph.nextbank.drums.ui.components.TransportButton
import ph.nextbank.drums.ui.components.TransportStyle
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun PlayerScreen(
    songId: String,
    onBack: () -> Unit,
    vm: PlayerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val song = state.song ?: return
    val ctx = LocalContext.current

    LaunchedEffect(state.playing) {
        while (state.playing) {
            withFrameNanos { nano ->
                vm.onFrame(nano / 1_000_000L)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(DrumsColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBox(Icons.Filled.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text("NOW READING", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
                Text(song.title, color = DrumsColors.Text, style = DrumsType.cardTitle, maxLines = 1)
            }
            IconBox(Icons.Filled.MoreVert, "More") {
                Toast.makeText(ctx, "Player options coming soon", Toast.LENGTH_SHORT).show()
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("BPM", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("${song.bpm}", color = DrumsColors.Accent, style = DrumsType.playerBpm)
            val currentBar = (state.currentSlot / song.slotsPerBar).toInt() + 1
            Text("BAR", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("$currentBar", color = DrumsColors.Text, style = DrumsType.barCounter)
            Text("/${song.totalBars}", color = DrumsColors.Dim, style = DrumsType.barCounter)
        }

        val staffScroll = rememberScrollState()
        val slotsPerBar = song.slotsPerBar
        val barsPerLine = 2
        val currentLine = (state.currentSlot / slotsPerBar).toInt() / barsPerLine
        val density = androidx.compose.ui.platform.LocalDensity.current
        // line height: DrumStaffStack default lineHeightDp=140 + 4dp spacing
        val lineHeightPx = with(density) { (140 + 4).dp.toPx() }
        LaunchedEffect(currentLine) {
            // scroll so the current line is roughly centered/visible
            staffScroll.animateScrollTo((currentLine * lineHeightPx).toInt())
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                .padding(top = 18.dp, bottom = 10.dp, start = 10.dp, end = 10.dp),
        ) {
            Box(modifier = Modifier.verticalScroll(staffScroll)) {
                DrumStaffStack(
                    bars = song.bars,
                    currentSlot = state.currentSlot,
                    timeSig = song.timeSig,
                )
            }
            Text(
                "${song.timeSig.first}/${song.timeSig.second}",
                color = DrumsColors.Dim, style = DrumsType.caption,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }

        val activeTokens: Set<DrumToken> = remember(state.currentSlot, song) {
            val slot = state.currentSlot.toInt().coerceIn(0, song.bars.size * song.slotsPerBar - 1)
            val barIdx = slot / song.slotsPerBar
            val slotIdx = slot % song.slotsPerBar
            song.bars[barIdx][slotIdx].toSet()
        }
        DrumHitChips(
            activeTokens = activeTokens,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = rememberVectorPainter(Icons.Outlined.MusicNote),
                contentDescription = "Metronome",
                onClick = vm::toggleMetronome,
                style = if (state.metronomeOn) TransportStyle.PRIMARY else TransportStyle.GHOST,
                sizeDp = 42.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(Icons.Filled.Stop),
                contentDescription = "Stop",
                onClick = vm::stop,
                sizeDp = 42.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow),
                contentDescription = if (state.playing) "Pause" else "Play",
                onClick = { vm.togglePlay(SystemClock.elapsedRealtime()) },
                style = TransportStyle.PRIMARY,
                sizeDp = 64.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(Icons.Outlined.Loop),
                contentDescription = "Loop",
                onClick = {},
                sizeDp = 42.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(Icons.Outlined.Speed),
                contentDescription = "Practice",
                onClick = {},
                sizeDp = 42.dp,
            )
        }
    }
}

@Composable
private fun IconBox(icon: ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(1.dp, DrumsColors.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = cd, tint = DrumsColors.Text) }
}
