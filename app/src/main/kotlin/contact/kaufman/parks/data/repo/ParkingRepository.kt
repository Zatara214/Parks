package contact.kaufman.parks.data.repo

import contact.kaufman.parks.data.db.ParkingDao
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.domain.Park
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
class ParkingRepository @Inject constructor(
    private val dao: ParkingDao,
) {
    fun active(): Flow<ParkingRecordEntity?> = dao.active()

    fun history(): Flow<List<ParkingRecordEntity>> = dao.history()

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
