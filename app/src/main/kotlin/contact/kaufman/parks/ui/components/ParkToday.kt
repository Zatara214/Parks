package contact.kaufman.parks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Today's date in **park time**, kept current while the screen is open.
 *
 * Reading the clock straight into a composable looks fine and quietly rots: nothing
 * invalidates it, so the dashboard showed "Monday, September 14" for the whole of the 15th
 * and only corrected itself when navigating into a park and back rebuilt the top bar.
 * Pull-to-refresh did not help either — refreshing park data changes no state the date
 * reads, so Compose had no reason to run it again.
 *
 * Two triggers cover the two ways a day turns over:
 * - coming back to the foreground, which is the usual case, the app having been left on
 *   the dashboard overnight;
 * - a timer that sleeps until the next park midnight, so sitting on the screen at midnight
 *   is right too. One sleep to a known boundary, not a poll.
 */
@Composable
fun rememberParkToday(): LocalDate {
    var today by remember { mutableStateOf(parkToday()) }

    LifecycleResumeEffect(Unit) {
        today = parkToday()
        onPauseOrDispose { }
    }

    LaunchedEffect(today) {
        delay(millisUntilParkTomorrow(Clock.System.now()))
        today = parkToday()
    }

    return today
}

private fun parkToday(): LocalDate = Clock.System.now().toLocalDateTime(ParkTimeZone).date

/**
 * Milliseconds from [now] until midnight in park time.
 *
 * Steps a **calendar day** and asks the time zone where that starts, rather than adding 24
 * hours: the two DST changeover days are 23 and 25 hours long, and a fixed addition lands
 * an hour off on exactly those two mornings. Same reasoning as `domain/ParkingDay.kt`.
 */
internal fun millisUntilParkTomorrow(now: Instant): Long {
    val tomorrow = now.toLocalDateTime(ParkTimeZone).date.plus(1, DateTimeUnit.DAY)
    val boundary = tomorrow.atStartOfDayIn(ParkTimeZone)
    // A second of slack, so a wake-up that fires a hair early does not read yesterday and
    // then immediately sleep for another whole day.
    return (boundary - now).inWholeMilliseconds + 1_000
}
