package contact.kaufman.parks.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TripLengthTest {

    @Test
    fun `hours and minutes together`() {
        assertEquals("8h 28m", (8.hours + 28.minutes).toTripLength())
    }

    @Test
    fun `under an hour is minutes only`() {
        assertEquals("45m", 45.minutes.toTripLength())
    }

    /** "8h 0m" reads like a machine wrote it. */
    @Test
    fun `a whole number of hours drops the minutes`() {
        assertEquals("8h", 8.hours.toTripLength())
    }

    @Test
    fun `minutes past several hours do not overflow into the hour count`() {
        assertEquals("10h 5m", (10.hours + 5.minutes).toTripLength())
    }
}
