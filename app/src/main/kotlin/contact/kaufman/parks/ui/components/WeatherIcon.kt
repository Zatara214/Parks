package contact.kaufman.parks.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/** WMO weather codes, collapsed to the handful of shapes worth distinguishing at a glance.
 *  Shared by the dashboard strip and the calendar so a storm looks the same in both. */
fun weatherIcon(weatherCode: Int?): ImageVector = when (weatherCode) {
    null, 0, 1 -> Icons.Default.WbSunny
    in 95..99 -> Icons.Default.Thunderstorm
    in 51..67, in 80..86 -> Icons.Default.Grain
    else -> Icons.Default.Cloud
}
