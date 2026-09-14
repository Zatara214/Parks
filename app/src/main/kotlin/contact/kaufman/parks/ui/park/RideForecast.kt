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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import contact.kaufman.parks.domain.ForecastPoint
import contact.kaufman.parks.domain.ParkEntity
import contact.kaufman.parks.ui.components.ParkTimeZone
import contact.kaufman.parks.ui.theme.waitColor
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Disney's own hourly wait forecast for one ride, drawn as bars across the rest of the day.
 *
 * This answers the question actually asked while standing in front of a ride: queue now,
 * or come back later? Universal publishes no forecast, so this simply does not appear
 * there rather than showing an empty chart.
 */
@Composable
fun RideForecast(entity: ParkEntity, modifier: Modifier = Modifier) {
    // Previews have no clock worth honouring; anchor to the data so bars always render.
    val now = if (LocalInspectionMode.current) {
        entity.forecast.firstOrNull()?.time ?: Clock.System.now()
    } else {
        Clock.System.now()
    }

    val points = entity.forecast.filter { it.waitMinutes != null && it.time >= now.minusOneHour() }
    if (points.size < 2) return

    val best = entity.bestTimeAhead(now)
    val peak = points.maxOf { it.waitMinutes!! }.coerceAtLeast(1)

    Column(
        modifier = modifier.fillMaxWidth().padding(start = 68.dp, end = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (best != null) {
                "Quietest around ${best.time.hourLabel()} · about ${best.waitMinutes} min"
            } else {
                "Forecast for the rest of today"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (best != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (best != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        ForecastBars(points = points, peak = peak, best = best, now = now)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = points.first().time.hourLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = points.last().time.hourLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ForecastBars(
    points: List<ForecastPoint>,
    peak: Int,
    best: ForecastPoint?,
    now: Instant,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val highlight = MaterialTheme.colorScheme.primary

    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (points.size - 1)) / points.size).coerceAtLeast(1f)

        points.forEachIndexed { index, point ->
            val minutes = point.waitMinutes ?: return@forEachIndexed
            val fraction = minutes.toFloat() / peak
            // Every bar keeps a visible stub so a zero-wait hour still reads as an hour
            // rather than as missing data.
            val barHeight = (size.height * fraction).coerceAtLeast(2.dp.toPx())
            val x = index * (barWidth + gap)

            val color: Color = when {
                point === best -> highlight
                // Hours already gone are context, not advice.
                point.time < now -> dim
                else -> waitColor(minutes)
            }

            drawRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

private fun Instant.hourLabel(): String {
    val hour = toLocalDateTime(ParkTimeZone).hour
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$h12${if (hour < 12) "am" else "pm"}"
}

/** Keeps the hour just gone on screen so the curve has somewhere to come from. */
private fun Instant.minusOneHour(): Instant = Instant.fromEpochSeconds(epochSeconds - 3600)
