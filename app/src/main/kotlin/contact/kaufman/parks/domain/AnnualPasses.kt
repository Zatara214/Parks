package contact.kaufman.parks.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * The four Walt Disney World Annual Passes, which differ mainly in their blockout dates.
 *
 * Selectable on the calendar, several at once, because the question a local actually asks
 * is rarely about their own pass: it is "which of these days can my friend come too?" Zak
 * holds an Incredi-Pass, which has no blockouts at all; the calendar earns its keep for the
 * friends on the other three.
 *
 * [disneySlug] is the pass's name in Disney's own addresses —
 * `/passes/calendar/disney-incredi-pass/`. The Incredi and Pixie Dust slugs are seen in
 * Disney's published page addresses; Sorcerer and Pirate follow the same pattern and are
 * confirmed by the first read of the pass calendar feed.
 */
enum class AnnualPass(val displayName: String, val shortName: String, val disneySlug: String) {
    INCREDI("Disney Incredi-Pass", "Incredi", "disney-incredi-pass"),
    SORCERER("Disney Sorcerer Pass", "Sorcerer", "disney-sorcerer-pass"),
    PIRATE("Disney Pirate Pass", "Pirate", "disney-pirate-pass"),
    PIXIE_DUST("Disney Pixie Dust Pass", "Pixie Dust", "disney-pixie-dust-pass"),
    ;

    companion object {
        fun fromSlug(slug: String): AnnualPass? = entries.firstOrNull { it.disneySlug == slug }
    }
}

/**
 * What Disney's pass calendar says: each pass's blocked days, and the Good-to-Go days.
 *
 * Read from Disney's own feed behind its public per-pass calendar pages, and refreshed at
 * most weekly — blockouts are set a year ahead, and Good-to-Go days arrive in batches a few
 * weeks at a time, so a week-old copy is current for everything that matters.
 */
data class PassCalendar(
    /** For each pass, the parks blocked on each blocked date. A date absent is not blocked. */
    val blockouts: Map<AnnualPass, Map<LocalDate, Set<Park>>>,
    /** Days an Annual Passholder needs no park reservation. */
    val goodToGo: Set<LocalDate>,
    val fetchedAt: Instant,
)

/** One pass blocked on one day, at some or all of the parks. */
data class PassBlockout(val pass: AnnualPass, val parks: Set<Park>) {
    /** Blocked everywhere — "blocked out" — rather than at one park. */
    val isEveryPark: Boolean get() = parks.containsAll(DISNEY_THEME_PARKS)

    companion object {
        val DISNEY_THEME_PARKS: Set<Park> = setOf(
            Park.MAGIC_KINGDOM, Park.EPCOT, Park.HOLLYWOOD_STUDIOS, Park.ANIMAL_KINGDOM,
        )
    }
}
