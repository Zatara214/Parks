package contact.kaufman.parks.data.menu

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** What one request for one slug came back with. */
sealed interface MenuFetch {
    data class Found(val body: String) : MenuFetch

    /** No restaurant has this slug. The ordinary answer to a wrong guess. */
    data object NotFound : MenuFetch

    /** Disney's bot check answered with a web page instead of the menu. */
    data object Blocked : MenuFetch

    data class Failed(val error: Throwable) : MenuFetch
}

/**
 * Disney's menu endpoint — the one its own menu pages load, one restaurant per request.
 *
 * Called only when someone opens a restaurant, never in the background and never in bulk.
 * That is the line this app draws: it asks for the one menu a person is looking at, the
 * same request their browser would make on Disney's menu page, and it keeps no copy
 * anywhere but that person's phone.
 *
 * **It sends a browser's User-Agent**, unlike every other request this app makes, which
 * identify as Parks. Disney's endpoint answers anything else with a bot-check page instead
 * of JSON. Zak weighed that and chose it on 2026-09-26; see PLAN.md, Restaurant menus.
 */
@Singleton
class DisneyMenuApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun fetch(slug: String): MenuFetch = try {
        val response = client.get(ENDPOINT) {
            parameter("searchTerm", slug)
            // A 404 is the normal answer to a slug guessed wrong, not an error — the shared
            // client would throw on it, so this one request reads its own status.
            expectSuccess = false
            // Set here, per request, so it overrides the client's honest default for this
            // one host and nowhere else.
            header(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        }
        when {
            response.status == HttpStatusCode.NotFound -> MenuFetch.NotFound
            !response.status.isSuccess() -> MenuFetch.Failed(IllegalStateException("HTTP ${response.status.value}"))
            else -> {
                val body = response.bodyAsText()
                // The bot check returns 200 with an HTML page, so the status alone does not
                // tell a menu from a challenge.
                if (body.trimStart().startsWith("<")) MenuFetch.Blocked else MenuFetch.Found(body)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        MenuFetch.Failed(error)
    }

    companion object {
        const val ENDPOINT = "https://disneyworld.disney.go.com/dining/dinemenu/api/menu"

        /** Chrome on Android in the reduced form Chrome itself now sends — the truthful
         *  shape for a request that comes from a phone. */
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/137.0.0.0 Mobile Safari/537.36"
    }
}
