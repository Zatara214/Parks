package contact.kaufman.parks.data.repo

import contact.kaufman.parks.data.db.AttractionAverage
import contact.kaufman.parks.data.db.DailyWaitAverageDao
import contact.kaufman.parks.data.db.DailyWaitAverageEntity
import contact.kaufman.parks.data.db.WaitSampleDao
import contact.kaufman.parks.data.db.WaitSampleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Storage stubs for tests that drive the real [ParksRepository] over a mock HTTP engine.
 *
 * A repository test is about what comes off the wire, so the database is stubbed out
 * rather than faked: every read answers "nothing recorded yet", which is also the honest
 * state of a fresh install.
 */
internal object NoOpWaitSampleDao : WaitSampleDao {
    override suspend fun insertAll(samples: List<WaitSampleEntity>) = Unit
    override suspend fun middayAverages(
        parkId: String,
        parkDate: String,
        startHour: Int,
        endHour: Int,
    ): List<AttractionAverage> = emptyList()
    override suspend fun pruneOlderThan(cutoffEpochSeconds: Long): Int = 0
}

internal object NoOpDailyWaitAverageDao : DailyWaitAverageDao {
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
        emptyList()
}
