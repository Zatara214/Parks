package contact.kaufman.parks.ui.components

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

val ParkTimeZone: TimeZone = TimeZone.of("America/New_York")

/** "9:00 AM" in park-local time, which is the only time zone a park hour ever means. */
fun Instant.toParkClockTime(): String {
    val local = toLocalDateTime(ParkTimeZone)
    val hour12 = when (local.hour % 12) {
        0 -> 12
        else -> local.hour % 12
    }
    val suffix = if (local.hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(hour12, local.minute, suffix)
}

/** "9:00 AM – 6:00 PM", or a single-ended range when upstream only gives one side. */
fun formatHoursRange(opening: Instant?, closing: Instant?): String = when {
    opening != null && closing != null -> "${opening.toParkClockTime()} – ${closing.toParkClockTime()}"
    opening != null -> "Opens ${opening.toParkClockTime()}"
    closing != null -> "Closes ${closing.toParkClockTime()}"
    else -> "Hours unavailable"
}
