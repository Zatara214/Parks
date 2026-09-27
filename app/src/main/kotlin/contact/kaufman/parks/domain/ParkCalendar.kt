package contact.kaufman.parks.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * One day of the resort's weather outlook.
 *
 * From Open-Meteo's daily forecast. Beyond about a week a daily forecast is an outlook,
 * not a promise, and the screen says so rather than presenting day ten like day one.
 */
data class DayOutlook(
    val date: LocalDate,
    val highF: Double?,
    val lowF: Double?,
    val rainChancePercent: Int?,
    val weatherCode: Int?,
)

/**
 * Anything on a park's day besides its regular hours: Early Entry, Extended Evening, Early
 * Park Admission, a hard-ticket night, an event's early access.
 */
data class ExtraSession(
    val label: String,
    val opening: Instant?,
    val closing: Instant?,
    /** Needs its own ticket. Drawn differently, because it is the one that does not come
     *  with a park ticket or an annual pass. */
    val isTicketed: Boolean,
)

enum class ParkDayStatus {
    OPEN,

    /** Inside the published range, with no regular hours on the day. */
    CLOSED,

    /**
     * Past the last date the park has published. **Not the same as closed**: schedules
     * are published weeks ahead, not forever, and a day beyond them is unknown, not shut.
     */
    NOT_YET_PUBLISHED,

    /** The schedule could not be loaded at all. */
    UNAVAILABLE,
}

data class ParkDay(
    val park: Park,
    val status: ParkDayStatus,
    val hours: ParkHours? = null,
    val extras: List<ExtraSession> = emptyList(),
)

data class CalendarDay(
    val date: LocalDate,
    val weather: DayOutlook?,
    val parks: List<ParkDay>,
)

/**
 * Turns each park's published schedule and the resort's weather outlook into a day-by-day
 * calendar.
 *
 * The schedule is the same `/schedule` response the dashboard already reads for today's
 * hours; the calendar simply keeps the days after today instead of throwing them away.
 */
object ParkCalendar {

    fun build(
        dates: List<LocalDate>,
        schedules: Map<Park, List<ParkHours>?>,
        outlook: List<DayOutlook>,
    ): List<CalendarDay> {
        val weatherByDate = outlook.associateBy { it.date }
        return dates.map { date ->
            CalendarDay(
                date = date,
                weather = weatherByDate[date],
                parks = schedules.map { (park, rows) -> parkDay(park, date, rows) },
            )
        }
    }

    /** [rows] is the park's whole published schedule, or null when it could not be loaded. */
    fun parkDay(park: Park, date: LocalDate, rows: List<ParkHours>?): ParkDay {
        if (rows == null) return ParkDay(park, ParkDayStatus.UNAVAILABLE)
        val publishedThrough = rows.maxOfOrNull { it.date }
        if (publishedThrough == null || date > publishedThrough) {
            return ParkDay(park, ParkDayStatus.NOT_YET_PUBLISHED)
        }

        val today = rows.filter { it.date == date }
        val regular = today.firstOrNull { it.isRegularOperating }
        val extras = today
            .filter { !it.isRegularOperating && !it.isClosedRow }
            .map { row ->
                ExtraSession(
                    label = extraLabel(row, regular, park.resort),
                    opening = row.opening,
                    closing = row.closing,
                    isTicketed = row.isTicketedEvent,
                )
            }
            .sortedBy { it.opening }

        return ParkDay(
            park = park,
            status = if (regular != null) ParkDayStatus.OPEN else ParkDayStatus.CLOSED,
            hours = regular,
            extras = extras,
        )
    }

    /**
     * What to call a session that is not the regular day.
     *
     * The feed's own description wins whenever it has one — that is how a hard-ticket
     * night arrives named ("Mickey's Not-So-Scary Halloween Party"). Without one, the name
     * comes from **where it sits against the regular hours**: before opening is early
     * entry, after closing is an extended evening, each in the resort's own words. This is
     * deliberately not driven by upstream's `type` beyond the ticketed case, because Walt
     * Disney World's collector is not open source and its labels for extra hours could
     * not be checked; timing is a fact the app can see for itself.
     */
    fun extraLabel(row: ParkHours, regular: ParkHours?, resort: Resort): String {
        row.description?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        if (row.isTicketedEvent) return "Ticketed event"
        if (row.type == "PRIVATE_EVENT") return "Private event"

        val opening = row.opening
        val closing = row.closing
        val dayOpens = regular?.opening
        val dayCloses = regular?.closing
        return when {
            closing != null && dayOpens != null && closing <= dayOpens -> when (resort) {
                Resort.WALT_DISNEY_WORLD -> "Early Entry"
                Resort.UNIVERSAL_ORLANDO -> "Early Park Admission"
            }
            opening != null && dayCloses != null && opening >= dayCloses -> when (resort) {
                Resort.WALT_DISNEY_WORLD -> "Extended Evening"
                Resort.UNIVERSAL_ORLANDO -> "Extended hours"
            }
            else -> "Extra hours"
        }
    }

    private val ParkHours.isClosedRow: Boolean get() = type == "CLOSED"
}
