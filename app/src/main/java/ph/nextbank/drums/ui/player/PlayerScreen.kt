package ph.nextbank.drums.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.audio.playback.PlaybackState
import ph.nextbank.drums.ui.components.DrumHitChips
import ph.nextbank.drums.ui.components.DrumStaff
import ph.nextbank.drums.ui.components.DrumStaffLayout
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

    DisposableEffect(Unit) {
        val activity = ctx.findActivity()
        val prior = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                prior ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is PlayerEvent.Toast ->
                    Toast.makeText(ctx, ev.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(DrumsColors.Bg)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            HeaderRow(song.title, state, vm, onBack)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                StaffArea(state, song)
                AudioStatusPill(
                    phase = state.phase,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }

            DrumHitChips(
                activeTokens = remember(state.activeSlotIndex, song) {
                    val slot = state.activeSlotIndex.coerceIn(0, song.bars.size * song.slotsPerBar - 1)
                    val barIdx = slot / song.slotsPerBar
                    val slotIdx = slot % song.slotsPerBar
                    song.bars[barIdx][slotIdx].toSet()
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            BottomRow(state, vm)
        }

        val phase = state.phase
        if (phase is PlayerPhase.Confirming) {
            YouTubeConfirmDialog(
                candidate = phase.candidate,
                onUseThis = { vm.acceptCandidate() },
                onTryAnother = { vm.tryAnotherVideo() },
                onDismiss = { vm.dismissConfirmation() },
            )
        }
    }
}

@Composable
private fun HeaderRow(
    title: String,
    state: PlayerUiState,
    vm: PlayerViewModel,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBox(Icons.Filled.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f)) {
            Text("NOW READING", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text(title, color = DrumsColors.Text, style = DrumsType.cardTitle, maxLines = 1)
        }
        if (state.phase is PlayerPhase.SynthFallback) {
            Text(
                "SYNTH",
                color = DrumsColors.Dim, style = DrumsType.allCapsLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, DrumsColors.Dim, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Box {
            IconBox(Icons.Filled.MoreVert, "More") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Try another video") },
                    onClick = { menuOpen = false; vm.tryAnotherVideo() },
                )
            }
        }
    }
}

@Composable
private fun StaffArea(state: PlayerUiState, song: ph.nextbank.drums.data.model.Song) {
    val density = LocalDensity.current
    val playheadXDp = 140
    val staffHeightDp = 280
    val barCount = song.bars.size
    val barWidthDp = DrumStaffLayout.computeBarWidthDp(barCount)
    val totalWidthDp = barWidthDp * barCount
    val slotsTotal = barCount * song.slotsPerBar
    val clefWPx = with(density) { 32.dp.toPx() }
    val padXPx = with(density) { 12.dp.toPx() }
    val totalWidthPx = with(density) { totalWidthDp.dp.toPx() }
    val innerX0Px = clefWPx + padXPx
    val innerWPx = totalWidthPx - clefWPx - 2 * padXPx
    val pxPerSlotPx = innerWPx / slotsTotal
    val playheadXPx = with(density) { playheadXDp.dp.toPx() }
    val translationXPx = playheadXPx - innerX0Px - state.currentSlot * pxPerSlotPx

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DrumsColors.Surface)
            .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        DrumStaff(
            bars = song.bars,
            currentSlot = 0f,
            showClef = true,
            showPlayhead = false,
            timeSig = song.timeSig,
            widthDp = totalWidthDp,
            heightDp = staffHeightDp,
            modifier = Modifier.graphicsLayer { translationX = translationXPx },
        )
        Box(
            modifier = Modifier
                .offset(x = (playheadXDp - 1).dp)
                .fillMaxHeight()
                .width(2.5.dp)
                .background(DrumsColors.Playhead),
        )
    }
}

@Composable
private fun AudioStatusPill(phase: PlayerPhase, modifier: Modifier = Modifier) {
    val (label, showSpinner) = when (phase) {
        is PlayerPhase.Loading -> "Loading…" to true
        is PlayerPhase.Searching -> "Searching YouTube…" to true
        is PlayerPhase.Confirming -> "Awaiting confirmation" to false
        is PlayerPhase.YouTubeBuffering -> "Loading audio…" to true
        is PlayerPhase.YouTubeReady -> "♪ YouTube audio" to false
        is PlayerPhase.SynthFallback -> "Synth mode" to false
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(DrumsColors.Surface2)
            .border(1.dp, DrumsColors.Line, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                color = DrumsColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(label, color = DrumsColors.Dim, style = DrumsType.caption)
    }
}

@Composable
private fun BottomRow(state: PlayerUiState, vm: PlayerViewModel) {
    val isPlaying = state.playbackState == PlaybackState.Playing
    val canPlay = state.playbackState in setOf(
        PlaybackState.Ready, PlaybackState.Playing,
        PlaybackState.Paused, PlaybackState.Finished,
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
            icon = rememberVectorPainter(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow),
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = { if (canPlay) vm.togglePlay() },
            style = TransportStyle.PRIMARY,
            sizeDp = 64.dp,
        )
        TransportButton(
            icon = rememberVectorPainter(Icons.Outlined.Loop),
            contentDescription = if (state.looping) "Stop looping" else "Loop song",
            onClick = vm::toggleLoop,
            style = if (state.looping) TransportStyle.PRIMARY else TransportStyle.GHOST,
            sizeDp = 42.dp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SyncButton("−", onClick = { vm.nudgeOffset(-50) })
            Text("${state.youtubeOffsetMs}ms", color = DrumsColors.Dim, style = DrumsType.caption)
            SyncButton("+", onClick = { vm.nudgeOffset(50) })
        }
    }
}

@Composable
private fun SyncButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .border(1.dp, DrumsColors.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = DrumsColors.Text, style = DrumsType.caption) }
}

private fun android.content.Context.findActivity(): Activity? {
    var c: android.content.Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
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
