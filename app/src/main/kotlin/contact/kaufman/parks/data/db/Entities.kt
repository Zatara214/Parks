package contact.kaufman.parks.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One posted-wait observation. Written every time live data is fetched while the app is
 * open, which is what lets the crowd model eventually stop relying on shipped seeds.
 *
 * Rows outside the midday window are still stored — they cost almost nothing and are
 * what a future "wait time through the day" chart needs.
 */
@Entity(
    tableName = "wait_samples",
    indices = [Index(value = ["attractionId", "observedAtEpochSeconds"])],
)
data class WaitSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attractionId: String,
    val parkId: String,
    val observedAtEpochSeconds: Long,
    /** Local park date, `yyyy-MM-dd`, so a late-night sample lands on the right day. */
    val parkDate: String,
    val waitMinutes: Int,
)

/**
 * A ride's midday mean for one day, rolled up from [WaitSampleEntity].
 *
 * This is the unit the crowd model actually consumes, and the unit a recorded baseline
 * is computed from. Keeping it separate means the raw samples can be pruned later
 * without losing history.
 */
@Entity(
    tableName = "daily_wait_averages",
    primaryKeys = ["attractionId", "parkDate"],
    indices = [Index(value = ["parkId", "parkDate"])],
)
data class DailyWaitAverageEntity(
    val attractionId: String,
    val parkId: String,
    val parkDate: String,
    val averageMinutes: Float,
    val sampleCount: Int,
    /** 1 = Monday .. 7 = Sunday. Stored so baselines can be cut by day type. */
    val dayOfWeek: Int,
    /** 1-12. Stored so baselines can be cut by season. */
    val month: Int,
)

/** A recorded park-level crowd reading, kept so the app can show its own history. */
@Entity(tableName = "park_crowd_history", primaryKeys = ["parkId", "parkDate"])
data class ParkCrowdEntity(
    val parkId: String,
    val parkDate: String,
    val level: Float,
    val ratio: Float,
    val confidence: Float,
)

/**
 * Where the car is.
 *
 * Deliberately free-form: Disney rows are "Heroes 12", Universal's are "Jaws, Level 4",
 * and Epic Universe is different again. Forcing a schema on that would make it slower
 * to enter than a photo, which defeats the point.
 */
@Entity(tableName = "parking_records")
data class ParkingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parkId: String,
    val parkedAtEpochSeconds: Long,
    /** Lot or area name — "Heroes", "Jaws", "Terminal C". */
    val lot: String,
    /** Row or level — "12", "Level 4". */
    val row: String,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Only one record is current; older ones are kept as history. */
    val isActive: Boolean = true,
)

/**
 * One moment the app could see, from its own evidence, that you were in a park.
 *
 * Parks never tracks location in the background, so there is no continuous record to draw
 * a trip from. What it does have is the fixes it already takes when a screen asks: opening
 * the dashboard, opening a park, recording a spot. Each of those that lands inside a park
 * boundary is written here, and a visit is reconstructed later by grouping them.
 *
 * Deliberately dumb rows. Storing raw sightings rather than computed visits means the
 * grouping rules can change — a longer gap counting as two visits, say — without the
 * stored data needing a migration or having already thrown away the detail.
 *
 * **No coordinates are kept.** The park is the whole answer; the fix that produced it is
 * nobody's business afterwards, including this app's.
 */
@Entity(
    tableName = "park_sightings",
    indices = [Index(value = ["parkId", "seenAtEpochSeconds"])],
)
data class ParkSightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parkId: String,
    val seenAtEpochSeconds: Long,
)
