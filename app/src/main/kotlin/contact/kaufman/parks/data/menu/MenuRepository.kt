package contact.kaufman.parks.data.menu

import android.util.Log
import contact.kaufman.parks.domain.MenuSlugs
import contact.kaufman.parks.domain.RestaurantMenu
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

sealed interface MenuResult {
    data class Found(val menu: RestaurantMenu) : MenuResult

    /** Disney has no menu under any address the app could work out for this name. */
    data object NoMenu : MenuResult

    data class Failed(val message: String) : MenuResult
}

/**
 * Restaurant menus: the phone's own copy first, Disney when that copy is a day old.
 *
 * **Picking up menu changes** is the job here, not speed. A cached menu is shown at once,
 * and if it is older than [FRESH_FOR] it is replaced by whatever Disney says now — new
 * items, new prices, removed dishes, all of it, because the whole response is swapped
 * rather than merged. Pull-to-refresh asks immediately. Nothing checks in the background:
 * a menu nobody opens is never fetched.
 *
 * A failed refresh keeps the copy already on the phone, the same rule the park cards follow.
 */
@Singleton
class MenuRepository @Inject constructor(
    private val api: DisneyMenuApi,
    private val store: MenuStore,
    private val json: Json,
) {
    /** Restaurants Disney had nothing for, so reopening one does not re-run every guess. */
    private val noMenuUntil = mutableMapOf<String, Instant>()

    suspend fun cached(restaurantId: String, name: String): RestaurantMenu? =
        store.read(restaurantId)?.let { decode(it, name) }

    fun isFresh(menu: RestaurantMenu, now: Instant = Clock.System.now()): Boolean =
        now - menu.fetchedAt < FRESH_FOR

    suspend fun refresh(
        restaurantId: String,
        name: String,
        force: Boolean = false,
        now: Instant = Clock.System.now(),
    ): MenuResult {
        if (force) noMenuUntil.remove(restaurantId)
        noMenuUntil[restaurantId]?.let { until -> if (now < until) return MenuResult.NoMenu }

        val previous = store.read(restaurantId)
        // The address that worked last time goes first, so a known restaurant costs one
        // request rather than a walk through the guesses.
        val slugs = (listOfNotNull(previous?.slug) + MenuSlugs.candidates(name)).distinct()
        var unreadable = false

        for (slug in slugs) {
            when (val outcome = api.fetch(slug)) {
                MenuFetch.NotFound -> continue
                // Both of these are Disney not answering, not Disney saying "no menu", so
                // stop rather than spend the remaining guesses against a wall.
                MenuFetch.Blocked -> {
                    Log.w(TAG, "menu request for $slug got a bot check instead of JSON")
                    return MenuResult.Failed("Disney didn't send the menu just now. Pull down to try again.")
                }
                is MenuFetch.Failed -> {
                    Log.w(TAG, "menu request for $slug failed", outcome.error)
                    return MenuResult.Failed("Couldn't reach Disney.")
                }
                is MenuFetch.Found -> {
                    val entry = CachedMenu(restaurantId, slug, now.epochSeconds, outcome.body)
                    val parsed = runCatching { json.decodeFromString<DisneyMenuResponse>(outcome.body) }
                    val response = parsed.getOrNull()
                    if (response == null) {
                        Log.w(TAG, "menu for $slug did not parse", parsed.exceptionOrNull())
                        unreadable = true
                        continue
                    }
                    // A real restaurant with nothing listed; a later guess may be the one.
                    val menu = response.toMenu(name, now) ?: continue
                    store.write(entry)
                    noMenuUntil.remove(restaurantId)
                    return MenuResult.Found(menu)
                }
            }
        }

        // Disney changing the shape of its response would otherwise read as every
        // restaurant quietly having no menu — the failure that looks like an answer.
        if (unreadable) return MenuResult.Failed("Disney's menu came back in a form this version can't read.")

        noMenuUntil[restaurantId] = now + FRESH_FOR
        // Disney answered for every address and none has a menu: the old copy is no longer
        // true, so it goes rather than being shown as current.
        store.delete(restaurantId)
        return MenuResult.NoMenu
    }

    private fun decode(entry: CachedMenu, name: String): RestaurantMenu? =
        runCatching { json.decodeFromString<DisneyMenuResponse>(entry.body) }.getOrNull()
            ?.toMenu(name, Instant.fromEpochSeconds(entry.fetchedAtEpochSeconds))

    companion object {
        private const val TAG = "MenuRepository"

        /** Menus change on the scale of weeks. A day means a change shows up the next time
         *  the restaurant is opened, at one request per restaurant per day at most. */
        val FRESH_FOR = 24.hours
    }
}
