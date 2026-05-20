package ph.nextbank.drums

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsTheme
import ph.nextbank.drums.ui.theme.DrumsType

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DrumsTheme { PlaceholderRoot() }
        }
    }
}

@Composable
private fun PlaceholderRoot() {
    Box(
        modifier = Modifier.fillMaxSize().background(DrumsColors.Bg),
        contentAlignment = Alignment.Center,
    ) {
        Text("Drums", color = DrumsColors.Accent, style = DrumsType.screenTitle)
    }
}
