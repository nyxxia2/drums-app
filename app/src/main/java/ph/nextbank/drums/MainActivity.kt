package ph.nextbank.drums

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import ph.nextbank.drums.audio.DrumSampleBank
import ph.nextbank.drums.audio.SongClock
import ph.nextbank.drums.data.samples.SAMPLE_SONGS
import ph.nextbank.drums.ui.components.DrumStaffStack
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsTheme
import ph.nextbank.drums.ui.theme.DrumsType

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DrumsTheme { DebugPlayer() }
        }
    }
}

@Composable
private fun DebugPlayer() {
    val context = LocalContext.current
    val bank = remember { DrumSampleBank(context) }
    val song = SAMPLE_SONGS[0]
    val totalSlots = song.bars.size * song.bars[0].size
    val clock = remember { SongClock({ song.bpm }, totalSlots) }
    var playing by remember { mutableStateOf(false) }
    var slot by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            withFrameNanos { nano ->
                val now = nano / 1_000_000L
                slot = clock.currentSlot(now)
                clock.slotJustEntered(now)?.let { idx ->
                    val barIdx = idx / song.bars[0].size
                    val slotIdx = idx % song.bars[0].size
                    song.bars.getOrNull(barIdx)?.get(slotIdx)?.forEach(bank::play)
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(DrumsColors.Bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Debug player", color = DrumsColors.Text, style = DrumsType.screenTitle)
        DrumStaffStack(
            bars = song.bars,
            currentSlot = slot,
            timeSig = song.timeSig,
        )
        Button(
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (playing) clock.pause(now) else clock.play(now)
                playing = !playing
            },
        ) { Text(if (playing) "Pause" else "Play") }
    }
}
