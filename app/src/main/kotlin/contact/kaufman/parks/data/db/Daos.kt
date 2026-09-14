package contact.kaufman.parks.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WaitSampleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<WaitSampleEntity>)

    /**
     * The midday roll-up the crowd model reads. The window bounds are passed in rather
     * than hardcoded so [contact.kaufman.parks.data.crowd.CrowdModel] stays the single
     * place that decides what "midday" means.
     */
    @Query(
        """
        SELECT attractionId, AVG(waitMinutes) AS averageMinutes, COUNT(*) AS sampleCount
        FROM wait_samples
        WHERE parkId = :parkId
          AND parkDate = :parkDate
          AND CAST(strftime('%H', observedAtEpochSeconds, 'unixepoch', 'localtime') AS INTEGER)
              BETWEEN :startHour AND :endHour - 1
        GROUP BY attractionId
        """
    )
    suspend fun middayAverages(
        parkId: String,
        parkDate: String,
        startHour: Int,
        endHour: Int,
    ): List<AttractionAverage>

    @Query("DELETE FROM wait_samples WHERE observedAtEpochSeconds < :cutoffEpochSeconds")
    suspend fun pruneOlderThan(cutoffEpochSeconds: Long): Int
}

data class AttractionAverage(
    val attractionId: String,
    val averageMinutes: Float,
    val sampleCount: Int,
)

@Dao
interface DailyWaitAverageDao {

    @Upsert
    suspend fun upsertAll(rows: List<DailyWaitAverageEntity>)

    /**
     * The recorded baseline for a ride: the mean of its past daily averages, restricted
     * to comparable days. Excludes [parkDate] so today never calibrates itself.
     *
     * Matching on month *or* day-of-week rather than both keeps the sample size usable —
     * requiring "Septembers that were also Tuesdays" would take years to fill.
     */
    @Query(
        """
        SELECT attractionId, AVG(averageMinutes) AS averageMinutes, COUNT(*) AS sampleCount
        FROM daily_wait_averages
        WHERE parkId = :parkId
          AND parkDate != :parkDate
          AND (month = :month OR dayOfWeek = :dayOfWeek)
        GROUP BY attractionId
        HAVING COUNT(*) >= :minObservations
        """
    )
    suspend fun recordedBaselines(
        parkId: String,
        parkDate: String,
        month: Int,
        dayOfWeek: Int,
        minObservations: Int,
    ): List<AttractionAverage>

    @Query("SELECT COUNT(DISTINCT parkDate) FROM daily_wait_averages WHERE parkId = :parkId")
    fun observedDayCount(parkId: String): Flow<Int>
}

@Dao
interface ParkCrowdDao {

    @Upsert
    suspend fun upsert(row: ParkCrowdEntity)

    @Query("SELECT * FROM park_crowd_history WHERE parkId = :parkId ORDER BY parkDate DESC LIMIT :limit")
    fun recent(parkId: String, limit: Int = 30): Flow<List<ParkCrowdEntity>>
}

@Dao
interface ParkingDao {

    @Query("SELECT * FROM parking_records WHERE isActive = 1 ORDER BY parkedAtEpochSeconds DESC LIMIT 1")
    fun active(): Flow<ParkingRecordEntity?>

    @Query("SELECT * FROM parking_records ORDER BY parkedAtEpochSeconds DESC LIMIT :limit")
    fun history(limit: Int = 50): Flow<List<ParkingRecordEntity>>

    /**
     * Rows previously used in this lot, most recent first.
     *
     * Disney publishes lot names but not row ranges, so there is no honest fixed list to
     * offer. Remembering what was actually typed turns the common case — the same few
     * spots, over and over — into one tap, without inventing rows that may not exist.
     */
    @Query(
        """
        SELECT `row` FROM parking_records
        WHERE parkId = :parkId AND lot = :lot AND `row` != ''
        GROUP BY `row`
        ORDER BY MAX(parkedAtEpochSeconds) DESC
        LIMIT :limit
        """
    )
    fun recentRows(parkId: String, lot: String, limit: Int = 6): Flow<List<String>>

    @Query("UPDATE parking_records SET isActive = 0 WHERE isActive = 1")
    suspend fun clearActive()

    @Insert
    suspend fun insert(record: ParkingRecordEntity): Long

    @Upsert
    suspend fun upsert(record: ParkingRecordEntity)

    @Query("DELETE FROM parking_records WHERE id = :id")
    suspend fun delete(id: Long)

    /** Parking a car is a "there is exactly one current spot" action, so the swap has to
     *  be atomic — otherwise a crash between the two writes leaves zero or two actives. */
    @androidx.room.Transaction
    suspend fun park(record: ParkingRecordEntity): Long {
        clearActive()
        return insert(record)
    }
}
