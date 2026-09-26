package contact.kaufman.parks.data.menu

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Menus from Disney: resolving the address, reading the response, and above all picking up
 * changes without ever showing nothing when something usable is on the phone.
 */
class MenuRepositoryTest {

    private val now = Instant.parse("2026-09-26T16:00:00Z")

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    /** Shaped like Disney's response, including the fields the app ignores. */
    private fun menuJson(name: String = "Cosmic Ray's Starlight Café", burger: String = "14.99") = """
        {"name": "$name", "facilityId": "90001234", "mealPeriods": [
          {"label": "Lunch And Dinner", "groups": [
            {"name": "Entrées", "type": "standard", "items": [
              {"title": "Truffle-French Onion Burger", "description": "<p>With truffle aioli &amp; onion jam</p>",
               "prices": [{"withoutTax": $burger, "withTax": 16.04}]},
              {"title": "Chicken Nuggets", "prices": [{"withoutTax": 8.29}, {"withoutTax": 10.49}]}
            ]},
            {"name": "Allergy-Friendly Entrées", "type": "Allergy Friendly", "items": [
              {"title": "Truffle-French Onion Burger", "prices": [{"withoutTax": 14.99}]}
            ]}
          ]}
        ]}
    """.trimIndent()

    private class MemoryStore : MenuStore {
        val entries = mutableMapOf<String, CachedMenu>()
        override suspend fun read(restaurantId: String) = entries[restaurantId]
        override suspend fun write(entry: CachedMenu) { entries[entry.restaurantId] = entry }
        override suspend fun delete(restaurantId: String) { entries.remove(restaurantId) }
    }

    private class Recorder(val handler: MockRequestHandleScope.(slug: String) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val slugs get() = requests.map { it.url.parameters["searchTerm"] }
    }

    private fun repository(store: MenuStore = MemoryStore(), recorder: Recorder): MenuRepository {
        val engine = MockEngine { request ->
            recorder.requests += request
            recorder.handler(this, request.url.parameters["searchTerm"].orEmpty())
        }
        // Built like AppModule's client: expectSuccess on, and an honest default identity.
        val client = HttpClient(engine) {
            expectSuccess = true
            install(UserAgent) { agent = "Parks/test (github.com/Zatara214/Parks)" }
        }
        return MenuRepository(DisneyMenuApi(client), store, json)
    }

