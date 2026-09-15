package contact.kaufman.parks.ui.park

import contact.kaufman.parks.domain.EntityKind
import contact.kaufman.parks.domain.OperatingStatus
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkEntity
import contact.kaufman.parks.domain.ParkSnapshot
import contact.kaufman.parks.domain.Queue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Magic Kingdom's centre is 28.4177, -81.5812. These rides sit due north of it at known
 * spacings, so the expected order is arithmetic rather than a guess.
 */
class NearbyRidesTest {

    private fun ride(
        name: String,
        latitude: Double? = null,
        longitude: Double? = null,
        wait: Int? = 30,
    ) = ParkEntity(
        id = name,
        name = name,
        kind = EntityKind.ATTRACTION,
        park = Park.MAGIC_KINGDOM,
        status = OperatingStatus.OPERATING,
        queues = listOf(Queue.Standby(wait)),
        latitude = latitude,
        longitude = longitude,
    )

    private val near = ride("Near", 28.4180, -81.5812, wait = 5)
    private val middle = ride("Middle", 28.4200, -81.5812, wait = 90)
    private val far = ride("Far", 28.4250, -81.5812, wait = 45)
    private val unmapped = ride("Unmapped", wait = 120)

    private val standingAtTheCastle = InParkFix(28.4177, -81.5812)

    private fun state(sort: RideSort, fix: InParkFix?) = ParkDetailUiState(
        snapshot = ParkSnapshot(Park.MAGIC_KINGDOM, entities = listOf(far, unmapped, near, middle)),
        sort = sort,
        fix = fix,
    )

    @Test
    fun `nearby orders rides by how far away they are`() {
        val visible = state(RideSort.NEARBY, standingAtTheCastle).visibleEntities()
        assertEquals(listOf("Near", "Middle", "Far", "Unmapped"), visible.map { it.name })
    }

    /**
     * Same reasoning as a ride with no posted wait: an entity with no coordinates goes to
     * the bottom rather than pretending to be nought feet away and topping the list.
     */
    @Test
    fun `a ride with no coordinates sinks to the bottom`() {
        val visible = state(RideSort.NEARBY, standingAtTheCastle).visibleEntities()
        assertEquals("Unmapped", visible.last().name)
    }

    /**
     * The chip is hidden without a fix, but the selected sort is remembered, so this state
     * is reachable by losing location while Nearby is active. An arbitrary order would be
     * worse than the default one.
     */
    @Test
    fun `nearby with no fix falls back to the wait order`() {
        val visible = state(RideSort.NEARBY, fix = null).visibleEntities()
        assertEquals(listOf("Unmapped", "Middle", "Far", "Near"), visible.map { it.name })
    }

    @Test
    fun `the nearby chip is offered only once there is a fix in this park`() {
        assertFalse(RideSort.NEARBY in ParkDetailUiState().availableSorts())
        assertTrue(RideSort.NEARBY in ParkDetailUiState(fix = standingAtTheCastle).availableSorts())
    }

    @Test
    fun `the other sorts are always offered`() {
        assertEquals(listOf(RideSort.WAIT, RideSort.NAME), ParkDetailUiState().availableSorts())
    }

    /** Distance is a ride-list idea; the other tabs stay alphabetical whatever is selected. */
    @Test
    fun `nearby does not reorder the other tabs`() {
        val base = state(RideSort.NEARBY, standingAtTheCastle)
        assertTrue(base.copy(tab = ParkTab.SHOWS).visibleEntities().isEmpty())
    }
}
