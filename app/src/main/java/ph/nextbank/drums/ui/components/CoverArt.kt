package ph.nextbank.drums.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

@Composable
fun CoverArt(initials: String, sizeDp: Dp, cornerDp: Dp = 12.dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(cornerDp)),
    ) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(DrumsColors.CoverGradFrom, DrumsColors.CoverGradTo),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
        )
        rotate(degrees = -25f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            val stripeW = size.width / 8f
            for (i in -2..10) {
                drawRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(i * stripeW * 1.5f, -size.height),
                    size = Size(stripeW * 0.4f, size.height * 3f),
                )
            }
        }
    }
}

@Composable
fun CoverArtWithMonogram(initials: String, sizeDp: Dp, cornerDp: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CoverArt(initials, sizeDp, cornerDp)
        Text(text = initials, color = Color.White, style = DrumsType.cardTitle)
    }
}
