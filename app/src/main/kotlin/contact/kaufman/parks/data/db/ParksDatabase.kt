package contact.kaufman.parks.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        WaitSampleEntity::class,
        DailyWaitAverageEntity::class,
        ParkCrowdEntity::class,
        ParkingRecordEntity::class,
        ParkSightingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ParksDatabase : RoomDatabase() {
    abstract fun waitSamples(): WaitSampleDao
    abstract fun dailyAverages(): DailyWaitAverageDao
    abstract fun parkCrowd(): ParkCrowdDao
    abstract fun parking(): ParkingDao
    abstract fun parkSightings(): ParkSightingDao

    companion object {
        const val NAME = "parks.db"

        /**
         * Adds the trip-history sightings table.
         *
         * A real migration rather than a destructive one: v0.4.0 is installed and carries
         * recorded parking spots and months of wait samples, and the crowd model only gets
         * honest as that history accumulates. Dropping it to add a table would be a
         * self-inflicted regression.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `park_sightings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `parkId` TEXT NOT NULL,
                        `seenAtEpochSeconds` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_park_sightings_parkId_seenAtEpochSeconds` " +
                        "ON `park_sightings` (`parkId`, `seenAtEpochSeconds`)",
                )
            }
        }
    }
}
