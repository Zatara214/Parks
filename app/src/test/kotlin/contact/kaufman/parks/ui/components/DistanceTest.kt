package contact.kaufman.parks.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {

    @Test
    fun `short distances read in feet, rounded to ten`() {
        assertEquals("160 ft", formatWalkingDistance(48.0))
        assertEquals("330 ft", formatWalkingDistance(100.0))
    }

    /** "0 ft" reads like a bug, and no fix is accurate enough to justify "3 ft". */
    @Test
    fun `standing on top of it still says ten feet`() {
        assertEquals("10 ft", formatWalkingDistance(0.0))
        assertEquals("10 ft", formatWalkingDistance(1.0))
    }

    @Test
    fun `longer distances switch to miles`() {
        assertEquals("0.6 mi", formatWalkingDistance(1000.0))
        assertEquals("1.2 mi", formatWalkingDistance(2000.0))
    }

    /** A fifth of a mile is the switchover; it must not skip or repeat a value. */
    @Test
    fun `the switchover is continuous`() {
        assertEquals("1050 ft", formatWalkingDistance(320.0))
        assertEquals("0.2 mi", formatWalkingDistance(325.0))
    }
}
