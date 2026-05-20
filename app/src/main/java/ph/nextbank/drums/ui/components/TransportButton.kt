package ph.nextbank.drums.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.ui.theme.DrumsColors

enum class TransportStyle { PRIMARY, GHOST, FAB }

@Composable
fun TransportButton(
    icon: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    style: TransportStyle = TransportStyle.GHOST,
    sizeDp: Dp = when (style) {
        TransportStyle.PRIMARY -> 64.dp
        TransportStyle.FAB -> 56.dp
        TransportStyle.GHOST -> 42.dp
    },
    modifier: Modifier = Modifier,
) {
    val bg = when (style) {
        TransportStyle.PRIMARY, TransportStyle.FAB -> DrumsColors.Accent
        TransportStyle.GHOST -> Color.Transparent
    }
    val iconCol = if (style == TransportStyle.GHOST) DrumsColors.Text else DrumsColors.AccentOn
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(bg)
            .then(if (style == TransportStyle.GHOST) Modifier.border(1.dp, DrumsColors.Line, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = icon, contentDescription = contentDescription, tint = iconCol)
    }
}
