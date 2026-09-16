package contact.kaufman.parks.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

enum class EntityKind { ATTRACTION, SHOW, RESTAURANT }

enum class OperatingStatus { OPERATING, DOWN, CLOSED, REFURBISHMENT, UNKNOWN }

/** The queue kinds the two resorts actually use. Universal has no virtual queue and
 *  Disney has no paid express lane, so a card only ever shows a couple of these. */
sealed interface Queue {
    /** The posted standby wait, the only number the crowd model uses. */
    data class Standby(val waitMinutes: Int?) : Queue
    data class SingleRider(val waitMinutes: Int?) : Queue
    /** Universal Express. */
    data class PaidStandby(val waitMinutes: Int?) : Queue
    /** Disney Lightning Lane, free tier (virtual queue return window). */
    data class ReturnTime(val state: String?, val start: Instant?, val end: Instant?) : Queue
    /** Disney Lightning Lane, paid. */
    data class PaidReturnTime(
        val state: String?,
        val start: Instant?,
        val end: Instant?,
        val price: String?,
    ) : Queue
    data class BoardingGroup(
        val allocationStatus: String?,
        val currentStart: Int?,
        val currentEnd: Int?,
        val estimatedWaitMinutes: Int?,
    ) : Queue
}

data class Showtime(val type: String?, val start: Instant?, val end: Instant?)

/**
 * One hour of Disney's own posted-wait forecast.
 *
 * **Disney only.** Universal's `/live` carries no forecast array at all, so anything built
 * on this has to degrade gracefully at USF, IOA and Epic Universe rather than look broken.
 */
data class ForecastPoint(
    val time: Instant,
    val waitMinutes: Int?,
    /** Disney's own percentile for this hour, where 100 is the ride's worst. */
    val percentage: Int?,
)

/**
 * One thing Disney sells today to skip a queue, read from the park schedule's
 * `purchases` array.
 *
 * **Disney only.** Universal's schedule carries no purchases at all — Express Pass pricing
 * simply is not in the feed — so this list is empty at USF, Islands of Adventure and Epic
 * Universe, and the UI draws nothing rather than an empty card. Disney Springs has no
 * schedule to read in the first place.
 */
data class LightningLaneOffer(
    /** The entity this applies to, when upstream ties it to one attraction. */
    val id: String?,
    val name: String,
    /**
     * Upstream's own label, kept raw and deliberately not switched on.
     *
     * Whether an offer is park-wide is decided by [ParkSnapshot.lightningLanePackages]
     * matching it against the park's own attractions, so a new or renamed type upstream
     * cannot silently mis-file a pass into the wrong half of the screen.
     */
    val type: String?,
    /** False is a real answer — "sold out for today" — and reads differently from null,
     *  which is upstream declining to say. */
    val available: Boolean?,
    /** Upstream's formatted string, shown verbatim. Never recomputed from
     *  [amountMinorUnits]: the feed knows its own currency and Parks does not do money. */
    val price: String?,
    /** Cents. Used for ordering only, never for display. */
    val amountMinorUnits: Int?,
)

data class ParkEntity(
    val id: String,
    val name: String,
    val kind: EntityKind,
    val park: Park,
    val status: OperatingStatus,
    val queues: List<Queue> = emptyList(),
    val showtimes: List<Showtime> = emptyList(),
    val forecast: List<ForecastPoint> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastUpdated: Instant? = null,
) {
    val standbyMinutes: Int?
        get() = queues.filterIsInstance<Queue.Standby>().firstOrNull()?.waitMinutes

    val isOperating: Boolean get() = status == OperatingStatus.OPERATING

    /**
     * The quietest hour still ahead of you today, or null when there is nothing useful to
     * say — no forecast (every Universal ride), nothing left today, or a flat curve where
     * waiting buys you nothing.
     *
     * [minimumSavingMinutes] exists so the app stays quiet rather than advising someone to
     * walk away and come back for the sake of five minutes.
     */
    fun bestTimeAhead(now: Instant, minimumSavingMinutes: Int = 10): ForecastPoint? {
        val ahead = forecast.filter { it.time > now && it.waitMinutes != null }
        if (ahead.size < 2) return null
        val best = ahead.minByOrNull { it.waitMinutes!! } ?: return null
        // Compare against what it is posting now, not against the rest of the forecast:
        // the question is whether to queue up now, and a standby number that is already
        // lower than anything forecast should not be talked out of.
        val current = standbyMinutes ?: ahead.first().waitMinutes ?: return null
        return best.takeIf { current - it.waitMinutes!! >= minimumSavingMinutes }
    }
}

