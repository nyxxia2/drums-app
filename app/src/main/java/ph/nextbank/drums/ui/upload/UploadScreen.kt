package ph.nextbank.drums.ui.upload

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun UploadScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    fun comingSoon() = Toast.makeText(ctx, "Coming in a future update", Toast.LENGTH_SHORT).show()

    Column(
        Modifier
            .fillMaxSize()
            .background(DrumsColors.Bg)
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
            "Drop in sheet music or pick a track. We'll detect tempo and align the playhead automatically.",
            color = DrumsColors.Dim, style = DrumsType.body,
        )

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DrumsColors.Surface)
                .border(2.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
                .clickable { comingSoon() }
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(DrumsColors.Accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color.White) }
            Text("Drop file here", color = DrumsColors.Text, style = DrumsType.cardTitle)
            Text("PDF · PNG · JPG · up to 20MB", color = DrumsColors.Dim, style = DrumsType.caption)
        }

        Spacer(Modifier.height(14.dp))
        Text("OR PICK A SOURCE", color = DrumsColors.Dim, style = DrumsType.allCapsLabel)
        Spacer(Modifier.height(8.dp))

        SourceRow(Icons.Filled.CameraAlt, true, "Take a photo", "Snap sheet music with your camera") { comingSoon() }
        Spacer(Modifier.height(8.dp))
        SourceRow(Icons.Filled.AttachFile, false, "Choose PDF or image", "Pick from your files") { comingSoon() }
        Spacer(Modifier.height(8.dp))
        SourceRow(Icons.Filled.LibraryMusic, false, "Connect Spotify", "Drum along to tracks you're playing") { comingSoon() }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, accentIcon: Boolean, title: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DrumsColors.Surface)
            .border(1.dp, DrumsColors.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (accentIcon) DrumsColors.Accent else DrumsColors.Surface2),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (accentIcon) Color.White else DrumsColors.Text) }
        Column(Modifier.weight(1f)) {
            Text(title, color = DrumsColors.Text, style = DrumsType.cardTitle)
            Text(sub, color = DrumsColors.Dim, style = DrumsType.caption)
        }
        Text("›", color = DrumsColors.Dim, style = DrumsType.cardTitle)
    }
}
