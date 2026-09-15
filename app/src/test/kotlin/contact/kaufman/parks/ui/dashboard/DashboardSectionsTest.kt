package contact.kaufman.parks.ui.dashboard

import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkSnapshot
import contact.kaufman.parks.domain.Resort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSectionsTest {

    private val all = Park.entries.map { ParkSnapshot(it) }

    @Test
    fun `with no fix the list is just the two resorts`() {
        val sections = dashboardSections(all, youAreHere = null)
        assertEquals(listOf("Walt Disney World", "Universal Orlando"), sections.map { it.title })
        assertEquals(Park.entries.size, sections.sumOf { it.snapshots.size })
    }

    @Test
    fun `the park you are in comes first`() {
        val sections = dashboardSections(all, youAreHere = Park.EPCOT)
        assertEquals("Where you are", sections.first().title)
        assertEquals(Park.EPCOT, sections.first().snapshots.single().park)
        assertTrue(sections.first().isHere)
    }

    /** The bug this function exists to prevent. */
    @Test
    fun `the park you are in is not listed twice`() {
        val sections = dashboardSections(all, youAreHere = Park.EPCOT)
        val appearances = sections.flatMap { it.snapshots }.count { it.park == Park.EPCOT }
        assertEquals(1, appearances)
        assertEquals(Park.entries.size, sections.sumOf { it.snapshots.size })
    }

    @Test
    fun `the rest of the resort still appears below`() {
        val sections = dashboardSections(all, youAreHere = Park.EPCOT)
        val disney = sections.single { it.title == "Walt Disney World" }
        val expected = Park.entries.count { it.resort == Resort.WALT_DISNEY_WORLD } - 1
        assertEquals(expected, disney.snapshots.size)
        assertFalse(disney.snapshots.any { it.park == Park.EPCOT })
    }

    /** With parks hidden in settings, a resort can empty out entirely. */
    @Test
    fun `a resort with nothing left drops its header`() {
        val onlyUniversal = all.filter { it.park.resort == Park.EPIC_UNIVERSE.resort }
        val sections = dashboardSections(onlyUniversal, youAreHere = null)
        assertEquals(listOf("Universal Orlando"), sections.map { it.title })
    }

    @Test
    fun `a resort emptied by the pinned park drops its header`() {
        val disneyOnlyPlusOne = all.filter {
            it.park.resort == Park.MAGIC_KINGDOM.resort || it.park == Park.EPIC_UNIVERSE
        }
        val sections = dashboardSections(disneyOnlyPlusOne, youAreHere = Park.EPIC_UNIVERSE)
        assertEquals(listOf("Where you are", "Walt Disney World"), sections.map { it.title })
    }

    @Test
    fun `a fix outside every park changes nothing`() {
        assertEquals(
            dashboardSections(all, youAreHere = null),
            dashboardSections(all, youAreHere = null),
        )
    }
}