/** One row of a park's calendar — regular hours, Early Entry, or a hard-ticket night. */
data class ParkHours(
    val date: LocalDate,
    val type: String,
    val description: String?,
    val opening: Instant?,
    val closing: Instant?,
) {
    val isRegularOperating: Boolean get() = type == "OPERATING"
    val isTicketedEvent: Boolean get() = type == "TICKETED_EVENT"
}

data class ParkWeather(
    val temperatureF: Double?,
    val feelsLikeF: Double?,
    val humidityPercent: Int?,
    val weatherCode: Int?,
    val windMph: Double?,
    val isDay: Boolean,
    val highF: Double?,
    val lowF: Double?,
    val rainChancePercent: Int?,
    /** Next few hours of rain chance — the thing that decides whether to bring a poncho. */
    val hourlyRainChance: List<Pair<LocalTime, Int>> = emptyList(),
    val fetchedAt: Instant,
)

/** One day's measured midday average for a ride, from the app's own records. */
data class WaitHistoryDay(
    val date: LocalDate,
    val averageMinutes: Float,
    val sampleCount: Int,
)

data class CrowdReading(
    val level: Int,
    val exactLevel: Float,
    val label: String,
    val versusUsual: String,
    val ratio: Float,
    /** 0f when every baseline is still the shipped seed, 1f when all are measured. */
    val confidence: Float,
    val basedOnAttractions: Int,
    /** True when this is read off the current posted waits rather than the midday
     *  roll-up — an early-in-the-day estimate that will firm up as the day goes on. */
    val isProvisional: Boolean = false,
)

/** Everything the dashboard shows for one park. Each piece loads independently, so a
 *  weather failure must never blank out wait times. */
data class ParkSnapshot(
    val park: Park,
    val hours: List<ParkHours> = emptyList(),
    val entities: List<ParkEntity> = emptyList(),
    val crowd: CrowdReading? = null,
    val weather: ParkWeather? = null,
    val lightningLane: List<LightningLaneOffer> = emptyList(),
    val fetchedAt: Instant? = null,
    val error: String? = null,
) {
    val todayRegularHours: ParkHours? get() = hours.firstOrNull { it.isRegularOperating }

    val attractions: List<ParkEntity> get() = entities.filter { it.kind == EntityKind.ATTRACTION }

    val openAttractions: List<ParkEntity> get() = attractions.filter { it.isOperating }

    /** The headline number on a park card: the longest posted standby right now. */
    val longestWait: ParkEntity?
        get() = openAttractions.filter { it.standbyMinutes != null }.maxByOrNull { it.standbyMinutes!! }

    /**
     * The passes sold for the park as a whole — Multi Pass, Premier Pass — cheapest first.
     *
     * This is the half that appears nowhere else in the app. A **single attraction's**
     * price already arrives on the live feed as [Queue.PaidReturnTime] and is drawn on
     * that ride's own row, fresher than the schedule's copy of it, so listing every ride
     * here again would be a second, staler answer to a question already answered.
     *
     * Per-attraction offers are excluded by matching the park's own attractions on **id
     * or name**, not by reading upstream's `type`. The id is the better key, but the two
     * feeds are not documented to share an id scheme, and a name match is a cheap
     * backstop: get this wrong and the card lists thirty rides instead of two passes,
     * which is the kind of failure that looks like the feature working.
     */
    val lightningLanePackages: List<LightningLaneOffer>
        get() {
            val ids = attractions.mapTo(mutableSetOf()) { it.id }
            val names = attractions.mapTo(mutableSetOf()) { it.name.lowercase() }
            return lightningLane
                .filterNot { offer ->
                    (offer.id != null && offer.id in ids) || offer.name.lowercase() in names
                }
                // Cheapest first, and anything the feed priced as null sinks to the
                // bottom rather than sorting as free.
                .sortedBy { it.amountMinorUnits ?: Int.MAX_VALUE }
        }

    val medianWait: Int?
        get() = openAttractions.mapNotNull { it.standbyMinutes }.sorted()
            .takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
}
