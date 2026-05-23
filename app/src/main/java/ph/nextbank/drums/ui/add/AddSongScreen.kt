package ph.nextbank.drums.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ph.nextbank.drums.audio.songsterr.SongsterrResult
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun AddSongScreen(
    onBack: () -> Unit,
    vm: AddSongViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(DrumsColors.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, DrumsColors.Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DrumsColors.Text) }
            Spacer(Modifier.width(12.dp))
            Text("Add a song", color = DrumsColors.Text, style = DrumsType.sectionTitle)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Search Songsterr — we'll grab the drum tab and find the audio on YouTube.",
            color = DrumsColors.Dim, style = DrumsType.body,
        )

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (state.query.isEmpty()) {
                Text("Song or artist…", color = DrumsColors.Dim, style = DrumsType.body)
            }
            BasicTextField(
                value = state.query,
                onValueChange = vm::onQueryChanged,
                singleLine = true,
                textStyle = TextStyle(color = DrumsColors.Text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        if (state.isSearching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DrumsColors.Accent)
                Spacer(Modifier.width(8.dp))
                Text("Searching…", color = DrumsColors.Dim, style = DrumsType.caption)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.results, key = { it.songId }) { result ->
                    SongsterrResultRow(result) { vm.onResultClicked(result) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SongsterrResultRow(result: SongsterrResult, onClick: () -> Unit) {
    val hasDrums = result.popularTrackDrum != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DrumsColors.Surface)
            .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
            .clickable(enabled = hasDrums, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(result.title, color = DrumsColors.Text, style = DrumsType.cardTitle)
            Text(
                if (hasDrums) result.artist else "${result.artist} · no drum tab",
                color = if (hasDrums) DrumsColors.Dim else DrumsColors.Line,
                style = DrumsType.caption,
            )
        }
        if (hasDrums) {
            Text("›", color = DrumsColors.Dim, style = DrumsType.cardTitle)
        }
    }
}
