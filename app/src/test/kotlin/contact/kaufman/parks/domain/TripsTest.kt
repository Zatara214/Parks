package contact.kaufman.parks.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TripsTest {

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime(2026, 9, day, hour, minute).toInstant(ParkingDay.TIME_ZONE)

    private fun sighting(park: Park, day: Int, hour: Int, minute: Int = 0) = park to at(day, hour, minute)

    @Test
    fun `a day at one park is one visit spanning first to last sighting`() {
        val days = groupSightings(
            listOf(
                sighting(Park.MAGIC_KINGDOM, 15, 9, 12),
                sighting(Park.MAGIC_KINGDOM, 15, 13, 0),
                sighting(Park.MAGIC_KINGDOM, 15, 19, 40),
            ),
        )
        val visit = days.single().visits.single()
        assertEquals(Park.MAGIC_KINGDOM, visit.park)
        assertEquals(at(15, 9, 12), visit.firstSeen)
        assertEquals(at(15, 19, 40), visit.lastSeen)
        assertEquals(3, visit.sightings)
        assertFalse(days.single().isParkHop)
    }

    @Test
    fun `two parks on one day is a park hop, in the order visited`() {
        val days = groupSightings(
            listOf(
                sighting(Park.EPCOT, 15, 16, 0),
                sighting(Park.MAGIC_KINGDOM, 15, 9, 30),
            ),
        )
        val day = days.single()
        assertTrue(day.isParkHop)
        assertEquals(listOf(Park.MAGIC_KINGDOM, Park.EPCOT), day.visits.map { it.park })
    }

    /**
     * The decision this pins: a long gap does **not** start a new visit. Sightings only
     * happen when the app is opened, so hours of silence in the middle of a day at one
     * park are ordinary. An earlier version split on a three-hour gap and turned exactly
     * this day into three visits.
     */
    @Test
    fun `hours of silence in the middle of a day stay one visit`() {
        val days = groupSightings(
            listOf(
                sighting(Park.MAGIC_KINGDOM, 15, 9, 12),
                sighting(Park.MAGIC_KINGDOM, 15, 13, 0),
                sighting(Park.MAGIC_KINGDOM, 15, 19, 40),
            ),
        )
        val visit = days.single().visits.single()
        assertEquals(at(15, 9, 12), visit.firstSeen)
        assertEquals(at(15, 19, 40), visit.lastSeen)
        assertEquals(3, visit.sightings)
    }

    /**
     * The 4AM rollover, shared with parking. Leaving a Halloween party at 00:30 is part of
     * that evening's trip, not the next morning's.
     */
    @Test
    fun `the small hours belong to the night before`() {
        val days = groupSightings(
            listOf(
                sighting(Park.MAGIC_KINGDOM, 15, 22, 0),
                sighting(Park.MAGIC_KINGDOM, 16, 0, 30),
            ),
        )
        assertEquals(1, days.size)
        assertEquals(LocalDate(2026, 9, 15), days.single().date)
        assertEquals(1, days.single().visits.size)
    }

    @Test
    fun `a morning arrival after the rollover is its own day`() {
        val days = groupSightings(
            listOf(
                sighting(Park.MAGIC_KINGDOM, 15, 22, 0),
                sighting(Park.MAGIC_KINGDOM, 16, 9, 0),
            ),
        )
        assertEquals(listOf(LocalDate(2026, 9, 16), LocalDate(2026, 9, 15)), days.map { it.date })
    }

    @Test
    fun `days are listed newest first`() {
        val days = groupSightings(
            listOf(
                sighting(Park.EPCOT, 13, 10, 0),
                sighting(Park.EPCOT, 15, 10, 0),
                sighting(Park.EPCOT, 14, 10, 0),
            ),
        )
        assertEquals(
            listOf(LocalDate(2026, 9, 15), LocalDate(2026, 9, 14), LocalDate(2026, 9, 13)),
            days.map { it.date },
        )
    }

    @Test
    fun `a parking spot is attached to its own day and no other`() {
        val days = groupSightings(
            sightings = listOf(
                sighting(Park.MAGIC_KINGDOM, 15, 10, 0),
                sighting(Park.MAGIC_KINGDOM, 14, 10, 0),
            ),
            parkingByDay = mapOf(
                LocalDate(2026, 9, 15) to ParkedSpot(Park.MAGIC_KINGDOM, "Ursula", "42"),
            ),
        )
        assertEquals("Ursula", days.first { it.date == LocalDate(2026, 9, 15) }.parking?.lot)
        assertNull(days.first { it.date == LocalDate(2026, 9, 14) }.parking)
    }

    /**
     * One sighting is a moment, not a duration. The UI shows a time instead of a span, so
     * it never claims a nought-minute visit.
     */
    @Test
    fun `a single sighting has no meaningful duration`() {
        val visit = groupSightings(listOf(sighting(Park.EPCOT, 15, 14, 0))).single().visits.single()
        assertEquals(1, visit.sightings)
        assertFalse(visit.hasMeaningfulDuration)
    }

    @Test
    fun `two sightings minutes apart also read as a moment`() {
        val visit = groupSightings(
            listOf(sighting(Park.EPCOT, 15, 14, 0), sighting(Park.EPCOT, 15, 14, 5)),
        ).single().visits.single()
        assertFalse(visit.hasMeaningfulDuration)
    }

    @Test
    fun `a real day out has a duration worth showing`() {
        val visit = groupSightings(
            listOf(sighting(Park.EPCOT, 15, 9, 0), sighting(Park.EPCOT, 15, 17, 28)),
        ).single().visits.single()
        assertTrue(visit.hasMeaningfulDuration)
        assertEquals(8.hours + 28.minutes, visit.duration)
    }

    @Test
    fun `no sightings means no days rather than an empty day`() {
        assertTrue(groupSightings(emptyList()).isEmpty())
    }
}
