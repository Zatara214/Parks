package contact.kaufman.parks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkBoundariesTest {

    @Test
    fun `every park has a boundary`() {
        val mapped = ParkBoundaries.parks.mapNotNull { it.park }.toSet()
        assertEquals(Park.entries.toSet(), mapped)
    }

    /**
     * The guard on a regeneration. If `tools/park-boundaries.py` ever fetched the wrong
     * shape — a renamed element, a relation replacing a way — the park's own declared
     * coordinate would stop being inside it, and this is where that shows up.
     */
    @Test
    fun `each park's declared coordinate is inside its own boundary`() {
        for (park in Park.entries) {
            assertEquals(
                "${park.displayName}'s coordinate is not inside its mapped boundary",
                park,
                ParkBoundaries.parkAt(park.latitude, park.longitude),
            )
        }
    }

    /**
     * The whole point of the exercise. USF and Islands of Adventure share a wall, so the
     * old nearest-centre test could not separate them: it placed only 80.7% of Universal
     * Studios in the right park.
     */
    @Test
    fun `Universal Studios and Islands of Adventure are told apart`() {
        // Inside USF, north of the shared boundary — Hollywood / Springfield end.
        assertEquals(Park.UNIVERSAL_STUDIOS_FLORIDA, ParkBoundaries.parkAt(28.4790, -81.4680))
        // Inside Islands of Adventure — Jurassic Park / Toon Lagoon end.
        assertEquals(Park.ISLANDS_OF_ADVENTURE, ParkBoundaries.parkAt(28.4705, -81.4715))
    }

    /**
     * The bug this fixes: standing in CityWalk, the old test reported a park 100% of the
     * time. CityWalk touches both Universal parks, so it has to be an explicit answer.
     */
    @Test
    fun `CityWalk is not a park`() {
        val citywalk = ParkBoundaries.at(28.4733, -81.4665)
        assertNotNull("CityWalk should be a known place", citywalk)
        assertNull("CityWalk must not resolve to a park", citywalk?.park)
        assertEquals("Universal CityWalk Orlando", citywalk?.osmName)
        assertNull(ParkBoundaries.parkAt(28.4733, -81.4665))
    }

    @Test
    fun `nowhere near a park is still nowhere`() {
        // Home, in Celebration.
        assertNull(ParkBoundaries.at(28.3255, -81.5370))
        // Orlando International Airport.
        assertNull(ParkBoundaries.at(28.4312, -81.3081))
        // Downtown Orlando.
        assertNull(ParkBoundaries.at(28.5384, -81.3789))
    }

    /** SeaWorld is where Epic Universe's coordinate used to wrongly point. */
    @Test
    fun `SeaWorld is not Epic Universe`() {
        assertNull(ParkBoundaries.parkAt(28.4104, -81.4611))
    }

    /**
     * A fix a few metres past a fence should still be the park it is against, both for GPS
     * drift and so a park that grows keeps working until the table is regenerated.
     */
    @Test
    fun `a fix just outside a boundary still resolves to that park`() {
        val magicKingdom = ParkBoundaries.parks.single { it.park == Park.MAGIC_KINGDOM }
        // Walk north from Magic Kingdom's coordinate until just outside the ring.
        var latitude = Park.MAGIC_KINGDOM.latitude
        while (Geo.ringContains(magicKingdom.ring, latitude, Park.MAGIC_KINGDOM.longitude)) {
            latitude += 0.0001
        }
        val justOutside = latitude + 0.0002 // ~22 m clear of the edge
        assertTrue(!Geo.ringContains(magicKingdom.ring, justOutside, Park.MAGIC_KINGDOM.longitude))
        assertEquals(
            Park.MAGIC_KINGDOM,
            ParkBoundaries.parkAt(justOutside, Park.MAGIC_KINGDOM.longitude),
        )
    }

    /**
     * Tolerance must not turn into guessing. This point is outside both Universal parks
     * but within range of each — 76m from Universal Studios, 54m from Islands of
     * Adventure. Nearer is not the same as right, so the answer has to be neither.
     */
    @Test
    fun `tolerance never picks between two parks that are both in range`() {
        val latitude = 28.47520
        val longitude = -81.47395
        val usf = ParkBoundaries.parks.single { it.park == Park.UNIVERSAL_STUDIOS_FLORIDA }
        val ioa = ParkBoundaries.parks.single { it.park == Park.ISLANDS_OF_ADVENTURE }

        // Guard the fixture itself: if a regeneration moves these shapes so this point is
        // no longer ambiguous, the test below would quietly stop testing anything.
        assertTrue(
            "fixture should be outside both parks",
            ParkBoundaries.parks.none { Geo.ringContains(it.ring, latitude, longitude) },
        )
        assertTrue(
            "fixture should be within tolerance of both parks",
            Geo.distanceToRingMeters(usf.ring, latitude, longitude) <=
                ParkBoundaries.EXPANSION_TOLERANCE_METERS &&
                Geo.distanceToRingMeters(ioa.ring, latitude, longitude) <=
                ParkBoundaries.EXPANSION_TOLERANCE_METERS,
        )

        assertNull(ParkBoundaries.parkAt(latitude, longitude))
    }

    /** Shapes with no area, or a stray single point, would silently match nothing. */
    @Test
    fun `every ring is a usable polygon`() {
        for (boundary in ParkBoundaries.all) {
            assertTrue(
                "${boundary.osmName} has ${boundary.ring.size} values",
                boundary.ring.size >= 6 && boundary.ring.size % 2 == 0,
            )
        }
    }
}
