package contact.kaufman.parks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class BestTimeAheadTest {

    private val now = Instant.parse("2026-09-14T15:00:00-04:00")

    private fun hour(h: Int) = Instant.parse("2026-09-14T%02d:00:00-04:00".format(h))

    private fun ride(
        standby: Int?,
        forecast: List<Pair<Int, Int?>>,
    ) = ParkEntity(
        id = "x",
        name = "Test",
        kind = EntityKind.ATTRACTION,
        park = Park.MAGIC_KINGDOM,
        status = OperatingStatus.OPERATING,
        queues = listOfNotNull(standby?.let { Queue.Standby(it) }),
        forecast = forecast.map { (h, wait) -> ForecastPoint(hour(h), wait, null) },
    )

    /** Universal sends no forecast at all; the feature must simply not apply. */
    @Test
    fun `no forecast means no advice`() {
        assertNull(ride(standby = 45, forecast = emptyList()).bestTimeAhead(now))
    }

    @Test
    fun `a real dip later is surfaced`() {
        val entity = ride(standby = 45, forecast = listOf(16 to 45, 18 to 40, 22 to 25))
        val best = entity.bestTimeAhead(now)
        assertNotNull(best)
        assertEquals(25, best!!.waitMinutes)
        assertEquals(hour(22), best.time)
    }

    /** Sending someone across the park to save five minutes is bad advice. */
    @Test
    fun `a trivial saving stays quiet`() {
        assertNull(ride(standby = 30, forecast = listOf(16 to 28, 18 to 26, 22 to 25)).bestTimeAhead(now))
    }

    @Test
    fun `a dip that has already passed is not advice`() {
        // The 10am trough is gone; everything still ahead is worse than now.
        val entity = ride(standby = 45, forecast = listOf(10 to 5, 16 to 50, 18 to 55))
        assertNull(entity.bestTimeAhead(now))
    }

    /** If the queue is already shorter than anything forecast, do not talk someone out of it. */
    @Test
    fun `an unusually short line now is not overridden`() {
        assertNull(ride(standby = 10, forecast = listOf(16 to 40, 18 to 35, 22 to 30)).bestTimeAhead(now))
    }

    @Test
    fun `hours with no posted wait are ignored rather than read as zero`() {
        val entity = ride(standby = 45, forecast = listOf(16 to null, 18 to null, 22 to 20))
        // Only one usable hour ahead: too thin a curve to call a trough.
        assertNull(entity.bestTimeAhead(now))
    }

    @Test
    fun `the threshold is honoured exactly`() {
        val forecast = listOf(16 to 45, 22 to 35)
        assertNotNull(ride(standby = 45, forecast = forecast).bestTimeAhead(now, minimumSavingMinutes = 10))
        assertNull(ride(standby = 45, forecast = forecast).bestTimeAhead(now, minimumSavingMinutes = 11))
    }
}
