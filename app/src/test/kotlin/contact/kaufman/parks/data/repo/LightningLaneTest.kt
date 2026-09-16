package contact.kaufman.parks.data.repo

import contact.kaufman.parks.data.api.ThemeParksApi
import contact.kaufman.parks.data.api.WeatherApi
import contact.kaufman.parks.data.crowd.CrowdBaselines
import contact.kaufman.parks.domain.Park
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock

/**
 * Lightning Lane pricing, read off the park schedule's `purchases` array.
 *
 * The rule the whole feature turns on: this card is for passes sold **for the park**.
 * A single ride's price already arrives on the live feed as a paid return time and is
 * drawn on that ride's own row, so repeating every ride here would be a second, staler
 * answer to a question already answered — and would bury two passes under thirty rides.
 */
class LightningLaneTest {

    /** The same date the repository will filter on, computed the same way. */
    private val today = Clock.System.now()
        .toLocalDateTime(TimeZone.of("America/New_York")).date

    private val tomorrow = today.plus(1, DateTimeUnit.DAY)

    /** Two attractions, so an offer can be matched against them by id and by name. */
    private val liveJson = """
        {
          "id": "mk", "name": "Magic Kingdom",
          "liveData": [
            {"id":"sm-entity","name":"Space Mountain","entityType":"ATTRACTION",
             "status":"OPERATING","queue":{"STANDBY":{"waitTime":65}}},
            {"id":"jc-entity","name":"Jungle Cruise","entityType":"ATTRACTION",
             "status":"OPERATING","queue":{"STANDBY":{"waitTime":40}}}
          ]
        }
    """.trimIndent()

    private fun scheduleJson(): String = """
        {
          "id": "mk", "name": "Magic Kingdom",
          "schedule": [
            {
              "date": "$today", "type": "OPERATING",
              "openingTime": "${today}T09:00:00-04:00",
              "closingTime": "${today}T22:00:00-04:00",
              "purchases": [
                {"id":"pkg-premier","name":"Disney Premier Pass","type":"PACKAGE",
                 "available":false,
                 "price":{"amount":29900,"currency":"USD","formatted":"${'$'}299.00"}},
                {"id":"pkg-multi","name":"Disney Multi Pass","type":"PACKAGE",
                 "available":true,
                 "price":{"amount":2300,"currency":"USD","formatted":"${'$'}23.00"}},
                {"id":"sm-entity","name":"Space Mountain","type":"ATTRACTION",
                 "available":true,
                 "price":{"amount":1500,"currency":"USD","formatted":"${'$'}15.00"}},
                {"id":"some-other-id","name":"Jungle Cruise","type":"ATTRACTION",
                 "available":true,
                 "price":{"amount":1200,"currency":"USD","formatted":"${'$'}12.00"}},
                {"id":"pkg-mystery","name":"Unpriced Experiment","type":"PACKAGE",
                 "available":true}
              ]
            },
            {
              "date": "$today", "type": "TICKETED_EVENT",
              "description": "Mickey's Not-So-Scary Halloween Party",
              "purchases": [
                {"id":"pkg-multi","name":"Disney Multi Pass","type":"PACKAGE",
                 "available":true,
                 "price":{"amount":2300,"currency":"USD","formatted":"${'$'}23.00"}}
              ]
            },
            {
              "date": "$tomorrow", "type": "OPERATING",
              "purchases": [
                {"id":"pkg-multi","name":"Disney Multi Pass","type":"PACKAGE",
                 "available":true,
                 "price":{"amount":3900,"currency":"USD","formatted":"${'$'}39.00"}}
              ]
            }
          ]
        }
    """.trimIndent()

