package contact.kaufman.parks.data.repo

import contact.kaufman.parks.data.db.ParkingDao
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkingDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
class ParkingRepository @Inject constructor(
    private val dao: ParkingDao,
) {
    /**
     * The current spot, or null once the parking day has rolled over.
     *
     * Filtered on read as well as expired in the database, so a spot that goes stale
     * while the app is open disappears on the next emission rather than lingering until
     * the next launch.
     */
    fun active(): Flow<ParkingRecordEntity?> = dao.active().map { record ->
        record?.takeUnless { ParkingDay.hasExpired(it.parkedAtEpochSeconds, Clock.System.now()) }
    }

    /** Retire spots from previous days. Cheap, idempotent, and safe to call on every
     *  screen that shows parking. */
    suspend fun expireStale() {
        dao.expireActiveBefore(ParkingDay.mostRecentRollover(Clock.System.now()).epochSeconds)
    }

    fun history(): Flow<List<ParkingRecordEntity>> = dao.history()

    fun recentRows(park: Park, lot: String): Flow<List<String>> = dao.recentRows(park.id, lot)

    suspend fun park(
        park: Park,
        lot: String,
        row: String,
        note: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
    ): Long = dao.park(
        ParkingRecordEntity(
            parkId = park.id,
            parkedAtEpochSeconds = Clock.System.now().epochSeconds,
            lot = lot.trim(),
            row = row.trim(),
            note = note.trim(),
            latitude = latitude,
            longitude = longitude,
        )
    )

    suspend fun update(record: ParkingRecordEntity) = dao.upsert(record)

    /** Leaving the park: keep the record as history, just stop showing it up top. */
    suspend fun clearActive() = dao.clearActive()

    suspend fun delete(id: Long) = dao.delete(id)
}
