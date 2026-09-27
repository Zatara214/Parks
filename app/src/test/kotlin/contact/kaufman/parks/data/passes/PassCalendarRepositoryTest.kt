package contact.kaufman.parks.data.passes

import contact.kaufman.parks.domain.AnnualPass
import contact.kaufman.parks.domain.PassBlockout
import contact.kaufman.parks.domain.PassCalendar
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.UserAgent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The weekly rhythm Zak asked for, without any background work: the phone's copy until it
 * is a week old, then Disney again on the next open — and never an empty calendar because
 * one check failed. The reader is faked, because the feed's real shape is not yet known.
 */
class PassCalendarRepositoryTest {

    private val now = Instant.parse("2026-09-27T16:00:00Z")

    private class MemoryStore : PassCalendarStore {
        var entry: CachedPassCalendar? = null
        override suspend fun read() = entry
        override suspend fun write(entry: CachedPassCalendar) { this.entry = entry }
    }

    /** Reads a body of "ok" as one Pixie Dust blockout; anything else as unreadable. */
    private class FakeReader : PassCalendarReader {
        override fun read(body: String, fetchedAt: Instant): PassCalendar? =
            if (body.startsWith("ok")) {
                PassCalendar(
                    blockouts = mapOf(AnnualPass.PIXIE_DUST to mapOf(LocalDate(2026, 10, 3) to PassBlockout.DISNEY_THEME_PARKS)),
                    goodToGo = setOf(LocalDate(2026, 10, 1)),
                    fetchedAt = fetchedAt,
                )
            } else {
                null
            }
    }

    private class Server {
        var body = "ok"
        var status = HttpStatusCode.OK
        val agents = mutableListOf<List<String>>()
        var requests = 0
    }

    private fun repository(server: Server, store: PassCalendarStore = MemoryStore()): PassCalendarRepository {
        val engine = MockEngine { request ->
            server.requests++
            server.agents += request.headers.getAll(HttpHeaders.UserAgent).orEmpty()
            respond(server.body, server.status)
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(UserAgent) { agent = "Parks/test" }
        }
        return PassCalendarRepository(client, store, FakeReader())
    }

    @Test
    fun `a copy less than a week old is used without asking Disney`() = runTest {
        val server = Server()
        val repo = repository(server)
        repo.calendar(now = now)
        repo.calendar(now = now + 6.days)
        assertEquals(1, server.requests)
    }

    /** The whole point: a new batch of Good-to-Go days shows up within a week. */
    @Test
    fun `a week-old copy is replaced on the next open`() = runTest {
        val server = Server()
        val repo = repository(server)
        repo.calendar(now = now)
        val later = repo.calendar(now = now + 8.days) as PassCalendarResult.Loaded
        assertEquals(2, server.requests)
        assertEquals(now + 8.days, later.calendar.fetchedAt)
    }

    @Test
    fun `pull to refresh asks immediately`() = runTest {
        val server = Server()
        val repo = repository(server)
        repo.calendar(now = now)
        repo.calendar(force = true, now = now + 1.days)
        assertEquals(2, server.requests)
    }

    /** Survives a restart: the copy lives on disk, not only in memory. */
    @Test
    fun `the copy on the phone is used by a fresh start`() = runTest {
        val server = Server()
        val store = MemoryStore()
        repository(server, store).calendar(now = now)
        val result = repository(server, store).calendar(now = now + 2.days)
        assertTrue(result is PassCalendarResult.Loaded)
        assertEquals(1, server.requests)
    }

    @Test
    fun `a failed check keeps the old copy and says so`() = runTest {
        val server = Server()
        val repo = repository(server)
        repo.calendar(now = now)
        server.status = HttpStatusCode.ServiceUnavailable
        val result = repo.calendar(now = now + 8.days) as PassCalendarResult.Loaded
        assertEquals("Couldn't check Disney for new dates.", result.refreshNote)
        assertEquals(1, result.calendar.goodToGo.size)
    }

    @Test
    fun `a bot check with nothing on the phone is unavailable, not empty`() = runTest {
        val server = Server().apply { body = "<!DOCTYPE html><html>verify</html>" }
        assertTrue(repository(server).calendar(now = now) is PassCalendarResult.Unavailable)
    }

    /**
     * Until the reader understands Disney's format, the answer is "can't read yet" — never a
     * calendar with no blockouts, which would tell every friend they are free to come.
     * And the unreadable copy gets the same week's grace, so it is not refetched per open.
     */
    @Test
    fun `an unreadable feed is unavailable, and not refetched on every open`() = runTest {
        val server = Server().apply { body = "{\"shape\":\"unknown\"}" }
        val repo = repository(server)
        assertTrue(repo.calendar(now = now) is PassCalendarResult.Unavailable)
        assertTrue(repo.calendar(now = now + 1.days) is PassCalendarResult.Unavailable)
        assertEquals(1, server.requests)
    }

    @Test
    fun `the request goes out as a browser, once`() = runTest {
        val server = Server()
        repository(server).calendar(now = now)
        assertEquals(1, server.agents.single().size)
        assertTrue(server.agents.single().single().startsWith("Mozilla/5.0"))
    }
}
