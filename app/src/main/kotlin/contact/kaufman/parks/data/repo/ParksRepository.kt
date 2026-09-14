package contact.kaufman.parks.data.repo

import android.util.Log
import contact.kaufman.parks.data.api.ChildEntityDto
import contact.kaufman.parks.data.api.LiveEntityDto
import contact.kaufman.parks.data.api.ScheduleEntryDto
import contact.kaufman.parks.data.api.ThemeParksApi
import contact.kaufman.parks.data.api.WeatherApi
import contact.kaufman.parks.data.crowd.CrowdBaselines
import contact.kaufman.parks.domain.EntityKind
import contact.kaufman.parks.domain.Geo
import contact.kaufman.parks.domain.ForecastPoint
import contact.kaufman.parks.domain.OperatingStatus
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkEntity
import contact.kaufman.parks.domain.ParkHours
import contact.kaufman.parks.domain.ParkSnapshot
import contact.kaufman.parks.domain.ParkWeather
import contact.kaufman.parks.domain.Queue
import contact.kaufman.parks.domain.Resort
import contact.kaufman.parks.domain.Showtime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The single place live park data is assembled.
 *
 * themeparks.wiki is run by volunteers, so nothing here polls. Data is fetched when a
 * screen asks for it and then held in memory for [FRESH_FOR_SECONDS]; weather is fetched
 * only when something actually displays it.
 */
