package contact.kaufman.parks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class ParkingDayTest {

    /** Park-local wall clock to an Instant. EDT is -04:00, EST is -05:00. */
    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun `a spot parked in the evening is still current after midnight`() {
        // Parked 9PM during a hard-ticket night, checked at 12:30AM on the way out.
        val parked = at("2026-09-14T21:00:00-04:00")
        val now = at("2026-09-15T00:30:00-04:00")
        assertFalse(ParkingDay.hasExpired(parked.epochSeconds, now))
    }

    @Test
    fun `a spot parked in the evening is still current at 3am`() {
        val parked = at("2026-09-14T21:00:00-04:00")
        val now = at("2026-09-15T03:59:00-04:00")
        assertFalse(ParkingDay.hasExpired(parked.epochSeconds, now))
    }

    @Test
    fun `last night's spot is gone by breakfast`() {
        val parked = at("2026-09-14T21:00:00-04:00")
        val now = at("2026-09-15T08:00:00-04:00")
        assertTrue(ParkingDay.hasExpired(parked.epochSeconds, now))
    }

    @Test
    fun `the rollover bites exactly at 4am`() {
        val parked = at("2026-09-14T21:00:00-04:00")
        assertFalse(ParkingDay.hasExpired(parked.epochSeconds, at("2026-09-15T03:59:59-04:00")))
        assertTrue(ParkingDay.hasExpired(parked.epochSeconds, at("2026-09-15T04:00:01-04:00")))
    }

    @Test
    fun `a spot parked this morning after the rollover is current`() {
        val parked = at("2026-09-15T09:00:00-04:00")
        val now = at("2026-09-15T22:00:00-04:00")
        assertFalse(ParkingDay.hasExpired(parked.epochSeconds, now))
    }

    /**
     * Spring forward: 2026-03-08 has no 2AM hour, so that local day is 23 hours long.
     * Subtracting a fixed 24 hours from 4AM on the 8th lands at 3AM on the 7th and
     * expires a spot an hour early.
     */
    @Test
    fun `spring forward does not shift the rollover`() {
        val rollover = ParkingDay.mostRecentRollover(at("2026-03-08T01:00:00-05:00"))
        assertEquals(at("2026-03-07T04:00:00-05:00"), rollover)
    }

    /** Autumn back: 2026-11-01 repeats the 1AM hour, making that local day 25 hours. */
    @Test
    fun `fall back does not shift the rollover`() {
        val rollover = ParkingDay.mostRecentRollover(at("2026-11-01T01:00:00-04:00"))
        assertEquals(at("2026-10-31T04:00:00-04:00"), rollover)
    }
}
