package contact.kaufman.parks.data.repo

import contact.kaufman.parks.data.db.ParkSightingDao
import contact.kaufman.parks.data.db.ParkSightingEntity
import contact.kaufman.parks.data.db.ParkingDao
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkedSpot
import contact.kaufman.parks.domain.ParkingDay
import contact.kaufman.parks.domain.TripDay
import contact.kaufman.parks.domain.groupSightings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Trip history, assembled from what the app itself witnessed.
 *
 * Entirely local. There is no server, no account and no background work — a trip is
 * reconstructed from the fixes the app already takes when a screen asks for one, so the
 * record is a by-product of using the app rather than a reason to track anybody.
 *
 * That also sets the honest ceiling on it: the app knows you were at a park, roughly when,
 * and where the car was. It does not know what you rode. Reading a continuous location
 * history — Dawarich, optionally, later — is what would add that, and it stays optional
 * because almost nobody has one.
 */
@Singleton
class TripRepository @Inject constructor(
    private val sightings: ParkSightingDao,
    private val parking: ParkingDao,
) {

    /**
     * Note that the app has just seen you in a park.
     *
     * Debounced: the dashboard takes a fix every time it opens, and opening it six times
     * while queuing would otherwise write six rows a minute apart, inflating the sighting
     * count until it stopped meaning anything. One row per [SIGHTING_INTERVAL] per park is
     * plenty to bound a visit.
     */
    suspend fun noteSighting(park: Park, now: Instant = Clock.System.now()) {
        val previous = sightings.latestFor(park.id)
        if (previous != null && now.epochSeconds - previous.seenAtEpochSeconds < SIGHTING_INTERVAL.inWholeSeconds) {
            return
        }
        sightings.insert(ParkSightingEntity(parkId = park.id, seenAtEpochSeconds = now.epochSeconds))
    }

    /**
     * Every day the app has evidence for, newest first.
     *
     * Parking records are folded in per day so a trip reads as one thing rather than two
     * unrelated histories. Only the most recent spot for a day is kept: on the rare day
     * with two, the later one is the one worth remembering.
     */
    fun days(): Flow<List<TripDay>> =
        combine(sightings.recent(), parking.history(limit = 200)) { rows, parkingRows ->
            val parkingByDay = parkingRows
                .sortedBy { it.parkedAtEpochSeconds }
                .mapNotNull { record ->
                    val park = Park.fromId(record.parkId) ?: return@mapNotNull null
                    ParkingDay.dayOf(Instant.fromEpochSeconds(record.parkedAtEpochSeconds)) to
                        ParkedSpot(park = park, lot = record.lot, row = record.row)
                }
                .toMap()

            groupSightings(
                sightings = rows.mapNotNull { row ->
                    val park = Park.fromId(row.parkId) ?: return@mapNotNull null
                    park to Instant.fromEpochSeconds(row.seenAtEpochSeconds)
                },
                parkingByDay = parkingByDay,
            )
        }

    /** Forget a day. Trip history is a convenience, so it has to be disposable. */
    suspend fun forget(day: TripDay) {
        val from = day.visits.minOf { it.firstSeen }.epochSeconds
        val to = day.visits.maxOf { it.lastSeen }.epochSeconds
        sightings.deleteBetween(from, to)
    }

    private companion object {
        val SIGHTING_INTERVAL = 10.minutes
    }
}
