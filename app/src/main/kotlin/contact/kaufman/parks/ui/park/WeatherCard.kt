package contact.kaufman.parks.ui.park

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
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import contact.kaufman.parks.data.prefs.TemperatureUnit
import contact.kaufman.parks.domain.ParkWeather
import contact.kaufman.parks.ui.components.formatTemperature

/**
 * Weather, behind a tap.
 *
 * Zak has a dedicated weather app, so this is never fetched on its own — the card starts
 * as a button. What it shows is tuned to the one question a park actually raises: is it
 * about to rain on me, and how soon.
 */
@Composable
fun WeatherCard(
    weather: ParkWeather?,
    isLoading: Boolean,
    unit: TemperatureUnit,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            when {
                weather != null -> WeatherContent(weather, unit)
                isLoading -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator()
                }
                else -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Weather",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TextButton(onClick = onLoad) { Text("Check") }
                }
            }
        }
    }
}

@Composable
private fun WeatherContent(weather: ParkWeather, unit: TemperatureUnit) {
    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Default.WbSunny,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = formatTemperature(weather.temperatureF, unit),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Column {
                    // In Orlando the real number is the heat index, not the air
                    // temperature — 91° feeling like 104° is the difference between a
                    // normal afternoon and a miserable one.
                    weather.feelsLikeF?.let {
                        Text(
                            text = "Feels like ${formatTemperature(it, unit)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val range = listOfNotNull(
                        weather.highF?.let { "H ${formatTemperature(it, unit)}" },
                        weather.lowF?.let { "L ${formatTemperature(it, unit)}" },
                    ).joinToString(" · ")
                    if (range.isNotEmpty()) {
                        Text(
                            text = range,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            weather.rainChancePercent?.let { chance ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "$chance% chance of rain today",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (weather.hourlyRainChance.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    weather.hourlyRainChance.take(6).forEach { (time, chance) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$chance%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (chance >= 50) FontWeight.Bold else FontWeight.Normal,
                                color = if (chance >= 50) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = time.hour.let { h ->
                                    val h12 = if (h % 12 == 0) 12 else h % 12
                                    "$h12${if (h < 12) "a" else "p"}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
