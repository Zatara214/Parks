package contact.kaufman.parks.ui.components

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarHeadingTest {

    private val today = LocalDate(2026, 9, 27)   // a Sunday

    @Test
    fun `the first two days are named, not dated`() {
        assertEquals("Today", today.toCalendarHeading(today))
        assertEquals("Tomorrow", LocalDate(2026, 9, 28).toCalendarHeading(today))
    }

    @Test
    fun `later days read as a short weekday and date`() {
        assertEquals("Wed, Sep 30", LocalDate(2026, 9, 30).toCalendarHeading(today))
        assertEquals("Mon, Oct 5", LocalDate(2026, 10, 5).toCalendarHeading(today))
    }
}
