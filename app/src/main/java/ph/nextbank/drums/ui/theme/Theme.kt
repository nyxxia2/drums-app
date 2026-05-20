package ph.nextbank.drums.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    background = DrumsColors.Bg,
    surface = DrumsColors.Surface,
    surfaceVariant = DrumsColors.Surface2,
    onBackground = DrumsColors.Text,
    onSurface = DrumsColors.Text,
    primary = DrumsColors.Accent,
    onPrimary = DrumsColors.AccentOn,
    outline = DrumsColors.Line,
)

@Composable
fun DrumsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = DrumsTypography,
        shapes = DrumsShapes,
        content = content,
    )
}
