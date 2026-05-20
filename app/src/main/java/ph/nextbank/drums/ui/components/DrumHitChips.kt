package ph.nextbank.drums.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.ui.theme.DrumsColors
import ph.nextbank.drums.ui.theme.DrumsType

private val CHIP_ORDER: List<Pair<String, DrumToken>> = listOf(
    "KICK" to DrumToken.KICK,
    "SNR" to DrumToken.SNARE,
    "HH" to DrumToken.HIHAT_CLOSED,
    "CRSH" to DrumToken.CRASH,
    "RIDE" to DrumToken.RIDE,
    "TOM1" to DrumToken.TOM_HI,
    "TOM2" to DrumToken.TOM_MID,
    "FLR" to DrumToken.TOM_FLOOR,
)

@Composable
fun DrumHitChips(activeTokens: Set<DrumToken>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CHIP_ORDER.forEach { (label, token) ->
            val active = token in activeTokens
            Text(
                text = label,
                color = if (active) DrumsColors.ChipActiveText else DrumsColors.Dim,
                style = DrumsType.drumHitChip,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) DrumsColors.ChipActiveBg else DrumsColors.ChipBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
