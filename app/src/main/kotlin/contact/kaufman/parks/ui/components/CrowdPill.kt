package contact.kaufman.parks.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import contact.kaufman.parks.domain.CrowdReading
import contact.kaufman.parks.ui.theme.crowdColor
import kotlin.math.roundToInt

/**
 * The crowd level as a single glanceable chip.
 *
 * The number animates between readings rather than snapping, because a crowd level that
 * jumps from 4 to 7 on a refresh looks like a bug even when it is correct.
 */
@Composable
fun CrowdPill(
    reading: CrowdReading,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val target = crowdColor(reading.level)
    val color by animateColorAsState(target, label = "crowdColor")
    val level by animateFloatAsState(reading.exactLevel, label = "crowdLevel")
    val onColor = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.82f) else Color.White

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(onColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // Must round, not truncate: the label beside it comes from
                // `reading.level`, which is already rounded, so truncating here renders
                // a 5.6 as "5 · Above average".
                text = level.roundToInt().coerceIn(1, 10).toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = onColor,
            )
        }
        if (showLabel) {
            Text(
                text = reading.label,
                style = MaterialTheme.typography.labelLarge,
                color = onColor,
            )
        }
    }
}
