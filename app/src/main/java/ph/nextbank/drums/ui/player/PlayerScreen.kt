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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.ui.components.DrumHitChips
import ph.nextbank.drums.ui.components.DrumStaff
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

    // Frame-driven scheduler is only needed when SongClock drives the sound.
    LaunchedEffect(state.playing, state.youtubeMode) {
        if (state.youtubeMode) return@LaunchedEffect
        while (state.playing) {
            withFrameNanos { nano -> vm.onFrame(nano / 1_000_000L) }
        }
    }

    Column(Modifier.fillMaxSize().background(DrumsColors.Bg).safeDrawingPadding()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBox(Icons.Filled.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text("NOW READING", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
                Text(song.title, color = DrumsColors.Text, style = DrumsType.cardTitle, maxLines = 1)
            }
            // Inline BPM + bar — saves a row of vertical space for landscape.
            Text("BPM", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("${song.bpm}", color = DrumsColors.Accent, style = DrumsType.cardTitle)
            val currentBar = (state.currentSlot / song.slotsPerBar).toInt() + 1
            Text("BAR", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
            Text("$currentBar/${song.totalBars}", color = DrumsColors.Text, style = DrumsType.cardTitle)
            IconBox(Icons.Filled.MoreVert, "More") {
                Toast.makeText(ctx, "Player options coming soon", Toast.LENGTH_SHORT).show()
            }
        }

        // Body: YouTube player (when available) on the left, drum staff on the right.
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (song.youtubeVideoId != null) {
                YoutubePane(
                    videoId = song.youtubeVideoId,
                    onReady = vm::setYoutubePlayer,
                    onSecond = vm::onYoutubeSecond,
                    onStateChange = vm::onYoutubeStateChange,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(16f / 9f),
                )
            }
            StaffPane(
                song = song,
                currentSlot = state.currentSlot,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        // Transport
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = rememberVectorPainter(Icons.Outlined.MusicNote),
                contentDescription = "Metronome",
                onClick = vm::toggleMetronome,
                style = if (state.metronomeOn) TransportStyle.PRIMARY else TransportStyle.GHOST,
                sizeDp = 38.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(Icons.Filled.Stop),
                contentDescription = "Stop",
                onClick = vm::stop,
                sizeDp = 38.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow),
                contentDescription = if (state.playing) "Pause" else "Play",
                onClick = { vm.togglePlay(System.nanoTime() / 1_000_000L) },
                style = TransportStyle.PRIMARY,
                sizeDp = 56.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(Icons.Outlined.Loop),
                contentDescription = if (state.looping) "Stop looping" else "Loop song",
                onClick = vm::toggleLoop,
                style = if (state.looping) TransportStyle.PRIMARY else TransportStyle.GHOST,
                sizeDp = 38.dp,
            )
            TransportButton(
                icon = rememberVectorPainter(Icons.Outlined.Speed),
                contentDescription = "Practice",
                onClick = {},
                sizeDp = 38.dp,
            )
        }
    }
}

@Composable
private fun YoutubePane(
    videoId: String,
    onReady: (YouTubePlayer) -> Unit,
    onSecond: (Float) -> Unit,
    onStateChange: (PlayerConstants.PlayerState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DrumsColors.Surface),
    ) {
        AndroidView(
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    // We provide our own UI so we can drive playback from the transport
                    // row. Enabling 'controls=0' hides YouTube's overlaid controls.
                    enableAutomaticInitialization = false
                    val opts = IFramePlayerOptions.Builder().controls(0).build()
                    lifecycleOwner.lifecycle.addObserver(this)
                    initialize(
                        object : AbstractYouTubePlayerListener() {
                            override fun onReady(player: YouTubePlayer) {
                                onReady(player)
                                player.loadVideo(videoId, 0f)
                            }
                            override fun onCurrentSecond(player: YouTubePlayer, second: Float) {
                                onSecond(second)
                            }
                            override fun onStateChange(
                                player: YouTubePlayer,
                                state: PlayerConstants.PlayerState,
                            ) {
                                onStateChange(state)
                            }
                        },
                        opts,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StaffPane(
    song: ph.nextbank.drums.data.model.Song,
    currentSlot: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val barWidthDp = 380
    val playheadXDp = 120
    val staffHeightDp = 240
    val barCount = song.bars.size
    val totalWidthDp = barWidthDp * barCount
    val slotsTotal = barCount * song.slotsPerBar
    val clefWPx = with(density) { 32.dp.toPx() }
    val padXPx = with(density) { 12.dp.toPx() }
    val totalWidthPx = with(density) { totalWidthDp.dp.toPx() }
    val innerX0Px = clefWPx + padXPx
    val innerWPx = totalWidthPx - clefWPx - 2 * padXPx
    val pxPerSlotPx = innerWPx / slotsTotal
    val playheadXPx = with(density) { playheadXDp.dp.toPx() }
    val translationXPx = playheadXPx - innerX0Px - currentSlot * pxPerSlotPx

    Box(
        modifier = modifier
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
        Text(
            "${song.timeSig.first}/${song.timeSig.second}",
            color = DrumsColors.Dim, style = DrumsType.caption,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        )
    }
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