    private fun repository(schedule: String = scheduleJson()): ParksRepository {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val body = when {
                path.endsWith("/schedule") -> schedule
                path.endsWith("/live") -> liveJson
                else -> """{"id":"mk","name":"Magic Kingdom","children":[]}"""
            }
            respond(
                content = body,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        return ParksRepository(
            api = ThemeParksApi(client),
            weatherApi = WeatherApi(client),
            crowdBaselines = CrowdBaselines(NoOpWaitSampleDao, NoOpDailyWaitAverageDao),
        )
    }

    @Test
    fun `the card lists park-wide passes, cheapest first`() = runTest {
        val snapshot = repository().refresh(Park.MAGIC_KINGDOM, force = true)

        assertEquals(
            listOf("Disney Multi Pass", "Disney Premier Pass", "Unpriced Experiment"),
            snapshot.lightningLanePackages.map { it.name },
        )
    }

    /**
     * The id is the good key. This pins the case where it works.
     */
    @Test
    fun `a single ride's price is left to that ride's own row`() = runTest {
        val snapshot = repository().refresh(Park.MAGIC_KINGDOM, force = true)

        assertTrue(
            "Space Mountain's Single Pass belongs on the ride row, not in the passes card",
            snapshot.lightningLanePackages.none { it.name == "Space Mountain" },
        )
    }

    /**
     * ...and this pins the backstop. `some-other-id` matches no attraction, so an
     * id-only rule would file Jungle Cruise as a park-wide pass. The two feeds are not
     * documented to share an id scheme, and the failure mode — a card listing thirty
     * rides — looks enough like a working feature to ship unnoticed.
     */
    @Test
    fun `an offer whose id does not line up is still caught by its name`() = runTest {
        val snapshot = repository().refresh(Park.MAGIC_KINGDOM, force = true)

        assertTrue(
            "a name matching an attraction is a per-ride price whatever its id says",
            snapshot.lightningLanePackages.none { it.name == "Jungle Cruise" },
        )
    }

    /**
     * A date carries several rows — regular hours and a hard-ticket night — and upstream
     * repeats the same pass on each. Listing Multi Pass twice would read as a bug.
     */
    @Test
    fun `a pass repeated across the day's schedule rows is listed once`() = runTest {
        val snapshot = repository().refresh(Park.MAGIC_KINGDOM, force = true)

        assertEquals(1, snapshot.lightningLanePackages.count { it.name == "Disney Multi Pass" })
    }

    /** The schedule runs ~78 days ahead. Only today's prices are today's prices. */
    @Test
    fun `tomorrow's price is not shown as today's`() = runTest {
        val snapshot = repository().refresh(Park.MAGIC_KINGDOM, force = true)

        assertEquals(
            "\$23.00",
            snapshot.lightningLanePackages.first { it.name == "Disney Multi Pass" }.price,
        )
    }

    /**
     * Sold out is information, not a reason to hide the row: knowing Premier Pass is gone
     * for the day is exactly what someone opens this card to find out.
     */
    @Test
    fun `a sold-out pass is still listed, and says so`() = runTest {
        val snapshot = repository().refresh(Park.MAGIC_KINGDOM, force = true)

        val premier = snapshot.lightningLanePackages.first { it.name == "Disney Premier Pass" }
        assertEquals(false, premier.available)
    }

    /** An unpriced offer sorts last rather than leading the card as though it were free. */
    @Test
    fun `an offer with no price sinks below the priced ones`() = runTest {
        val snapshot = repository().refresh(Park.MAGIC_KINGDOM, force = true)

        assertEquals("Unpriced Experiment", snapshot.lightningLanePackages.last().name)
    }

    /**
     * Universal's schedule carries no purchases at all — Express Pass pricing is not in
     * the feed. An empty list is what keeps the card from drawing there.
     */
    @Test
    fun `no purchases means no card`() = runTest {
        val bare = """
            {"id":"mk","name":"Magic Kingdom",
             "schedule":[{"date":"$today","type":"OPERATING"}]}
        """.trimIndent()

        val snapshot = repository(bare).refresh(Park.MAGIC_KINGDOM, force = true)

        assertTrue(snapshot.lightningLane.isEmpty())
        assertTrue(snapshot.lightningLanePackages.isEmpty())
    }
}
