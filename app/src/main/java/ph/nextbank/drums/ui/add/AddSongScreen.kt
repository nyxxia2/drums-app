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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun AddSongScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }

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
            if (query.isEmpty()) {
                Text("Song or artist…", color = DrumsColors.Dim, style = DrumsType.body)
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = DrumsColors.Text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        // Results list will go here in Task 7.
    }
}
