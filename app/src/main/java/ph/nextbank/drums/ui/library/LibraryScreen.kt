package ph.nextbank.drums.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import ph.nextbank.drums.ui.components.CoverArtWithMonogram
import ph.nextbank.drums.ui.components.DrumStaff
import ph.nextbank.drums.ui.components.PillTab
import ph.nextbank.drums.ui.components.TransportButton
import ph.nextbank.drums.ui.components.TransportStyle
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onSongClick: (String) -> Unit,
    onSongLongPress: (String) -> Unit,
    onAddClick: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val songs by vm.songs.collectAsState()
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("All") }

    val filtered = remember(songs, tab) {
        when (tab) {
            "Recent" -> songs.filter { it.lastPlayed != null }
            "Bundled" -> songs.filter { it.importedFrom == ImportSource.BUNDLED }
            else -> songs
        }
    }
    val sectionLabel = when (tab) {
        "Recent" -> "RECENTLY PLAYED"
        "Bundled" -> "BUNDLED SONGS"
        else -> "ALL SONGS"
    }
    val emptyMessage = when (tab) {
        "Recent" -> "Songs you've played show up here."
        "Bundled" -> "Bundled songs show up here."
        else -> "Tap the + button to add a song from Songsterr."
    }

    Box(Modifier.fillMaxSize().background(DrumsColors.Bg)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("LIBRARY", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
                    Spacer(Modifier.height(4.dp))
                    Text("Your kit", color = DrumsColors.Text, style = DrumsType.screenTitle)
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.4.dp, DrumsColors.Line, RoundedCornerShape(999.dp))
                        .clickable {
                            Toast.makeText(ctx, "Sort & options coming soon", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = DrumsColors.Text) }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All ${songs.size}", "Recent", "Bundled").forEach { label ->
                    val key = label.takeWhile { it != ' ' }
                    PillTab(label = label, active = tab == key, onClick = { tab = key })
                }
            }

            Spacer(Modifier.height(14.dp))

            val continueSong = songs.firstOrNull()
            if (continueSong != null && tab == "All") {
                ContinueCard(continueSong, onClick = { onSongClick(continueSong.id) })
                Spacer(Modifier.height(14.dp))
            }

            Text(
                sectionLabel,
                color = DrumsColors.Dim,
                style = DrumsType.allCapsLabel,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp),
            )

            if (filtered.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(top = 24.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(emptyMessage, color = DrumsColors.Dim, style = DrumsType.body)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            onClick = { onSongClick(song.id) },
                            onLongPress = { onSongLongPress(song.id) },
                        )
                    }
                }
            }
        }

        TransportButton(
            icon = rememberVectorPainter(Icons.Filled.Add),
            contentDescription = "Add song",
            onClick = onAddClick,
            style = TransportStyle.FAB,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 50.dp),
        )
    }
}

@Composable
private fun ContinueCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DrumsColors.Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArtWithMonogram(initials = song.coverInitials, sizeDp = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("⟶ CONTINUE", color = DrumsColors.Accent, style = DrumsType.allCapsLabel)
                Text(song.title, color = DrumsColors.Text, style = DrumsType.cardTitle, maxLines = 1)
                Text("${song.artist} · ${song.bpm} BPM", color = DrumsColors.Dim, style = DrumsType.caption)
            }
        }
        DrumStaff(
            bars = song.bars.take(2),
            currentSlot = 11f,
            showClef = false,
            widthDp = 300,
            heightDp = 68,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .height(4.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DrumsColors.BarTrack),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.34f).background(DrumsColors.Accent))
            }
            Text("1:14 / 3:32", color = DrumsColors.Dim, style = DrumsType.caption)
            TransportButton(
                icon = rememberVectorPainter(Icons.Filled.PlayArrow),
                contentDescription = "Play",
                onClick = onClick,
                style = TransportStyle.PRIMARY,
                sizeDp = 36.dp,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SongRow(song: Song, onClick: () -> Unit, onLongPress: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .drawBottomBorder(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverArtWithMonogram(initials = song.coverInitials, sizeDp = 40.dp, cornerDp = 8.dp)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = DrumsColors.Text, style = DrumsType.rowTitle, maxLines = 1)
            Text(
                text = "${song.artist} · ${song.bpm}BPM · ${lastPlayedLabel(song)}",
                color = DrumsColors.Dim,
                style = DrumsType.caption,
            )
        }
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = null,
            tint = DrumsColors.Dim,
            modifier = Modifier.size(14.dp),
        )
    }
}

private fun lastPlayedLabel(song: Song): String =
    if (song.lastPlayed == null) "Imported"
    else "Last played"

private fun Modifier.drawBottomBorder(): Modifier = drawBehind {
    drawLine(
        color = DrumsColors.Line,
        start = Offset(0f, size.height - 0.5f),
        end = Offset(size.width, size.height - 0.5f),
        strokeWidth = 1f,
    )
}
