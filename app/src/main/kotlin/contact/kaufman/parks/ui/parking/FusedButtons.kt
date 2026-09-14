package contact.kaufman.parks.ui.parking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A connected (fused) row of toggle buttons — the Material 3 Expressive button group.
 *
 * Fused groups say "pick exactly one of these" far more clearly than a row of detached
 * chips does, which is precisely the choice being made at every step of recording a spot.
 * The shapes come from [ButtonGroupDefaults] so the leading and trailing ends round off
 * and the middle stays square, and so a pressed button morphs the way the spec intends.
 */
@Composable
fun <T> FusedToggleRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val shapes = when {
                // A lone button has no neighbours to fuse with, so it keeps the stock
                // rounded shape rather than a half-connected one.
                options.size == 1 -> ToggleButtonShapes(
                    shape = ToggleButtonDefaults.shape,
                    pressedShape = ToggleButtonDefaults.pressedShape,
                    checkedShape = ToggleButtonDefaults.checkedShape,
                )
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = shapes,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Splits options into balanced rows of at most three.
 *
 * A plain `chunked(3)` leaves four options as a row of three and a lone full-width button,
 * which reads as a mistake. Balancing gives 2+2 instead, and 5 becomes 3+2.
 */
fun <T> List<T>.balancedRows(maxPerRow: Int = 3): List<List<T>> {
    if (isEmpty()) return emptyList()
    val rows = (size + maxPerRow - 1) / maxPerRow
    val perRow = (size + rows - 1) / rows
    return chunked(perRow)
}
