package contact.kaufman.parks.data.crowd

import contact.kaufman.parks.data.db.AttractionAverage
import contact.kaufman.parks.data.db.DailyWaitAverageDao
import contact.kaufman.parks.data.db.DailyWaitAverageEntity
import contact.kaufman.parks.data.db.WaitSampleDao
import contact.kaufman.parks.data.db.WaitSampleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitHistoryTest {

    private fun day(date: String, average: Float, samples: Int) = DailyWaitAverageEntity(
        attractionId = "ride",
        parkId = "park",
        parkDate = date,
        averageMinutes = average,
        sampleCount = samples,
        dayOfWeek = 1,
        month = 9,
    )

    private fun baselines(rows: List<DailyWaitAverageEntity>) =
        CrowdBaselines(NoOpWaitSamples, FakeDailyAverages(rows))

    /**
     * A day with one glance recorded is not a day's average. Plotting it as one would let
     * a single walk-past reading stand beside days of real observation.
     */
    @Test
    fun `thinly sampled days are excluded`() = runTest {
        val history = baselines(
            listOf(
                day("2026-09-07", 150f, 1),
                day("2026-09-08", 62f, 11),
                day("2026-09-09", 55f, 10),
            )
        ).history("ride")

        assertEquals(2, history.size)
        assertTrue(history.none { it.averageMinutes == 150f })
    }

    @Test
    fun `history is returned oldest first so a chart reads left to right`() = runTest {
        val history = baselines(
            listOf(
                day("2026-09-12", 83f, 11),
                day("2026-09-08", 62f, 11),
                day("2026-09-10", 58f, 12),
            )
        ).history("ride")

        assertEquals(
            listOf("2026-09-08", "2026-09-10", "2026-09-12"),
            history.map { it.date.toString() },
        )
    }

    @Test
    fun `a ride the app has never watched has no history`() = runTest {
        assertTrue(baselines(emptyList()).history("ride").isEmpty())
    }

    @Test
    fun `averages and sample counts survive the mapping`() = runTest {
        val history = baselines(listOf(day("2026-09-08", 62.5f, 11))).history("ride")
        assertEquals(62.5f, history.single().averageMinutes, 0.001f)
        assertEquals(11, history.single().sampleCount)
    }
}

private class FakeDailyAverages(private val rows: List<DailyWaitAverageEntity>) : DailyWaitAverageDao {
    override suspend fun upsertAll(rows: List<DailyWaitAverageEntity>) = Unit
    override suspend fun recordedBaselines(
        parkId: String,
        parkDate: String,
        month: Int,
        dayOfWeek: Int,
        minObservations: Int,
    ): List<AttractionAverage> = emptyList()
    override fun observedDayCount(parkId: String): Flow<Int> = flowOf(0)
    override suspend fun recentDays(attractionId: String, limit: Int): List<DailyWaitAverageEntity> =
        rows.sortedByDescending { it.parkDate }.take(limit)
}

private object NoOpWaitSamples : WaitSampleDao {
    override suspend fun insertAll(samples: List<WaitSampleEntity>) = Unit
    override suspend fun middayAverages(
        parkId: String,
        parkDate: String,
        startHour: Int,
        endHour: Int,
    ): List<AttractionAverage> = emptyList()
    override suspend fun pruneOlderThan(cutoffEpochSeconds: Long): Int = 0
}
