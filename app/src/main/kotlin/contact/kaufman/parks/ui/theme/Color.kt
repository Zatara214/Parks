package contact.kaufman.parks.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fallback palette for when dynamic colour is turned off.
 *
 * Warm twilight blue and a sodium-lamp gold — the colours of a park at closing time,
 * rather than either resort's branding.
 */
val ParksBlue = Color(0xFF1B3A5C)
val ParksGold = Color(0xFFFFD166)
val ParksCoral = Color(0xFFF4978E)

// Crowd scale. Not a red-to-green ramp: those read as "error" and "success", and a busy
// park is neither. This runs cool -> warm, which matches how a day actually feels.
val CrowdVeryLight = Color(0xFF4FB3A9)
val CrowdLight = Color(0xFF74C476)
val CrowdModerate = Color(0xFFE8C547)
val CrowdBusy = Color(0xFFEF8A47)
val CrowdPacked = Color(0xFFD9534F)

/** Crowd levels are 1-10; this collapses them onto the five-stop ramp above. */
fun crowdColor(level: Int): Color = when (level.coerceIn(1, 10)) {
    1, 2 -> CrowdVeryLight
    3, 4 -> CrowdLight
    5, 6 -> CrowdModerate
    7, 8 -> CrowdBusy
    else -> CrowdPacked
}

/** Wait-time colouring uses the same ramp so a 60-minute wait and a level-8 park read
 *  as the same kind of bad. */
fun waitColor(minutes: Int): Color = when {
    minutes <= 15 -> CrowdVeryLight
    minutes <= 30 -> CrowdLight
    minutes <= 50 -> CrowdModerate
    minutes <= 75 -> CrowdBusy
    else -> CrowdPacked
}
