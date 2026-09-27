package contact.kaufman.parks.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkCalendarTest {

    private val zone = TimeZone.of("America/New_York")
    private val day = LocalDate(2026, 10, 2)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day) =
        LocalDateTime(date.year, date.month, date.day, hour, minute).toInstant(zone)

    private fun row(type: String, open: Int?, close: Int?, description: String? = null, date: LocalDate = day) =
        ParkHours(date, type, description, open?.let { at(it, date = date) }, close?.let { at(it, date = date) })

    private val regular = row("OPERATING", 9, 22)

    @Test
    fun `a normal day is open with its hours`() {
        val result = ParkCalendar.parkDay(Park.MAGIC_KINGDOM, day, listOf(regular))
        assertEquals(ParkDayStatus.OPEN, result.status)
        assertEquals(regular, result.hours)
    }

    /** Disney's Early Entry, whatever upstream calls its type, placed by its timing. */
    @Test
    fun `a session ending at opening is early entry, in the resort's words`() {
        val early = row("EXTRA_HOURS", 8, 9)
        assertEquals("Early Entry", ParkCalendar.parkDay(Park.MAGIC_KINGDOM, day, listOf(regular, early)).extras.single().label)
        assertEquals("Early Park Admission",
            ParkCalendar.parkDay(Park.ISLANDS_OF_ADVENTURE, day, listOf(regular, early)).extras.single().label)
    }

    @Test
    fun `a session starting at closing is an extended evening`() {
        val late = row("EXTRA_HOURS", 22, 23)
        assertEquals("Extended Evening", ParkCalendar.parkDay(Park.EPCOT, day, listOf(regular, late)).extras.single().label)
    }

    /** The feed's own name always wins — that is how hard-ticket nights arrive. */
    @Test
    fun `a named session keeps its name and a ticketed one is marked`() {
        val party = row("TICKETED_EVENT", 19, 23, "Mickey's Not-So-Scary Halloween Party")
        val extra = ParkCalendar.parkDay(Park.MAGIC_KINGDOM, day, listOf(row("OPERATING", 9, 18), party)).extras.single()
        assertEquals("Mickey's Not-So-Scary Halloween Party", extra.label)
        assertTrue(extra.isTicketed)
    }

    @Test
    fun `extras are listed in time order`() {
        val labels = ParkCalendar.parkDay(
            Park.MAGIC_KINGDOM, day,
            listOf(regular, row("EXTRA_HOURS", 22, 23), row("EXTRA_HOURS", 8, 9)),
        ).extras.map { it.label }
        assertEquals(listOf("Early Entry", "Extended Evening"), labels)
    }

    /**
     * The trap: a day past the last published date is unknown, not closed. Reading it as
     * closed would show a wall of "Closed" at the end of every calendar.
     */
    @Test
    fun `a day past the published schedule is not yet published, not closed`() {
        val later = LocalDate(2026, 10, 9)
        assertEquals(ParkDayStatus.NOT_YET_PUBLISHED, ParkCalendar.parkDay(Park.EPCOT, later, listOf(regular)).status)
    }

    @Test
    fun `a day inside the published range with no hours is closed`() {
        val nextWeek = row("OPERATING", 9, 22, date = LocalDate(2026, 10, 9))
        assertEquals(ParkDayStatus.CLOSED, ParkCalendar.parkDay(Park.EPCOT, day, listOf(nextWeek)).status)
    }

    @Test
    fun `a schedule that failed to load is unavailable`() {
        assertEquals(ParkDayStatus.UNAVAILABLE, ParkCalendar.parkDay(Park.EPCOT, day, null).status)
    }

    @Test
    fun `each day carries its own weather, and a day without one carries none`() {
        val outlook = listOf(DayOutlook(day, 91.0, 76.0, 40, 95))
        val days = ParkCalendar.build(
            listOf(day, LocalDate(2026, 10, 3)),
            mapOf(Park.MAGIC_KINGDOM to listOf(regular)),
            outlook,
        )
        assertEquals(40, days[0].weather?.rainChancePercent)
        assertNull(days[1].weather)
        assertEquals(Park.MAGIC_KINGDOM, days[0].parks.single().park)
    }
}