@Singleton
class ParksRepository @Inject constructor(
    private val api: ThemeParksApi,
    private val weatherApi: WeatherApi,
    private val crowdBaselines: CrowdBaselines,
) {
    /** Orlando. Park-local time is what every displayed hour means. */
    private val parkTimeZone = TimeZone.of("America/New_York")

    private val snapshots = mutableMapOf<Park, ParkSnapshot>()
    private val weatherCache = mutableMapOf<String, ParkWeather>()
    private val childrenCache = mutableMapOf<Park, List<ChildEntityDto>>()

    fun cached(park: Park): ParkSnapshot? = snapshots[park]

    fun today(): LocalDate = Clock.System.now().toLocalDateTime(parkTimeZone).date

    /**
     * Fetch live data, hours and the crowd reading for one park.
     *
     * Weather is left out on purpose — you have a weather app, so it is fetched only when
     * a park screen asks for it via [weather], never as part of a dashboard refresh.
     */
    suspend fun refresh(park: Park, force: Boolean = false): ParkSnapshot = coroutineScope {
        val existing = snapshots[park]
        if (!force && existing != null && existing.isFresh()) return@coroutineScope existing

        val liveJob = async { runCatching { api.live(park.id) } }
        val scheduleJob = async { runCatching { api.schedule(park.id) } }
        val childrenJob = async {
            childrenCache[park]?.let { Result.success(it) }
                ?: runCatching { api.children(park.id).children }.onSuccess { childrenCache[park] = it }
        }

        val live = liveJob.await()
        val schedule = scheduleJob.await()
        val children = childrenJob.await().getOrElse { emptyList() }
        val locations = children.associateBy { it.id }

        val failure = live.exceptionOrNull()
        if (failure != null) {
            Log.w(TAG, "live fetch failed for ${park.displayName}", failure)
            // Keep whatever is already on screen. A refresh that fails must not blank a
            // park card that still holds usable, if stale, numbers.
            val fallback = existing ?: ParkSnapshot(park)
            return@coroutineScope fallback.copy(error = failure.readableMessage())
                .also { snapshots[park] = it }
        }

        val entities = live.getOrNull()?.liveData.orEmpty().mapNotNull { dto ->
            dto.toParkEntity(park, locations[dto.id])
        }

        val today = today()
        val hours = schedule.getOrNull()?.schedule.orEmpty()
            .filter { it.date == today.toString() }
            .map { it.toParkHours() }

        runCatching { crowdBaselines.record(park, entities, today) }
            .onFailure { Log.w(TAG, "recording wait samples failed", it) }

        val crowd = runCatching { crowdBaselines.reading(park, entities, today) }
            .onFailure { Log.w(TAG, "crowd reading failed", it) }
            .getOrNull()

        ParkSnapshot(
            park = park,
            hours = hours,
            entities = entities,
            crowd = crowd,
            // Weather is sticky: once fetched it survives a data refresh rather than
            // being thrown away and re-requested.
            weather = existing?.weather,
            fetchedAt = Clock.System.now(),
            error = null,
        ).also { snapshots[park] = it }
    }

    suspend fun refreshAll(force: Boolean = false): List<ParkSnapshot> = coroutineScope {
        Park.entries.map { async { refresh(it, force) } }.map { it.await() }
    }

    /**
     * Weather for a park — which is really weather for its **resort**.
     *
     * Open-Meteo is point-based, so there is no "Walt Disney World" location as such. But
     * a resort's parks sit a few miles apart and an afternoon storm does not pick between
     * them, so one reading per resort is honest at this scale and means checking EPCOT
     * also answers for Magic Kingdom. Four parks, one call.
     *
     * Still nothing in the background: every call here is driven by something on screen,
     * and repeats inside [WEATHER_FRESH_FOR_SECONDS] are served from memory.
     */
    suspend fun weather(park: Park): ParkWeather? =
        resortWeather(park.resort)?.also { fetched ->
            // Fan the one reading out to every park of that resort, so a screen opened
            // later already has it without another request.
            Park.entries.filter { it.resort == park.resort }.forEach { sibling ->
                snapshots[sibling] = (snapshots[sibling] ?: ParkSnapshot(sibling)).copy(weather = fetched)
            }
        }

    /** The dashboard header reads Walt Disney World, which is where Zak mostly is. */
    suspend fun resortWeather(resort: Resort = Resort.WALT_DISNEY_WORLD): ParkWeather? {
        val (latitude, longitude) = Geo.resortCenter(resort)
        return weatherFor(resort.name, latitude, longitude, resort.displayName)
    }

    private suspend fun weatherFor(
        key: String,
        latitude: Double,
        longitude: Double,
        label: String,
    ): ParkWeather? {
        weatherCache[key]?.let { cached ->
            if ((Clock.System.now() - cached.fetchedAt).inWholeSeconds < WEATHER_FRESH_FOR_SECONDS) {
                return cached
            }
        }

        val response = runCatching { weatherApi.forecast(latitude, longitude) }
            .onFailure { Log.w(TAG, "weather fetch failed for $label", it) }
            // A failed refresh keeps whatever was last read rather than blanking the strip.
            .getOrNull() ?: return weatherCache[key]

        val current = response.current
        val daily = response.daily
        val now = Clock.System.now().toLocalDateTime(parkTimeZone)

        // Compare whole timestamps, not hour-of-day: the forecast spans two days, so
        // filtering on `hour >= nowHour` matches 11PM on both of them and throws away
        // everything after midnight.
        val hourly = response.hourly?.let { h ->
            h.time.indices.mapNotNull { i ->
                val at = runCatching { LocalDateTime.parse(h.time[i]) }.getOrNull()
                    ?: return@mapNotNull null
                val chance = h.precipitationProbability.getOrNull(i) ?: return@mapNotNull null
                Triple(at, at.time, chance)
            }.filter { it.first >= now }.take(8).map { it.second to it.third }
        }.orEmpty()

        val weather = ParkWeather(
            temperatureF = current?.temperature,
            feelsLikeF = current?.feelsLike,
            humidityPercent = current?.humidity,
            weatherCode = current?.weatherCode,
            windMph = current?.windSpeed,
            isDay = (current?.isDay ?: 1) == 1,
            highF = daily?.high?.firstOrNull(),
            lowF = daily?.low?.firstOrNull(),
            rainChancePercent = daily?.precipitationProbabilityMax?.firstOrNull(),
            hourlyRainChance = hourly,
            fetchedAt = Clock.System.now(),
        )
        weatherCache[key] = weather
        return weather
    }

    /** The app's own recorded history for one ride. Empty until it has watched a few days. */
    suspend fun waitHistory(attractionId: String) = crowdBaselines.history(attractionId)

    private fun ParkSnapshot.isFresh(): Boolean {
        val at = fetchedAt ?: return false
        return (Clock.System.now() - at).inWholeSeconds < FRESH_FOR_SECONDS
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Couldn't reach themeparks.wiki"

    private companion object {
        const val TAG = "ParksRepository"

        /** themeparks.wiki itself refreshes on the order of minutes, so anything shorter
         *  would hammer a volunteer service for numbers that have not changed. */
        const val FRESH_FOR_SECONDS = 120L

        /** Weather moves slowly enough that a quarter hour is plenty, and Open-Meteo is
         *  free for non-commercial use — worth not abusing. */
        const val WEATHER_FRESH_FOR_SECONDS = 900L


    }
}

private fun LiveEntityDto.toParkEntity(park: Park, child: ChildEntityDto?): ParkEntity? {
    val kind = when (entityType) {
        "ATTRACTION" -> EntityKind.ATTRACTION
        "SHOW" -> EntityKind.SHOW
        "RESTAURANT" -> EntityKind.RESTAURANT
        else -> return null // PARK rows and anything new upstream
    }

    val queues = buildList {
        queue?.standby?.let { add(Queue.Standby(it.waitTime)) }
        queue?.singleRider?.let { add(Queue.SingleRider(it.waitTime)) }
        queue?.paidStandby?.let { add(Queue.PaidStandby(it.waitTime)) }
        queue?.returnTime?.let {
            add(Queue.ReturnTime(it.state, it.returnStart.toInstantOrNull(), it.returnEnd.toInstantOrNull()))
        }
        queue?.paidReturnTime?.let {
            add(
                Queue.PaidReturnTime(
                    state = it.state,
                    start = it.returnStart.toInstantOrNull(),
                    end = it.returnEnd.toInstantOrNull(),
                    price = it.price?.formatted,
                )
            )
        }
        queue?.boardingGroup?.let {
            add(
                Queue.BoardingGroup(
                    allocationStatus = it.allocationStatus,
                    currentStart = it.currentGroupStart,
                    currentEnd = it.currentGroupEnd,
                    estimatedWaitMinutes = it.estimatedWait,
                )
            )
        }
    }

    return ParkEntity(
        id = id,
        name = name,
        kind = kind,
        park = park,
        status = when (status) {
            "OPERATING" -> OperatingStatus.OPERATING
            "DOWN" -> OperatingStatus.DOWN
            "CLOSED" -> OperatingStatus.CLOSED
            "REFURBISHMENT" -> OperatingStatus.REFURBISHMENT
            else -> OperatingStatus.UNKNOWN
        },
        queues = queues,
        showtimes = showtimes.map { Showtime(it.type, it.startTime.toInstantOrNull(), it.endTime.toInstantOrNull()) },
        forecast = forecast.mapNotNull { point ->
            val at = point.time.toInstantOrNull() ?: return@mapNotNull null
            ForecastPoint(time = at, waitMinutes = point.waitTime, percentage = point.percentage)
        },
        latitude = child?.location?.latitude,
        longitude = child?.location?.longitude,
        lastUpdated = lastUpdated.toInstantOrNull(),
    )
}

private fun ScheduleEntryDto.toParkHours() = ParkHours(
    date = LocalDate.parse(date),
    type = type,
    description = description,
    opening = openingTime.toInstantOrNull(),
    closing = closingTime.toInstantOrNull(),
)

/** Upstream timestamps are ISO-8601 with an offset, but a malformed one must not take
 *  down a whole refresh — a missing time just renders as absent. */
private fun String?.toInstantOrNull(): Instant? =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }
