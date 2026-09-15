package contact.kaufman.parks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingAreasTest {

    @Test
    fun `a fix in a Magic Kingdom lot names the section, not just the park`() {
        val area = ParkingAreas.at(28.398351, -81.576938)
        assertEquals(Park.MAGIC_KINGDOM, area?.park)
        assertEquals("Ursula", area?.lot)
    }

    @Test
    fun `sections resolve at every Disney park`() {
        assertEquals("Moana", ParkingAreas.at(28.378406, -81.548277)?.lot)
        assertEquals("Mickey", ParkingAreas.at(28.356608, -81.556136)?.lot)
        assertEquals("Yeti", ParkingAreas.at(28.350118, -81.584794)?.lot)
    }

    /**
     * The whole reason this table exists separately from `ParkBoundaries`: knowing which
     * park you are at does not tell you which lot you left the car in.
     */
    @Test
    fun `the Universal garages are told apart rather than collapsed`() {
        val north = ParkingAreas.at(28.475643, -81.461060)
        val south = ParkingAreas.at(28.472034, -81.462505)
        assertEquals("North Garage", north?.group)
        assertEquals("South Garage", south?.group)
    }

    /**
     * Both garages serve USF and Islands of Adventure from the same walkway, so the shape
     * genuinely does not determine the park. Saying so beats a coin flip.
     */
    @Test
    fun `a Universal garage does not claim to know which park you are visiting`() {
        val area = ParkingAreas.at(28.472034, -81.462505)
        assertNotNull(area)
        assertNull(area?.park)
        assertNull(area?.lot)
    }

    /** Epic Universe's big lots are mapped, its sections are not — park only, no guess. */
    @Test
    fun `an unnamed Epic Universe lot fills the park and leaves the section blank`() {
        val area = ParkingAreas.at(28.439577, -81.441183)
        assertEquals(Park.EPIC_UNIVERSE, area?.park)
        assertNull(area?.lot)
    }

    @Test
    fun `somewhere that is not a park car park matches nothing`() {
        // Downtown Orlando, about 20km up I-4.
        assertNull(ParkingAreas.at(28.5384, -81.3789))
        // The middle of Magic Kingdom itself — you are not parked on Main Street.
        assertNull(ParkingAreas.at(28.4177, -81.5812))
    }

    /**
     * Lots abut along their tram aisles, so a fix a few metres outside one is still that
     * lot. Far outside stays unmatched rather than snapping to the nearest thing on earth.
     */
    @Test
    fun `a fix just outside an edge still resolves, a distant one does not`() {
        val area = ParkingAreas.all.single { it.osmName == "Ursula" }
        val onEdge = Geo.distanceToRingMeters(area.ring, 28.398351, -81.576938)
        assertTrue("test point should be well inside its lot", onEdge > ParkingAreas.NEAR_TOLERANCE_METERS)
        // 300m due north of Magic Kingdom's lots is the tram road and then water.
        assertNull(ParkingAreas.at(28.4100, -81.576938))
    }

    /**
     * The chips offered on the parking screen come from [ParkingLots]; a polygon labelled
     * with a lot that is not in that table would prefill a value the UI cannot display.
     */
    @Test
    fun `every mapped section is a section the parking screen offers`() {
        for (area in ParkingAreas.all) {
            val lot = area.lot ?: continue
            val park = requireNotNull(area.park) { "${area.osmName} names a lot but no park" }
            assertTrue(
                "${area.osmName} -> '$lot' is not a lot ParkingLots offers for $park",
                lot in ParkingLots.lotsFor(park),
            )
        }
    }

    @Test
    fun `every mapped group is a group the parking screen offers`() {
        for (area in ParkingAreas.all) {
            val group = area.group ?: continue
            val parks = area.park?.let(::listOf)
                ?: listOf(Park.UNIVERSAL_STUDIOS_FLORIDA, Park.ISLANDS_OF_ADVENTURE)
            assertTrue(
                "${area.osmName} -> '$group' is not a group ParkingLots offers",
                parks.all { park -> ParkingLots.groupsFor(park).any { it.name == group } },
            )
        }
    }

    @Test
    fun `ring containment handles a simple square`() {
        val square = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0)
        assertTrue(Geo.ringContains(square, 0.5, 0.5))
        assertTrue(!Geo.ringContains(square, 1.5, 0.5))
        assertTrue(!Geo.ringContains(square, 0.5, 1.5))
    }
}
