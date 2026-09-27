package contact.kaufman.parks.data.menu

import contact.kaufman.parks.data.disney.DisneyResponse
import contact.kaufman.parks.data.disney.DisneyWeb
import contact.kaufman.parks.data.disney.getFromDisney
import io.ktor.client.HttpClient
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
 * anywhere but that person's phone. The request itself, browser identity included, is
 * [getFromDisney], shared with the pass calendar.
 */
@Singleton
class DisneyMenuApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun fetch(slug: String): MenuFetch =
        when (val response = client.getFromDisney(ENDPOINT, mapOf("searchTerm" to slug))) {
            is DisneyResponse.Found -> MenuFetch.Found(response.body)
            DisneyResponse.NotFound -> MenuFetch.NotFound
            DisneyResponse.Blocked -> MenuFetch.Blocked
            is DisneyResponse.Failed -> MenuFetch.Failed(response.error)
        }

    companion object {
        const val ENDPOINT = "https://disneyworld.disney.go.com/dining/dinemenu/api/menu"

        const val BROWSER_USER_AGENT = DisneyWeb.BROWSER_USER_AGENT
    }
}
