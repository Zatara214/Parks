package contact.kaufman.parks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coordinates are the real ones themeparks.wiki publishes for these attractions. */
class ParkLandsTest {

    /** A dining district has no lands, and the chip row keys off exactly this. */
    @Test
    fun `Disney Springs has no lands, which is not a gap`() {
        assertTrue(ParkLands.landsIn(Park.DISNEY_SPRINGS).isEmpty())
    }

    @Test
    fun `rides land in the land they are actually in`() {
        assertEquals("Fantasyland", ParkLands.landAt(Park.MAGIC_KINGDOM, 28.42037, -81.58031))
        assertEquals("Tomorrowland", ParkLands.landAt(Park.MAGIC_KINGDOM, 28.4188341691, -81.5781962872))
        assertEquals("Adventureland", ParkLands.landAt(Park.MAGIC_KINGDOM, 28.4179699235, -81.5842252029))
        assertEquals("Liberty Square", ParkLands.landAt(Park.MAGIC_KINGDOM, 28.4202, -81.58288))
    }

    /**
     * Storybook Circus sits inside Fantasyland's outline, so both contain The Barnstormer.
     * The tighter answer is the useful one.
     */
    @Test
    fun `the smallest containing land wins`() {
        assertEquals(
            "Storybook Circus",
            ParkLands.landAt(Park.MAGIC_KINGDOM, 28.4207661576, -81.5783907473),
        )
    }

    /** Lands are per-park; a Magic Kingdom coordinate is in none of EPCOT's. */
    @Test
    fun `a coordinate is only matched against its own park's lands`() {
        assertNull(ParkLands.landAt(Park.EPCOT, 28.42037, -81.58031))
    }

    @Test
    fun `somewhere outside every land has no land`() {
        // The Seven Seas Lagoon, between the car park and the gate.
        assertNull(ParkLands.landAt(Park.MAGIC_KINGDOM, 28.4110, -81.5810))
    }

    /**
     * Every park currently has mapped lands, but the UI must still cope with one that does
     * not — this pins that `landsIn` is the signal, so a park dropping out of OSM turns the
     * filter off rather than drawing an empty row.
     */
    @Test
    fun `every park reports its own lands and only its own`() {
        // Disney Springs is not a theme park and has no lands; it is not a coverage gap.
        for (park in Park.entries.filter { it.kind == ParkKind.THEME_PARK }) {
            val names = ParkLands.landsIn(park)
            assertTrue("${park.displayName} has no mapped lands", names.isNotEmpty())
            assertTrue(
                "${park.displayName} lists a land belonging to another park",
                ParkLands.all.filter { it.name in names }.all { it.park == park } ||
                    // Two parks may legitimately share a land name ("Hollywood" exists at
                    // Universal Studios only today, but this should not be brittle).
                    names.all { name -> ParkLands.all.any { it.park == park && it.name == name } },
            )
        }
    }

    @Test
    fun `land names are distinct within a park`() {
        for (park in Park.entries) {
            val names = ParkLands.landsIn(park)
            assertEquals(names.size, names.distinct().size)
        }
    }

    @Test
    fun `every ring is a usable polygon`() {
        for (land in ParkLands.all) {
            assertTrue(
                "${land.name} has ${land.ring.size} values",
                land.ring.size >= 6 && land.ring.size % 2 == 0,
            )
        }
    }
}
