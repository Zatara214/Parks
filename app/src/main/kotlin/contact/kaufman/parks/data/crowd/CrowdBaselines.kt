package contact.kaufman.parks.data.crowd

import contact.kaufman.parks.data.db.DailyWaitAverageDao
import contact.kaufman.parks.data.db.DailyWaitAverageEntity
import contact.kaufman.parks.data.db.WaitSampleDao
import contact.kaufman.parks.data.db.WaitSampleEntity
import contact.kaufman.parks.domain.CrowdReading
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkEntity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Bridges recorded history into [CrowdModel].
 *
 * The shipped [CrowdSeed] is only ever the starting point. Every live refresh records
 * what the rides were actually posting, and once a ride has enough comparable days on
 * file its measured baseline replaces the seed — so the "vs. usual" line stops being an
 * educated guess and becomes an observation.
 */
@Singleton
class CrowdBaselines @Inject constructor(
    private val waitSamples: WaitSampleDao,
    private val dailyAverages: DailyWaitAverageDao,
) {

    private val parkTimeZone = TimeZone.of("America/New_York")

    /** Store this refresh's posted waits. Only open rides with a real number count —
     *  a closed ride posting null is not a zero-minute wait. */
    suspend fun record(park: Park, entities: List<ParkEntity>, parkDate: LocalDate) {
        val now = Clock.System.now().epochSeconds
        val rows = entities.mapNotNull { entity ->
            val wait = entity.standbyMinutes ?: return@mapNotNull null
            if (!entity.isOperating) return@mapNotNull null
            WaitSampleEntity(
                attractionId = entity.id,
                parkId = park.id,
                observedAtEpochSeconds = now,
                parkDate = parkDate.toString(),
                waitMinutes = wait,
            )
        }
        if (rows.isEmpty()) return
        waitSamples.insertAll(rows)
        rollUp(park, parkDate)
    }

    /** Recompute today's midday means. Cheap, and it keeps the daily table correct
     *  without needing a separate end-of-day job that a phone may never run. */
    private suspend fun rollUp(park: Park, parkDate: LocalDate) {
        val averages = waitSamples.middayAverages(
            parkId = park.id,
            parkDate = parkDate.toString(),
            startHour = CrowdModel.WINDOW_START_HOUR,
            endHour = CrowdModel.WINDOW_END_HOUR,
        )
        if (averages.isEmpty()) return
        dailyAverages.upsertAll(
            averages.map {
                DailyWaitAverageEntity(
                    attractionId = it.attractionId,
                    parkId = park.id,
                    parkDate = parkDate.toString(),
                    averageMinutes = it.averageMinutes,
                    sampleCount = it.sampleCount,
                    dayOfWeek = parkDate.dayOfWeek.isoDayNumber,
                    month = parkDate.month.number,
                )
            }
        )
    }

    /**
     * Today's crowd level for [park], or null when there isn't enough open-ride data to
     * say anything honest — before opening, or on a park running a hard-ticket night only.
     */
    suspend fun reading(park: Park, entities: List<ParkEntity>, parkDate: LocalDate): CrowdReading? {
        val month = parkDate.month.number
        val dayOfWeek = parkDate.dayOfWeek.isoDayNumber

        val middayAverages = waitSamples.middayAverages(
            parkId = park.id,
            parkDate = parkDate.toString(),
            startHour = CrowdModel.WINDOW_START_HOUR,
            endHour = CrowdModel.WINDOW_END_HOUR,
        ).associate { it.attractionId to it.averageMinutes }

        val hourNow = Clock.System.now().toLocalDateTime(parkTimeZone).hour
        val provisional = middayAverages.isEmpty()

        // With no midday roll-up the only thing available is what the rides are posting
        // right now, and that is only representative while the day is still running.
        // A 10pm reading is a closing-time queue, not a verdict on the day — publishing
        // it would have every park reading "Ghost town" every night.
        if (provisional && hourNow >= CrowdModel.WINDOW_END_HOUR) return null

        val observed = middayAverages.ifEmpty {
            entities.filter { it.isOperating }
                .mapNotNull { e -> e.standbyMinutes?.let { e.id to it.toFloat() } }
                .toMap()
        }
        if (observed.isEmpty()) return null

        val recorded = dailyAverages.recordedBaselines(
            parkId = park.id,
            parkDate = parkDate.toString(),
            month = month,
            dayOfWeek = dayOfWeek,
            minObservations = CrowdModel.MIN_OBSERVATIONS_TO_TRUST,
        ).associate { it.attractionId to it.averageMinutes }

        val crowd = CrowdModel.parkCrowd(
            park = park,
            observedByAttraction = observed,
            recordedBaselines = recorded,
            month = month,
            dayOfWeek = dayOfWeek,
        ) ?: return null

        return CrowdReading(
            level = crowd.rounded,
            exactLevel = crowd.level,
            label = CrowdModel.describe(crowd.rounded),
            versusUsual = CrowdModel.describeVersusUsual(crowd.ratio),
            ratio = crowd.ratio,
            confidence = crowd.confidence,
            basedOnAttractions = crowd.attractions.size,
            isProvisional = provisional,
        )
    }

    /** Raw samples are only needed until they roll up; the daily means are the history
     *  worth keeping. Six weeks leaves room to rebuild if a roll-up ever goes wrong. */
    suspend fun pruneOldSamples() {
        val cutoff = Clock.System.now().minus(42.days).epochSeconds
        waitSamples.pruneOlderThan(cutoff)
    }
}
