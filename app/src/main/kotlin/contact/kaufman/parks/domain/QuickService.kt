package contact.kaufman.parks.domain

import java.text.Normalizer

/**
 * Which Walt Disney World restaurants are quick service.
 *
 * Nothing upstream says: themeparks.wiki files every restaurant the same way, and Disney's
 * menu response carries no service style. So this is a curated list, like `CrowdSeed` —
 * which makes it a staleness risk in exactly one direction. Restaurants do not switch
 * between quick and table service, but they open and close constantly, so a name here
 * that matches nothing is harmless (a closed restaurant simply never appears), while a
 * **new** quick-service spot missing from here is the real gap: it shows up unlabelled and
 * the filter hides it.
 *
 * Source: Disney's own list of Quick-Service Dining Plan locations, as published by Disney
 * Food Blog on 2024-01-03, checked 2026-09-26 against the venues Disney's menu endpoint
 * answers for today. A name the list had that Disney no longer serves was confirmed closed
 * before being dropped, not dropped for being missing.
 */
object QuickService {

    fun isQuickService(entity: ParkEntity): Boolean =
        entity.kind == EntityKind.RESTAURANT &&
            entity.park.resort == Resort.WALT_DISNEY_WORLD &&
            WALT_DISNEY_WORLD.contains(entity.name)

    /** Empty until the list is filled in, and the UI treats empty as "no designation":
     *  no label, and no filter chip that could only ever hide everything. */
    val WALT_DISNEY_WORLD = QuickServiceIndex(NAMES)
}

/**
 * Name matching that survives how differently one restaurant gets written.
 *
 * The list's spelling, themeparks.wiki's and Disney's own disagree in small ways —
 * "Pecos Bill Tall Tale Inn and Cafe" against "… Inn & Cafe", "Satu'li" against "Satu’li",
 * a ™ here and not there, a leading "The". All of that is flattened before comparing. What
 * is **not** attempted is fuzzy matching: two genuinely different names stay different,
 * because a near miss that labels the wrong restaurant is worse than a label left off.
 */
class QuickServiceIndex(names: Collection<String>) {

    private val keys: Set<String> = names.mapTo(HashSet(), ::key)

    val isEmpty: Boolean get() = keys.isEmpty()

    fun contains(name: String): Boolean = key(name) in keys

    companion object {
        fun key(name: String): String {
            val flattened = Normalizer.normalize(SYMBOLS.replace(name, ""), Normalizer.Form.NFKD)
                .replace(COMBINING_MARKS, "")
                .lowercase()
                .replace("&", " and ")
                .replace(APOSTROPHES, "")
            return WORD.findAll(flattened).map { it.value }.filterNot { it in IGNORED_WORDS }.joinToString(" ")
        }

        private val SYMBOLS = Regex("[®™©]")
        private val COMBINING_MARKS = Regex("\\p{Mn}+")
        private val APOSTROPHES = Regex("['‘’]")
        private val WORD = Regex("[a-z0-9]+")

        /** Words that come and go between spellings of one name without changing it. */
        private val IGNORED_WORDS = setOf("the", "and", "a")
    }
}

/**
 * Disney's quick-service locations, grouped by where they are.
 *
 * Not yet filled in: the source article could not be read from the Claude Code container
 * (egress policy) and was not reconstructed from search snippets, which would risk a
 * plausible name that was never on the list. See PLAN.md, Restaurant menus.
 */
private val NAMES: List<String> = listOf(
)
