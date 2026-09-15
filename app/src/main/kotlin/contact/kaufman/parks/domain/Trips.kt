package contact.kaufman.parks.domain

import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A stretch of time the app could see you were in one park.
 *
 * [sightings] is kept because it is the honest measure of how much this rests on: a visit
 * built from two glances is a guess, one built from thirty is a record. The UI says so
 * rather than presenting both with the same confidence.
 */
data class ParkVisit(
    val park: Park,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val sightings: Int,
) {
    val duration: Duration get() = lastSeen - firstSeen

    /**
     * A single sighting has no duration, and two a few minutes apart are not a day out.
     * Below this a visit is real but its length means nothing, so the UI shows a time
     * rather than a span.
     */
    val hasMeaningfulDuration: Boolean get() = sightings >= 2 && duration >= MINIMUM_SPAN

    companion object {
        val MINIMUM_SPAN = 15.minutes
    }
}

/** Everywhere you were on one park day, most recent day first when listed. */
data class TripDay(
    val date: LocalDate,
    val visits: List<ParkVisit>,
    /** Where the car was, when a spot was recorded on this day. */
    val parking: ParkedSpot? = null,
) {
    /** A day with a single park reads differently from a park-hopping day. */
    val isParkHop: Boolean get() = visits.size > 1
}

/** The bit of a parking record a trip cares about. */
data class ParkedSpot(val park: Park, val lot: String, val row: String)

/**
 * Group raw sightings into per-day visits: one visit per park per park day, spanning the
 * first and last time the app saw you there.
 *
 * **Days are park days, not calendar days.** A sighting at 00:30 after a Halloween party
 * belongs to the night before, so this reuses [ParkingDay]'s 4AM rollover rather than
 * inventing a second definition of "today" — the app already had to settle this once for
 * parking, and disagreeing with itself would be worse than either answer.
 *
 * **Visits are deliberately not split on gaps.** The first version broke a visit whenever
 * three hours passed with no sighting, on the theory that leaving and coming back is two
 * visits rather than one long one. Tried against a realistic day — the app opened at 9:12,
 * again at 1pm, and again at 7:40pm — it produced *three* visits from one continuous day at
 * Magic Kingdom, because sightings only happen when a screen asks for a fix and gaps that
 * long are completely ordinary. No threshold fixes that: the app cannot see you leave, only
 * the absence of evidence, and a phone in a pocket is indistinguishable from a drive home.
 * So a gap is left as what it is — unknown — and the [ParkVisit.sightings] count is
 * surfaced instead, which tells the reader how much the span rests on.
 */
fun groupSightings(
    sightings: List<Pair<Park, Instant>>,
    parkingByDay: Map<LocalDate, ParkedSpot> = emptyMap(),
): List<TripDay> = sightings
    .sortedBy { (_, at) -> at }
    .groupBy { (_, at) -> ParkingDay.dayOf(at) }
    .map { (date, onThatDay) ->
        val visits = onThatDay
            .groupBy { (park, _) -> park }
            .map { (park, times) ->
                val instants = times.map { it.second }
                ParkVisit(
                    park = park,
                    firstSeen = instants.first(),
                    lastSeen = instants.last(),
                    sightings = instants.size,
                )
            }
            .sortedBy { it.firstSeen }
        TripDay(date = date, visits = visits, parking = parkingByDay[date])
    }
    .sortedByDescending { it.date }
