package contact.kaufman.parks.data.repo

import contact.kaufman.parks.data.api.ThemeParksApi
import contact.kaufman.parks.data.api.WeatherApi
import contact.kaufman.parks.data.crowd.CrowdBaselines
import contact.kaufman.parks.data.db.AttractionAverage
import contact.kaufman.parks.data.db.DailyWaitAverageDao
import contact.kaufman.parks.data.db.DailyWaitAverageEntity
import contact.kaufman.parks.data.db.WaitSampleDao
import contact.kaufman.parks.data.db.WaitSampleEntity
import contact.kaufman.parks.domain.Park
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Weather is read once per resort, not once per park.
 *
 * Open-Meteo is free for non-commercial use, and four requests where one will do is the
 * kind of thing that gets free services withdrawn.
 */
class ResortWeatherTest {

    private val weatherJson = """
        {
          "latitude": 28.4, "longitude": -81.5,
          "current": {"time":"2026-09-14T16:00","temperature_2m":82.0,"apparent_temperature":88.0,"weather_code":2},
          "daily": {"time":["2026-09-14"],"temperature_2m_max":[93.0],"temperature_2m_min":[74.0],"precipitation_probability_max":[38]}
        }
    """.trimIndent()

    private fun repository(onRequest: () -> Unit): ParksRepository {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val engine = MockEngine {
            onRequest()
            respond(
                content = weatherJson,
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
    fun `every Disney park shares one weather call`() = runTest {
        var calls = 0
        val repository = repository { calls++ }

        assertNotNull(repository.weather(Park.EPCOT))
        repository.weather(Park.MAGIC_KINGDOM)
        repository.weather(Park.HOLLYWOOD_STUDIOS)
        repository.weather(Park.ANIMAL_KINGDOM)

        assertEquals("four Disney parks should cost one request", 1, calls)
    }

    @Test
    fun `the two resorts are read separately`() = runTest {
        var calls = 0
        val repository = repository { calls++ }

        repository.weather(Park.EPCOT)
        repository.weather(Park.ISLANDS_OF_ADVENTURE)

        // Universal is a dozen miles up I-4; it genuinely can be raining at one and not
        // the other, so this must not be collapsed into a single reading.
        assertEquals(2, calls)
    }

    @Test
    fun `the dashboard header reuses the Disney reading`() = runTest {
        var calls = 0
        val repository = repository { calls++ }

        repository.resortWeather()
        repository.weather(Park.MAGIC_KINGDOM)

        assertEquals(1, calls)
    }

    @Test
    fun `a park picks up weather fetched for a sibling`() = runTest {
        val repository = repository { }
        repository.weather(Park.EPCOT)

        // Without the fan-out this snapshot would have no weather until its own fetch.
        assertNotNull(repository.cached(Park.MAGIC_KINGDOM)?.weather)
    }
}

private object NoOpWaitSampleDao : WaitSampleDao {
    override suspend fun insertAll(samples: List<WaitSampleEntity>) = Unit
    override suspend fun middayAverages(
        parkId: String,
        parkDate: String,
        startHour: Int,
        endHour: Int,
    ): List<AttractionAverage> = emptyList()
    override suspend fun pruneOlderThan(cutoffEpochSeconds: Long): Int = 0
}

private object NoOpDailyWaitAverageDao : DailyWaitAverageDao {
    override suspend fun upsertAll(rows: List<DailyWaitAverageEntity>) = Unit
    override suspend fun recordedBaselines(
        parkId: String,
        parkDate: String,
        month: Int,
        dayOfWeek: Int,
        minObservations: Int,
    ): List<AttractionAverage> = emptyList()
    override fun observedDayCount(parkId: String): Flow<Int> = flowOf(0)
    override suspend fun recentDays(attractionId: String, limit: Int): List<DailyWaitAverageEntity> =
        emptyList()
}
