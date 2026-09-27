package contact.kaufman.parks.data.passes

import android.util.Log
import contact.kaufman.parks.data.disney.DisneyResponse
import contact.kaufman.parks.data.disney.getFromDisney
import contact.kaufman.parks.domain.PassCalendar
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

sealed interface PassCalendarResult {
    /** [refreshNote] is set when this is an older copy because a check just failed. */
    data class Loaded(val calendar: PassCalendar, val refreshNote: String? = null) : PassCalendarResult

    /** Never read, and cannot be read now. */
    data class Unavailable(val message: String) : PassCalendarResult
}

/**
 * Annual Pass blockouts and Good-to-Go days, from Disney's own pass calendar feed.
 *
 * **Weekly, on open — never in the background.** Blockouts are set a year ahead and
 * Good-to-Go days arrive in batches a few weeks at a time, so Zak's requirement was a
 * check every week or two. This app does no background work at all, so the week is
 * enforced here instead: the phone's copy is used until it is [FRESH_FOR] old, and the
 * first time the calendar is opened after that, Disney is asked again. A new batch of
 * Good-to-Go days therefore shows up within a week of it being published, at one request
 * a week at most. Pull-to-refresh asks immediately.
 *
 * A failed check keeps the copy on the phone and says so, the rule the rest of the app
 * follows. One request returns every pass, so choosing a friend's pass costs nothing more.
 */
@Singleton
class PassCalendarRepository @Inject constructor(
    private val client: HttpClient,
    private val store: PassCalendarStore,
    private val reader: PassCalendarReader,
) {
    private var inMemory: PassCalendar? = null

    suspend fun calendar(force: Boolean = false, now: Instant = Clock.System.now()): PassCalendarResult {
        val stored = store.read()
        val storedAt = stored?.let { Instant.fromEpochSeconds(it.fetchedAtEpochSeconds) }
        val cached = inMemory ?: stored
            ?.let { reader.read(it.body, Instant.fromEpochSeconds(it.fetchedAtEpochSeconds)) }
            ?.also { inMemory = it }
        if (!force) {
            if (cached != null && now - cached.fetchedAt < FRESH_FOR) return PassCalendarResult.Loaded(cached)
            // A recent copy this version cannot read gets the same week's grace as a readable
            // one. Asking again on every open would only fetch the same unreadable answer.
            if (cached == null && storedAt != null && now - storedAt < FRESH_FOR) {
                return PassCalendarResult.Unavailable("Can't read Disney's pass calendar yet.")
            }
        }

        return when (val response = client.getFromDisney(ENDPOINT, mapOf("months" to MONTHS.toString()))) {
            is DisneyResponse.Found -> {
                val calendar = reader.read(response.body, now)
                if (calendar == null) {
                    // Keep the body regardless: once the reader can understand it, the copy
                    // on the phone becomes readable without asking Disney again.
                    store.write(CachedPassCalendar(now.epochSeconds, response.body))
                    Log.w(TAG, "pass calendar did not read (${response.body.length} bytes)")
                    cached?.let { PassCalendarResult.Loaded(it, "Disney's pass calendar has changed format.") }
                        ?: PassCalendarResult.Unavailable("Can't read Disney's pass calendar yet.")
                } else {
                    store.write(CachedPassCalendar(now.epochSeconds, response.body))
                    inMemory = calendar
                    PassCalendarResult.Loaded(calendar)
                }
            }
            DisneyResponse.Blocked, DisneyResponse.NotFound, is DisneyResponse.Failed -> {
                Log.w(TAG, "pass calendar check failed: $response")
                cached?.let { PassCalendarResult.Loaded(it, "Couldn't check Disney for new dates.") }
                    ?: PassCalendarResult.Unavailable("Couldn't reach Disney's pass calendar.")
            }
        }
    }

    companion object {
        private const val TAG = "PassCalendar"

        const val ENDPOINT = "https://disneyworld.disney.go.com/passes/blockout-dates/api/get-calendars/"

        /** What Disney's own calendar page asks for: the coming year and a month. */
        const val MONTHS = 13

        /** Zak's call: Good-to-Go batches land a few weeks apart, so weekly is current. */
        val FRESH_FOR = 7.days
    }
}
