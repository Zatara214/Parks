package contact.kaufman.parks.domain

/** The resort a park belongs to. Drives grouping on the dashboard and which
 *  official app a hand-off targets. */
enum class Resort(val displayName: String, val shortName: String) {
    WALT_DISNEY_WORLD("Walt Disney World", "WDW"),
    UNIVERSAL_ORLANDO("Universal Orlando", "UOR"),
}

/**
 * The seven parks Parks covers. IDs are themeparks.wiki entity UUIDs, verified
 * against /v1/destinations on 2026-09-13.
 *
 * Water parks are deliberately absent and are not coming back: Typhoon Lagoon,
 * Blizzard Beach and Volcano Bay all exist upstream and are filtered out here.
 *
 * **Every coordinate is a point inside that park's mapped boundary**, corrected on
 * 2026-09-15 and pinned by a test. They used to be eyeballed, and three were wrong:
 * Animal Kingdom's and Universal Studios' sat outside their own footprints, and **Epic
 * Universe's was 3.6km out, down by SeaWorld** — so Epic could never be detected at all
 * and it dragged Universal's resort weather point a kilometre south. Nothing decides which
 * park you are in from these any more (see [ParkBoundaries]), but `Geo.resortCenter` still
 * averages them for weather, so they have to be honest.
 */
/**
 * What kind of place this is, because not all of them are theme parks.
 *
 * The distinction is load-bearing rather than descriptive: a [DINING_DISTRICT] has no
 * themeparks.wiki entity, so nothing may call `/live`, `/schedule` or `/children` with its
 * id, and it has no rides, so the crowd model has nothing to measure.
 */
enum class ParkKind {
    THEME_PARK,

    /**
     * Disney Springs. Shops and restaurants, no gate and no rides.
     *
     * Zak goes often, so it earns a place on the dashboard even though most of what the
     * app does elsewhere does not apply. What it does have: a mapped boundary, so "where
     * you are" works; mapped car parks, so a spot can be recorded; and restaurants, which
     * come from the Walt Disney World *destination* feed rather than a park's.
     */
    DINING_DISTRICT,
}

enum class Park(
    val id: String,
    val displayName: String,
    val shortName: String,
    val resort: Resort,
    val latitude: Double,
    val longitude: Double,
    val kind: ParkKind = ParkKind.THEME_PARK,
) {
    MAGIC_KINGDOM(
        id = "75ea578a-adc8-4116-a54d-dccb60765ef9",
        displayName = "Magic Kingdom",
        shortName = "MK",
        resort = Resort.WALT_DISNEY_WORLD,
        latitude = 28.4189, longitude = -81.5810,
    ),
    EPCOT(
        id = "47f90d2c-e191-4239-a466-5892ef59a88b",
        displayName = "EPCOT",
        shortName = "EP",
        resort = Resort.WALT_DISNEY_WORLD,
        latitude = 28.3720, longitude = -81.5490,
    ),
    HOLLYWOOD_STUDIOS(
        id = "288747d1-8b4f-4a64-867e-ea7c9b27bad8",
        displayName = "Hollywood Studios",
        shortName = "HS",
        resort = Resort.WALT_DISNEY_WORLD,
        latitude = 28.3576, longitude = -81.5592,
    ),
    ANIMAL_KINGDOM(
        id = "1c84a229-8862-4648-9c71-378ddd2c7693",
        displayName = "Animal Kingdom",
        shortName = "AK",
        resort = Resort.WALT_DISNEY_WORLD,
        latitude = 28.3582, longitude = -81.5909,
    ),
    /**
     * Not a themeparks.wiki entity — its [id] is a local key for the database, never a
     * request. Anything that reaches the API must check [kind] first.
     */
    DISNEY_SPRINGS(
        id = "disney-springs",
        displayName = "Disney Springs",
        shortName = "DS",
        resort = Resort.WALT_DISNEY_WORLD,
        latitude = 28.3709, longitude = -81.5177,
        kind = ParkKind.DINING_DISTRICT,
    ),

    UNIVERSAL_STUDIOS_FLORIDA(
        id = "eb3f4560-2383-4a36-9152-6b3e5ed6bc57",
        displayName = "Universal Studios Florida",
        shortName = "USF",
        resort = Resort.UNIVERSAL_ORLANDO,
        latitude = 28.4757, longitude = -81.4686,
    ),
    ISLANDS_OF_ADVENTURE(
        id = "267615cc-8943-4c2a-ae2c-5da728ca591f",
        displayName = "Islands of Adventure",
        shortName = "IOA",
        resort = Resort.UNIVERSAL_ORLANDO,
        latitude = 28.4718, longitude = -81.4698,
    ),
    EPIC_UNIVERSE(
        id = "12dbb85b-265f-44e6-bccf-f1faa17211fc",
        displayName = "Epic Universe",
        shortName = "EU",
        resort = Resort.UNIVERSAL_ORLANDO,
        latitude = 28.4425, longitude = -81.4484,
    );

    /**
     * Does the posted space number already contain the level?
     *
     * True only at Universal, where "Cat in the Hat 457" means level 4, row 57. Disney
     * Springs' garages have levels but do not encode them this way, so writing "412" there
     * would be a number nobody could match to a sign.
     */
    val mergesLevelIntoRow: Boolean
        get() = this == UNIVERSAL_STUDIOS_FLORIDA || this == ISLANDS_OF_ADVENTURE

    companion object {
        private val byId = entries.associateBy(Park::id)

        fun fromId(id: String): Park? = byId[id]
    }
}
