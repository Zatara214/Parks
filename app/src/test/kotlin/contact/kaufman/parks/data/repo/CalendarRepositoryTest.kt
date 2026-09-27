package contact.kaufman.parks.data.repo

import contact.kaufman.parks.data.api.ThemeParksApi
import contact.kaufman.parks.data.api.WeatherApi
import contact.kaufman.parks.data.crowd.CrowdBaselines
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.Resort
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
 * The calendar's two reads: weeks of park hours, which the dashboard already downloads,
 * and a ten-day weather outlook per resort.
 */
class CalendarRepositoryTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.of("America/New_York")).date

    private val scheduleJson = """
        {"id":"mk","name":"Magic Kingdom","schedule":[
          ${(0 until 21).joinToString(",") { offset ->
              val date = today.plus(offset, DateTimeUnit.DAY)
              """{"date":"$date","type":"OPERATING","openingTime":"${date}T09:00:00-04:00","closingTime":"${date}T22:00:00-04:00"}"""
          }},
          {"date":"not-a-date","type":"OPERATING"}
        ]}
    """.trimIndent()

    private val outlookJson = """
        {"latitude":28.4,"longitude":-81.5,"daily":{
          "time":[${(0 until 10).joinToString(",") { "\"${today.plus(it, DateTimeUnit.DAY)}\"" }}],
          "temperature_2m_max":[91,90,89,88,87,88,89,90,91,92],
          "temperature_2m_min":[76,75,74,74,73,74,75,76,76,77],
          "precipitation_probability_max":[40,60,20,10,0,30,80,50,40,30],
          "weather_code":[95,61,2,1,0,3,95,80,2,1]}}
    """.trimIndent()

    private class Counts {
        var schedule = 0
        var outlook = 0
        var failing = false
    }

    private fun repository(counts: Counts): ParksRepository {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            if (counts.failing) return@MockEngine respond("", HttpStatusCode.ServiceUnavailable)
            val body = when {
                path.endsWith("/schedule") -> { counts.schedule++; scheduleJson }
                path.endsWith("/live") -> """{"id":"mk","name":"Magic Kingdom","liveData":[]}"""
                path.endsWith("/forecast") -> { counts.outlook++; outlookJson }
                else -> """{"id":"mk","name":"Magic Kingdom","children":[]}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
        }
        return ParksRepository(ThemeParksApi(client), WeatherApi(client), CrowdBaselines(NoOpWaitSampleDao, NoOpDailyWaitAverageDao))
    }

    /** The dashboard already fetched weeks of hours; opening the calendar must not refetch them. */
    @Test
    fun `the calendar reuses the schedule the dashboard already fetched`() = runTest {
        val counts = Counts()
        val repo = repository(counts)
        repo.refresh(Park.MAGIC_KINGDOM, force = true)
        assertEquals(1, counts.schedule)

        val rows = repo.schedule(Park.MAGIC_KINGDOM)!!
        assertEquals("no second request", 1, counts.schedule)
        // Every day ahead is kept, not just today's; the malformed date is dropped alone.
        assertEquals(21, rows.size)
    }

    /** Hours published an hour ago are still the hours. */
    @Test
    fun `a failed schedule fetch keeps the last good copy`() = runTest {
        val counts = Counts()
        val repo = repository(counts)
        repo.schedule(Park.MAGIC_KINGDOM)
        counts.failing = true
        assertEquals(21, repo.schedule(Park.MAGIC_KINGDOM, force = true)?.size)
    }

    @Test
    fun `Disney Springs has no schedule to fetch`() = runTest {
        val counts = Counts()
        assertEquals(null, repository(counts).schedule(Park.DISNEY_SPRINGS))
        assertEquals(0, counts.schedule)
    }

    @Test
    fun `the outlook is ten days, one request per resort, then cached`() = runTest {
        val counts = Counts()
        val repo = repository(counts)
        val days = repo.outlook(Resort.WALT_DISNEY_WORLD)
        repo.outlook(Resort.WALT_DISNEY_WORLD)

        assertEquals(10, days.size)
        assertEquals(today, days.first().date)
        assertEquals(40, days.first().rainChancePercent)
        assertEquals(91.0, days.first().highF!!, 0.0)
        assertEquals(1, counts.outlook)
    }

    @Test
    fun `a failed outlook keeps the last good one`() = runTest {
        val counts = Counts()
        val repo = repository(counts)
        repo.outlook(Resort.WALT_DISNEY_WORLD)
        counts.failing = true
        assertTrue(repo.outlook(Resort.WALT_DISNEY_WORLD, force = true).size == 10)
    }
}
