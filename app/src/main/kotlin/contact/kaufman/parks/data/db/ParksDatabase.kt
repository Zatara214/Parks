package contact.kaufman.parks.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WaitSampleEntity::class,
        DailyWaitAverageEntity::class,
        ParkCrowdEntity::class,
        ParkingRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ParksDatabase : RoomDatabase() {
    abstract fun waitSamples(): WaitSampleDao
    abstract fun dailyAverages(): DailyWaitAverageDao
    abstract fun parkCrowd(): ParkCrowdDao
    abstract fun parking(): ParkingDao

    companion object {
        const val NAME = "parks.db"
    }
}
