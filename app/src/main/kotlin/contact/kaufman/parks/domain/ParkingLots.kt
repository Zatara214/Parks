package contact.kaufman.parks.domain

/**
 * The named parking sections at each park.
 *
 * themeparks.wiki has no parking data, so this is a curated table, verified against
 * Disney Parks Blog, Universal's own app listings and on-site photo coverage
 * (2026-09-14). Section names change — EPCOT's were renamed wholesale in January 2023 —
 * so treat a lot that has vanished from the signage as a bug report, not a mystery.
 *
 * Nothing here is a closed list from the app's point of view: the lot field still accepts
 * anything typed, because a stale table must never stop someone recording where the car is.
 */
object ParkingLots {

    /** Lots sometimes split into named halves with their own tram loops — knowing which
     *  side you are on matters more than the section name when walking back. */
    data class LotGroup(val name: String?, val lots: List<String>)

    fun groupsFor(park: Park): List<LotGroup> = when (park) {
        Park.MAGIC_KINGDOM -> listOf(
            LotGroup("Heroes", listOf("Aladdin", "Mulan", "Peter Pan", "Rapunzel", "Simba", "Woody")),
            LotGroup("Villains", listOf("Cruella", "Hook", "Jafar", "Scar", "Ursula", "Zurg")),
        )
        Park.EPCOT -> listOf(
            LotGroup("Earth", listOf("Moana", "Hei Hei", "Crush", "Dory")),
            LotGroup("Space", listOf("Wall-E", "Eve", "Rocket", "Gamora")),
        )
        Park.HOLLYWOOD_STUDIOS -> listOf(
            LotGroup(null, listOf("Mickey", "Minnie", "Jessie", "Buzz", "Olaf", "BB-8")),
        )
        Park.ANIMAL_KINGDOM -> listOf(
            LotGroup(null, listOf("Peacock", "Giraffe", "Unicorn", "Dinosaur", "Butterfly", "Yeti")),
        )
        // USF and IOA share the same two garages, reached from the same CityWalk hub.
        Park.UNIVERSAL_STUDIOS_FLORIDA, Park.ISLANDS_OF_ADVENTURE -> listOf(
            LotGroup("North Garage", listOf("Jaws", "King Kong", "Jurassic Park")),
            LotGroup("South Garage", listOf("Spider-Man", "Cat in the Hat", "E.T.")),
        )
        Park.EPIC_UNIVERSE -> listOf(
            LotGroup(null, listOf("Explorer", "Monster", "Viking", "Gamer", "Hero")),
        )
    }

    fun lotsFor(park: Park): List<String> = groupsFor(park).flatMap { it.lots }

    /**
     * Universal's garages encode the level into the posted number: "Cat in the Hat 457"
     * is level 4, row 57. Splitting that into two fields is what the sign already means,
     * and it saves guessing at the boundary later.
     *
     * Every Disney lot is surface parking, and Epic Universe's is flat too.
     */
    fun hasLevels(park: Park): Boolean = when (park) {
        Park.UNIVERSAL_STUDIOS_FLORIDA, Park.ISLANDS_OF_ADVENTURE -> true
        else -> false
    }

    /** The garages run to six floors; the north side stops at five. */
    val GARAGE_LEVELS = (1..6).map(Int::toString)
}
