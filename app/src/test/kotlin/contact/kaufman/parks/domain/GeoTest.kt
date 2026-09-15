package contact.kaufman.parks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun `distance between Magic Kingdom and EPCOT is about four miles`() {
        val meters = Geo.distanceMeters(
            Park.MAGIC_KINGDOM.latitude, Park.MAGIC_KINGDOM.longitude,
            Park.EPCOT.latitude, Park.EPCOT.longitude,
        )
        // ~6.4 km on the ground; allow a wide band since these are park centroids.
        assertTrue("got $meters m", meters in 5_000.0..7_500.0)
    }

    @Test
    fun `zero distance to itself`() {
        val meters = Geo.distanceMeters(28.4177, -81.5812, 28.4177, -81.5812)
        assertEquals(0.0, meters, 0.001)
    }

    @Test
    fun `standing in a park identifies it`() {
        assertEquals(Park.EPCOT, Geo.parkAt(Park.EPCOT.latitude, Park.EPCOT.longitude))
        assertEquals(
            Park.ANIMAL_KINGDOM,
            Geo.parkAt(Park.ANIMAL_KINGDOM.latitude, Park.ANIMAL_KINGDOM.longitude),
        )
    }

    @Test
    fun `Celebration is not a theme park`() {
        // Home. Roughly six miles from the nearest park.
        assertNull(Geo.parkAt(28.3255, -81.5370))
    }

    @Test
    fun `Orlando International Airport is not a theme park`() {
        assertNull(Geo.parkAt(28.4312, -81.3081))
    }

    /**
     * USF and Islands of Adventure still have close centres — they share a wall, so they
     * always will. What changed is that it no longer decides anything: `parkAt` reads real
     * boundaries. This stays as a reminder of why, so nobody reintroduces a radius test.
     */
    @Test
    fun `Universal Studios and Islands of Adventure are uncomfortably close`() {
        val meters = Geo.distanceMeters(
            Park.UNIVERSAL_STUDIOS_FLORIDA.latitude, Park.UNIVERSAL_STUDIOS_FLORIDA.longitude,
            Park.ISLANDS_OF_ADVENTURE.latitude, Park.ISLANDS_OF_ADVENTURE.longitude,
        )
        assertTrue("centres are $meters m apart", meters < 1_000.0)
    }

    @Test
    fun `an entity without coordinates has no distance`() {
        val entity = ParkEntity(
            id = "x", name = "Nowhere", kind = EntityKind.ATTRACTION,
            park = Park.EPCOT, status = OperatingStatus.OPERATING,
        )
        assertNull(entity.distanceMetersFrom(28.0, -81.0))
    }
}
