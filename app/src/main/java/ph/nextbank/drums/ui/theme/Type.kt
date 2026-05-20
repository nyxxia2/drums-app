package ph.nextbank.drums.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// TODO(font): drop space_grotesk_*.ttf and jetbrains_mono_*.ttf into res/font/
// then restore named FontFamily(Font(R.font.*, FontWeight.*)) definitions per design spec.
val SpaceGrotesk: FontFamily = FontFamily.Default
val JetBrainsMono: FontFamily = FontFamily.Monospace

object DrumsType {
    val displayBpm = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp, letterSpacing = (-0.05).em, lineHeight = 72.sp)
    val playerBpm = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, letterSpacing = (-0.04).em, lineHeight = 36.sp)
    val screenTitle = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.03).em, lineHeight = 32.sp)
    val coverTitle = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.03).em, lineHeight = 27.sp)
    val barCounter = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.045).em, lineHeight = 22.sp)
    val sectionTitle = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.022).em, lineHeight = 24.sp)
    val cardTitle = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.018).em, lineHeight = 20.sp)
    val buttonLabel = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.028.em, lineHeight = 17.sp)
    val rowTitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = (-0.014).em, lineHeight = 18.sp)
    val body = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp)
    val allCapsLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.13.em, lineHeight = 13.sp)
    val drumHitChip = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.08.em, lineHeight = 9.sp)
}

val DrumsTypography = Typography(
    headlineLarge = DrumsType.screenTitle,
    titleLarge = DrumsType.sectionTitle,
    titleMedium = DrumsType.cardTitle,
    labelLarge = DrumsType.buttonLabel,
    bodyMedium = DrumsType.body,
    labelSmall = DrumsType.caption,
)
