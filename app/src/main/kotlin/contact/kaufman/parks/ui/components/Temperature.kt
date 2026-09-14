package contact.kaufman.parks.ui.components

import contact.kaufman.parks.data.prefs.TemperatureUnit
import kotlin.math.roundToInt

/**
 * Weather is always fetched in Fahrenheit and converted here.
 *
 * Doing it at display time rather than by re-requesting means flipping the unit is
 * instant and costs Open-Meteo nothing.
 */
fun formatTemperature(fahrenheit: Double?, unit: TemperatureUnit): String {
    if (fahrenheit == null) return "—"
    val value = when (unit) {
        TemperatureUnit.FAHRENHEIT -> fahrenheit
        TemperatureUnit.CELSIUS -> (fahrenheit - 32.0) * 5.0 / 9.0
    }
    return "${value.roundToInt()}°"
}