    private fun MockRequestHandleScope.ok(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun MockRequestHandleScope.notFound() = respond("", HttpStatusCode.NotFound)

    @Test
    fun `a wrong guess moves on to the next one`() = runTest {
        val recorder = Recorder { slug -> if (slug == "cosmic-ray-starlight-cafe") ok(menuJson()) else notFound() }
        val result = repository(recorder = recorder).refresh("r1", "Cosmic Ray's Starlight Café", now = now)

        assertTrue(result is MenuResult.Found)
        assertEquals(listOf("cosmic-rays-starlight-cafe", "cosmic-ray-starlight-cafe"), recorder.slugs)
    }

    @Test
    fun `the menu reads as prices, sizes and clean descriptions`() = runTest {
        val recorder = Recorder { ok(menuJson()) }
        val menu = (repository(recorder = recorder).refresh("r1", "Cosmic Ray's", now = now) as MenuResult.Found).menu

        assertEquals("Cosmic Ray's Starlight Café", menu.restaurantName)
        val items = menu.periods.single().sections.single().items
        assertEquals("$14.99", items[0].price)
        assertEquals("With truffle aioli & onion jam", items[0].description)
        assertEquals("$8.29 – $10.49", items[1].price)
    }

    /** Disney publishes the allergy menu as a second copy of the same dishes. */
    @Test
    fun `allergy-friendly duplicates are left out`() = runTest {
        val recorder = Recorder { ok(menuJson()) }
        val menu = (repository(recorder = recorder).refresh("r1", "Cosmic Ray's", now = now) as MenuResult.Found).menu

        assertEquals(listOf("Entrées"), menu.periods.single().sections.map { it.name })
    }

    /**
     * The one request that pretends to be a browser must pretend *instead of* the app's own
     * identity, not as well as it — and only on this request.
     */
    @Test
    fun `the menu request carries a browser identity, not the app's`() = runTest {
        val recorder = Recorder { ok(menuJson()) }
        repository(recorder = recorder).refresh("r1", "Cosmic Ray's", now = now)

        val agents = recorder.requests.single().headers.getAll(HttpHeaders.UserAgent).orEmpty()
        assertEquals(listOf(DisneyMenuApi.BROWSER_USER_AGENT), agents)
        assertEquals("en-US,en;q=0.9", recorder.requests.single().headers[HttpHeaders.AcceptLanguage])
    }

    /** The whole point of the refresh: a price Disney changed is the price shown next time. */
    @Test
    fun `a stale menu is replaced by what Disney says now`() = runTest {
        val store = MemoryStore()
        var burger = "14.99"
        val repo = repository(store, Recorder { ok(menuJson(burger = burger)) })

        repo.refresh("r1", "Cosmic Ray's", now = now)
        val cached = repo.cached("r1", "Cosmic Ray's")!!
        assertTrue(repo.isFresh(cached, now + 23.hours))
        assertTrue(!repo.isFresh(cached, now + 25.hours))

        burger = "15.49"
        val later = repo.refresh("r1", "Cosmic Ray's", now = now + 25.hours) as MenuResult.Found
        assertEquals("$15.49", later.menu.periods.single().sections.single().items[0].price)
        assertEquals("$15.49", repo.cached("r1", "Cosmic Ray's")!!.periods.single().sections.single().items[0].price)
    }

    @Test
    fun `the address that worked last time is asked first`() = runTest {
        val store = MemoryStore()
        store.write(CachedMenu("r1", "woodys-lunchbox", now.epochSeconds, menuJson("Woody's Lunch Box")))
        val recorder = Recorder { ok(menuJson("Woody's Lunch Box")) }

        repository(store, recorder).refresh("r1", "Woody's Lunch Box", now = now + 25.hours)
        assertEquals(listOf("woodys-lunchbox"), recorder.slugs)
    }

    /** A bot check is Disney not answering, not Disney saying there is no menu. */
    @Test
    fun `a bot check stops, fails, and keeps the menu already on the phone`() = runTest {
        val store = MemoryStore()
        store.write(CachedMenu("r1", "cosmic-ray-starlight-cafe", now.epochSeconds, menuJson()))
        val recorder = Recorder { respond("<!DOCTYPE html><html>Checking your browser</html>", HttpStatusCode.OK) }
        val repo = repository(store, recorder)

        val result = repo.refresh("r1", "Cosmic Ray's Starlight Café", now = now + 25.hours)
        assertTrue(result is MenuResult.Failed)
        assertEquals(1, recorder.requests.size)
        assertTrue("the old copy must survive a failed refresh", repo.cached("r1", "x") != null)
    }

    @Test
    fun `a network failure keeps the menu already on the phone`() = runTest {
        val store = MemoryStore()
        store.write(CachedMenu("r1", "cosmic-ray-starlight-cafe", now.epochSeconds, menuJson()))
        val repo = repository(store, Recorder { respond("", HttpStatusCode.ServiceUnavailable) })

        assertTrue(repo.refresh("r1", "Cosmic Ray's", now = now + 25.hours) is MenuResult.Failed)
        assertTrue(repo.cached("r1", "x") != null)
    }

    /**
     * If Disney ever changes the response's shape, every restaurant would otherwise read as
     * quietly having no menu — a broken feature that looks like an answer.
     */
    @Test
    fun `an unreadable response is a failure, not "no menu"`() = runTest {
        val recorder = Recorder { ok("""{"mealPeriods": "not a list"}""") }
        assertTrue(repository(recorder = recorder).refresh("r1", "Cosmic Ray's", now = now) is MenuResult.Failed)
    }

    @Test
    fun `no menu anywhere is remembered for a day, and a pull asks again`() = runTest {
        val recorder = Recorder { notFound() }
        val repo = repository(recorder = recorder)

        assertEquals(MenuResult.NoMenu, repo.refresh("r1", "Some Kiosk", now = now))
        val firstRound = recorder.requests.size
        assertEquals(MenuResult.NoMenu, repo.refresh("r1", "Some Kiosk", now = now + 1.hours))
        assertEquals("reopening must not re-run every guess", firstRound, recorder.requests.size)

        repo.refresh("r1", "Some Kiosk", force = true, now = now + 1.hours)
        assertTrue(recorder.requests.size > firstRound)
    }

    /** Disney answered for every address and has nothing: the old copy is no longer true. */
    @Test
    fun `a menu Disney has withdrawn is forgotten`() = runTest {
        val store = MemoryStore()
        store.write(CachedMenu("r1", "gone-cafe", now.epochSeconds, menuJson()))
        val repo = repository(store, Recorder { notFound() })

        assertEquals(MenuResult.NoMenu, repo.refresh("r1", "Gone Cafe", now = now + 25.hours))
        assertNull(repo.cached("r1", "Gone Cafe"))
    }
}
