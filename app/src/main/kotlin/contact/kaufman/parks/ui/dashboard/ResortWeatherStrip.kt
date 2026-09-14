package contact.kaufman.parks.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import contact.kaufman.parks.data.prefs.TemperatureUnit
import contact.kaufman.parks.domain.ParkWeather
import contact.kaufman.parks.ui.components.formatTemperature

/**
 * Resort weather under the Today title.
 *
 * Deliberately one line: this is the "do I need a poncho" glance, not a weather app. It
 * reads from the middle of Walt Disney World property, because the four parks are close
 * enough that an afternoon storm does not pick between them.
 */
@Composable
fun ResortWeatherStrip(
    weather: ParkWeather?,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = weather != null,
        enter = fadeIn() + expandVertically(),
    ) {
        weather ?: return@AnimatedVisibility
        Row(
            modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = weather.icon(),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = formatTemperature(weather.temperatureF, unit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            // The heat index is the number that actually decides the day in Orlando, so it
            // earns a place even in a one-line summary.
            weather.feelsLikeF?.let {
                Text(
                    text = "feels ${formatTemperature(it, unit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            weather.rainChancePercent?.let { chance ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        Icons.Default.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "$chance%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (chance >= 50) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (chance >= 50) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** WMO weather codes, collapsed to the handful of shapes worth distinguishing at a glance. */
private fun ParkWeather.icon(): ImageVector = when (weatherCode) {
    null, 0, 1 -> Icons.Default.WbSunny
    in 95..99 -> Icons.Default.Thunderstorm
    in 51..67, in 80..86 -> Icons.Default.Grain
    else -> Icons.Default.Cloud
}
