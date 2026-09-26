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
 * Source and reconciliation: see [NAMES]. The rule, Zak's: a listed restaurant Parks does
 * not recognise is checked for closure before anything is done with it.
 */
object QuickService {

    fun isQuickService(entity: ParkEntity): Boolean =
        entity.kind == EntityKind.RESTAURANT &&
            entity.park.resort == Resort.WALT_DISNEY_WORLD &&
            WALT_DISNEY_WORLD.contains(entity.name)

    /** An empty index would read as "no designation": no label, and no filter chip that
     *  could only ever hide everything. */
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
 * Disney's quick-service locations, grouped by where they are — 121 names.
 *
 * From Disney's Quick-Service Dining Plan list as Disney Food Blog published it on
 * 2024-01-03, reconciled on 2026-09-26:
 *
 * - **101 names match a venue Disney's menu endpoint serves today** and are written as
 *   Disney writes them now. The list's own spelling is kept beside it where it reads
 *   differently, since a second spelling of one restaurant costs nothing.
 * - **Six were renamed or respelled**, not closed: Sleepy Hollow Refreshments is now
 *   "Sleepy Hollow", Kringla's "Cafe" is "Kafé", Tangerine is Disney's "Tangierine",
 *   Grandstand Spirits dropped "Pool Bar", Beaches Pool Bar gained "& Grill", and Turtle
 *   Shack is "Turtle Shack Poolside Snacks".
 * - **Fifteen were missing from Disney's menu list and searched for closure** one by one.
 *   That list is hand-picked and skips whole resorts (the Riviera, for one), so missing is
 *   not closed. Fourteen are open. One closed: **Refreshment Port** shut on 2026-01-12 and
 *   reopened on 2026-07-01 as **La Poutinerie**, still quick service, which replaces it.
 * - **Sanaa is deliberately left off.** The list includes it, but it is quick service only
 *   at breakfast and table service at lunch and dinner, and this label covers the whole
 *   restaurant — "Quick service" on Sanaa would be wrong for two meals of three.
 * - Water parks are dropped: Parks does not cover them, so their names could never match.
 *
 * Resort names are kept although resorts are not built yet: they are what a resort screen
 * will need, and until then they match nothing and cost nothing.
 */
private val NAMES: List<String> = listOf(
    // Magic Kingdom
    "Casey's Corner",
    "Columbia Harbour House",
    "Cosmic Ray's Starlight Café",
    "The Friar's Nook",
    "Gaston's Tavern",
    "Golden Oak Outpost",
    "Liberty Square Market",
    "The Lunching Pad",
    "Main Street Bakery",
    "Pecos Bill Tall Tale Inn and Cafe",
    "Pinocchio Village Haus",
    "Sleepy Hollow",
    "Sleepy Hollow Refreshments",
    // EPCOT
    "Connections Café",
    "Connections Eatery",
    "Crêpes À Emporter at La Crêperie de Paris",
    "Crêpes À Emporter by La Crêperie de Paris",
    "Fife & Drum Tavern",
    "Katsura Grill",
    "Kringla Bakeri Og Kafé",
    "Kringla Bakeri Og Cafe",
    "La Cantina de San Angel",
    "Les Halles Boulangerie-Patisserie",
    "Lotus Blossom Café",
    "Pizza al Taglio",
    "Refreshment Outpost",
    "Regal Eagle Smokehouse: Craft Drafts & Barbecue",
    "Sommerfest",
    "Sunshine Seasons",
    "Tangierine Café: Flavors of the Medina",
    "Tangerine Cafe: Flavors of the Medina",
    "Yorkshire County Fish Shop",
    "La Poutinerie",
    "La Poutinerie Hosted by Air Canada",
    // Disney's Hollywood Studios
    "ABC Commissary",
    "Backlot Express",
    "Catalina Eddie's",
    "Docking Bay 7 Food and Cargo",
    "Dockside Diner",
    "Fairfax Fare",
    "Ronto Roasters",
    "Rosie's All-American Café",
    "The Trolley Car Café",
    "Woody's Lunch Box",
    // Disney's Animal Kingdom
    "Creature Comforts",
    "Flame Tree Barbecue",
    "Harambe Market",
    "Pizzafari",
    "Satu'li Canteen",
    "Yak & Yeti™ Local Food Cafes",
    // Disney Springs
    "eet by Maneet Chauhan",
    "Amorette's Patisserie",
    "B.B. Wolf's Sausage Co.",
    "Blaze Fast-Fire'd Pizza",
    "Chicken Guy!",
    "Cookes of Dublin",
    "The Daily Poutine",
    "D-Luxe Burger",
    "EARL OF SANDWICH®",
    "Everglazed Donuts & Cold Brew",
    "Marketplace Snacks",
    "Morimoto Asia™ Street Food",
    "Pepe",
    "Pepe by José Andrés",
    "Pizza Ponte",
    "The Polite Pig",
    "The Smokehouse at House of Blues®",
    "Swirls on the Water",
    "YeSake Kiosk",
    // Disney's All-Star Resorts
    "World Premiere Food Court",
    "Silver Screen Spirits Pool Bar",
    "Intermission Food Court",
    "Singing Spirits Pool Bar",
    "End Zone Food Court",
    "Grandstand Spirits",
    "Grandstand Spirits Pool Bar",
    // Disney's Animal Kingdom Lodge
    "Maji Pool Bar",
    "The Mara",
    // Disney's Art of Animation Resort
    "Landscape of Flavors",
    "The Drop Off Pool Bar",
    // Disney's BoardWalk Inn
    "Blue Ribbon Corn Dogs",
    "BoardWalk Deli",
    "Carousel Coffee",
    "Leaping Horse Libations",
    // Disney's Caribbean Beach Resort
    "Centertown Market",
    "Centertown Market Grab ‘n Go",
    "Spyglass Grill",
    // Disney's Contemporary Resort
    "Contempo Café",
    "Cove Bar",
    "The Sand Bar",
    // Disney's Coronado Springs Resort
    "Barcelona Lounge",
    "Cafe Rix",
    "El Mercado de Coronado",
    "Siestas Cantina",
    // Disney's Fort Wilderness Resort & Campground
    "Trail's End Restaurant",
    "The Chuck Wagon",
    "Meadow Snack Bar",
    // Disney's Grand Floridian Resort
    "Beaches Pool Bar & Grill",
    "Beaches Pool Bar",
    "Courtyard Pool Bar",
    "Gasparilla Island Grill",
    // Disney's Old Key West Resort
    "Good's Food to Go",
    "Turtle Shack Poolside Snacks",
    "Turtle Shack",
    // Disney's Polynesian Village Resort
    "Capt. Cook's",
    "Oasis Bar & Grill",
    // Disney's Pop Century Resort
    "Everything POP Shopping & Dining",
    "Petals Pool Bar",
    // Disney's Port Orleans Resort — Riverside
    "Muddy Rivers",
    "Riverside Mill Food Court",
    // Disney's Port Orleans Resort — French Quarter
    "Mardi Grogs",
    "Sassagoula Floatworks and Food Factory",
    // Disney's Riviera Resort
    "Primo Piatto",
    // Disney's Saratoga Springs Resort
    "The Artist's Palette",
    "Backstretch Pool Bar",
    "On the Rocks Pool Bar",
    "The Paddock Grill",
    // Disney's Wilderness Lodge
    "Roaring Fork",
    // Disney's Yacht & Beach Club Resorts
    "Beach Club Marketplace",
    "Hurricane Hanna's Waterside Bar and Grill",
    "The Market at Ale & Compass",
)
