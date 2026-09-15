package contact.kaufman.parks.ui.components

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ParkTodayTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime(year, month, day, hour, minute).toInstant(ParkTimeZone)

    /** The slack the implementation adds so a hair-early wake-up cannot read yesterday. */
    private val slack = 1_000L

    @Test
    fun `the heading reads as a person would say it`() {
        assertEquals("Tuesday, September 15", LocalDate(2026, 9, 15).toParkDateHeading())
        assertEquals("Sunday, March 1", LocalDate(2026, 3, 1).toParkDateHeading())
    }

    @Test
    fun `an ordinary evening sleeps until midnight`() {
        val millis = millisUntilParkTomorrow(at(2026, 9, 15, 23, 30))
        assertEquals(30.minutes.inWholeMilliseconds + slack, millis)
    }

    @Test
    fun `just after midnight sleeps nearly a whole day`() {
        val millis = millisUntilParkTomorrow(at(2026, 9, 15, 0, 1))
        assertEquals((24.hours - 1.minutes).inWholeMilliseconds + slack, millis)
    }

    /**
     * Spring forward: 8 March 2026 is 23 hours long in New York. Adding a fixed 24 hours
     * would overshoot the boundary by an hour and the date would be a day late all morning.
     */
    @Test
    fun `the short DST day is 23 hours, not 24`() {
        val millis = millisUntilParkTomorrow(at(2026, 3, 8, 0, 30))
        assertEquals((23.hours - 30.minutes).inWholeMilliseconds + slack, millis)
    }

    /** Fall back: 1 November 2026 is 25 hours long. A fixed 24 would fire an hour early. */
    @Test
    fun `the long DST day is 25 hours, not 24`() {
        val millis = millisUntilParkTomorrow(at(2026, 11, 1, 0, 30))
        assertEquals((25.hours - 30.minutes).inWholeMilliseconds + slack, millis)
    }

    /** Park time, not device time — the whole app agrees on Eastern. */
    @Test
    fun `the boundary is park midnight`() {
        // 00:30 UTC on the 16th is still 20:30 on the 15th in park time, so there should
        // be three and a half hours left of the day, not a day and a bit.
        val utcEarlyHours = LocalDateTime(2026, 9, 15, 20, 30).toInstant(ParkTimeZone)
        val millis = millisUntilParkTomorrow(utcEarlyHours)
        assertEquals((3.hours + 30.minutes).inWholeMilliseconds + slack, millis)
    }
}
