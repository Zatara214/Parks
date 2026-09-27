package contact.kaufman.parks.data.disney

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

/** What one request to Disney's website came back with. */
sealed interface DisneyResponse {
    data class Found(val body: String) : DisneyResponse

    /** Nothing at that address — for menus, the ordinary answer to a wrong guess. */
    data object NotFound : DisneyResponse

    /** Disney's bot check answered with a web page instead of data. */
    data object Blocked : DisneyResponse

    data class Failed(val error: Throwable) : DisneyResponse
}

/**
 * The one place Parks asks Disney's website for data, rather than themeparks.wiki.
 *
 * Every request here **sends a browser's User-Agent**, unlike the rest of the app, which
 * identifies itself as Parks. Disney's undocumented endpoints answer anything else with a
 * bot-check page. Zak weighed that and chose it for menus on 2026-09-26, and for the pass
 * calendar on 2026-09-27; keeping it in one function means the exception has exactly one
 * address. See PLAN.md, Restaurant menus.
 *
 * Only for requests a person triggers by opening a screen. Nothing here runs in the
 * background, and nothing is fetched in bulk.
 */
suspend fun HttpClient.getFromDisney(url: String, parameters: Map<String, String> = emptyMap()): DisneyResponse =
    try {
        val response = get(url) {
            parameters.forEach { (name, value) -> parameter(name, value) }
            // A 404 means "nothing here", not a crash — the shared client would throw on it.
            expectSuccess = false
            // Per request, so it overrides the client's honest default for Disney alone.
            header(HttpHeaders.UserAgent, DisneyWeb.BROWSER_USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        }
        when {
            response.status == HttpStatusCode.NotFound -> DisneyResponse.NotFound
            !response.status.isSuccess() ->
                DisneyResponse.Failed(IllegalStateException("HTTP ${response.status.value}"))
            else -> {
                val body = response.bodyAsText()
                // The bot check returns 200 with an HTML page, so the status alone does not
                // tell data from a challenge.
                if (body.trimStart().startsWith("<")) DisneyResponse.Blocked else DisneyResponse.Found(body)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        DisneyResponse.Failed(error)
    }

object DisneyWeb {
    /** Chrome on Android in the reduced form Chrome itself now sends — the truthful shape
     *  for a request that comes from a phone. */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/137.0.0.0 Mobile Safari/537.36"
}
