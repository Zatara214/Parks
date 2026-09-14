package contact.kaufman.parks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * When a recorded parking spot stops being "today's".
 *
 * A spot should not still be showing tomorrow morning — nobody lives at the resort. But
 * the day cannot roll over at midnight either: Magic Kingdom's hard-ticket nights run to
 * midnight and EPCOT's Extended Evening hours to 11PM, so a car parked at 9PM is still
 * very much parked at 12:30AM.
 *
 * [ROLLOVER_HOUR] is the compromise — late enough that every park has closed and emptied,
 * early enough that it has always passed before a normal morning.
 *
 * This is evaluated on read rather than scheduled as a background job: no wake-ups, no
 * battery cost, and it is still correct if the phone was off at 4AM.
 */
object ParkingDay {

    const val ROLLOVER_HOUR = 4

    /** Orlando. A spot is recorded in park-local terms, not wherever the phone thinks it is. */
    val TIME_ZONE: TimeZone = TimeZone.of("America/New_York")

    /**
     * The most recent rollover at or before [now] — the instant before which a parking
     * record belongs to a previous day.
     */
    fun mostRecentRollover(now: Instant): Instant {
        val today = now.toLocalDateTime(TIME_ZONE).date
        val todayRollover = rolloverOn(today)
        // Before 4AM the boundary that matters is yesterday's, so a car parked at 11PM
        // last night is still current at 1AM. Step a calendar day rather than subtracting
        // 24 hours: the spring and autumn DST days are 23 and 25 hours long, and a fixed
        // subtraction lands an hour off on exactly those two mornings.
        return if (now < todayRollover) rolloverOn(today.minus(1, DateTimeUnit.DAY)) else todayRollover
    }

    private fun rolloverOn(date: LocalDate): Instant =
        LocalDateTime(date, LocalTime(ROLLOVER_HOUR, 0)).toInstant(TIME_ZONE)

    /** True once [parkedAt] falls on the far side of the most recent rollover. */
    fun hasExpired(parkedAtEpochSeconds: Long, now: Instant): Boolean =
        parkedAtEpochSeconds < mostRecentRollover(now).epochSeconds
}
