package contact.kaufman.parks.ui.park

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import contact.kaufman.parks.domain.WaitHistoryDay
import contact.kaufman.parks.ui.theme.waitColor
import kotlinx.datetime.DayOfWeek
import kotlin.math.roundToInt

/**
 * What this ride has actually done on recent days, from the app's own observations.
 *
 * Distinct from the forecast above it: that is Disney's projection for the hours left
 * today, this is measurement of days already gone. The app only knows what it watched, so
 * this stays absent until there are a couple of real days to show rather than drawing a
 * chart out of one afternoon.
 */
@Composable
fun WaitHistory(days: List<WaitHistoryDay>, modifier: Modifier = Modifier) {
    if (days.size < MINIMUM_DAYS) return

    val peak = days.maxOf { it.averageMinutes }.coerceAtLeast(1f)
    val mean = days.map { it.averageMinutes }.average().roundToInt()

    Column(
        modifier = modifier.fillMaxWidth().padding(start = 68.dp, end = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Midday average over ${days.size} recorded ${if (days.size == 1) "day" else "days"} · $mean min typical",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val gap = 3.dp.toPx()
            val barWidth = ((size.width - gap * (days.size - 1)) / days.size).coerceAtLeast(1f)
            days.forEachIndexed { index, day ->
                val barHeight = (size.height * (day.averageMinutes / peak)).coerceAtLeast(2.dp.toPx())
                drawRect(
                    color = waitColor(day.averageMinutes.roundToInt()),
                    topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = days.first().date.dayOfWeek.shortLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = days.last().date.dayOfWeek.shortLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two days is the least that can show a trend; one is just today drawn twice. */
private const val MINIMUM_DAYS = 2

private fun DayOfWeek.shortLabel(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
