package contact.kaufman.parks.data.passes

import contact.kaufman.parks.domain.PassCalendar
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Turns the body of Disney's pass calendar feed into a [PassCalendar], or null when it
 * cannot.
 *
 * Kept apart from everything around it because it is the one piece that depends on the
 * feed's exact shape, and that shape has not yet been seen.
 */
interface PassCalendarReader {
    fun read(body: String, fetchedAt: Instant): PassCalendar?
}

/**
 * **Not yet written, on purpose.** Disney's feed at
 * `/passes/blockout-dates/api/get-calendars/?months=13` was found through a search index,
 * but its response cannot be seen from the Claude Code container, and a reader written
 * against a guessed shape is the failure this app is built to avoid: plausible blockouts
 * that are wrong, shown to someone deciding whether a friend can come.
 *
 * So until `tools/pass-calendar-survey.py` has been run and its output read, this answers
 * null for every response. The repository reports that as "can't read Disney's pass
 * calendar yet" and the calendar draws no pass controls — nothing claimed either way.
 */
@Singleton
class DisneyPassCalendarReader @Inject constructor(
    @Suppress("unused") private val json: Json,
) : PassCalendarReader {
    override fun read(body: String, fetchedAt: Instant): PassCalendar? = null
}
