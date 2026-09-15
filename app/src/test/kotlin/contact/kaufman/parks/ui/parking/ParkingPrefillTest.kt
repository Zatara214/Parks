package contact.kaufman.parks.ui.parking

import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkingAreas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prefill is an assist. These pin the cases where it has to keep its hands off, which
 * is the half that is easy to lose in a refactor — the filling-in half is obvious.
 */
class ParkingPrefillTest {

    private fun area(osmName: String) = ParkingAreas.all.single { it.osmName == osmName }

    @Test
    fun `a Disney lot fills in both the park and the section`() {
        val form = ParkingFormState().withDetected(area("Ursula"))
        assertEquals(Park.MAGIC_KINGDOM, form.park)
        assertEquals("Ursula", form.lot)
    }

    @Test
    fun `a park already chosen by hand is never replaced`() {
        val form = ParkingFormState(park = Park.EPCOT).withDetected(area("Ursula"))
        assertEquals(Park.EPCOT, form.park)
    }

    /** EPCOT has no lot called Ursula, so prefilling it would put a dead name in the field. */
    @Test
    fun `a section is not carried into a park that has no such section`() {
        val form = ParkingFormState(park = Park.EPCOT).withDetected(area("Ursula"))
        assertEquals("", form.lot)
    }

    @Test
    fun `a section already chosen by hand is never replaced`() {
        val form = ParkingFormState(park = Park.MAGIC_KINGDOM, lot = "Hook").withDetected(area("Ursula"))
        assertEquals("Hook", form.lot)
    }

    @Test
    fun `a Universal garage leaves the park for the user to pick`() {
        val form = ParkingFormState().withDetected(area("Structure South"))
        assertNull(form.park)
        assertEquals("", form.lot)
        assertEquals("South Garage", form.detected?.group)
    }

    @Test
    fun `the row and the level are never guessed`() {
        val form = ParkingFormState(row = "57", level = "4").withDetected(area("Structure South"))
        assertEquals("57", form.row)
        assertEquals("4", form.level)
    }

    @Test
    fun `a fix that matches nothing is reported rather than silently dropped`() {
        val form = ParkingFormState().withDetected(null)
        assertTrue(form.detectionMissed)
        assertFalse(form.locating)
        assertNull(form.park)
    }

    @Test
    fun `a successful detection clears the locating flag and the miss`() {
        val form = ParkingFormState(locating = true, detectionMissed = true).withDetected(area("Moana"))
        assertFalse(form.locating)
        assertFalse(form.detectionMissed)
    }
}
