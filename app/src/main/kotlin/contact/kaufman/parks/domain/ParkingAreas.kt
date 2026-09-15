package contact.kaufman.parks.domain

/**
 * Where the parking lots actually are, as polygons.
 *
 * themeparks.wiki has no parking data, so — like [ParkingLots] — this is curated. The
 * shapes are traced from OpenStreetMap (ODbL), which maps Disney's guest lots individually
 * and by name; see NOTICE.md. Outlines were simplified to about 5 metres, because the GPS
 * fix being tested against them is good to 5-10 metres at best and more vertices would be
 * false precision.
 *
 * What this can and cannot answer:
 * - **Disney's four parks: the exact named section.** Every lot on the signs is its own
 *   polygon, so a fix lands on "Ursula" rather than merely "Magic Kingdom".
 * - **Universal Studios and Islands of Adventure: the garage, but not the park.** Both
 *   parks are reached from the same two structures, so being in the South Garage says
 *   nothing about which gate you are walking to. Those areas carry a [group] and a null
 *   [park] on purpose — guessing would be wrong half the time.
 * - **Never the garage level.** Universal encodes it in the posted number ("Cat in the Hat
 *   457" is level 4, row 57) and GPS has no usable vertical resolution inside a concrete
 *   deck. That stays a manual pick, as does the row everywhere.
 *
 * Disney Springs is in here too. It is not a theme park, but the car parks work the same
 * way: three garages (Orange, Lime, Grapefruit) and four surface lots named after fruit.
 *
 * Gaps, all of them "OpenStreetMap has not mapped it yet" rather than anything subtler:
 * Hollywood Studios' **BB-8** lot, and four of Epic Universe's five sections (**Monster**,
 * **Viking**, **Gamer**, **Hero**). Epic's two big lots are mapped unnamed, so a fix there
 * still fills in the park and leaves the section blank. A missing lot costs a prefill, not
 * a feature: every field stays typeable.
 */
object ParkingAreas {

    /**
     * One mapped parking area.
     *
     * [park] and [lot] are null where the shape genuinely does not determine them, so a
     * caller can fill in exactly what is known and leave the rest alone.
     */
    data class Area(
        val park: Park?,
        val lot: String?,
        val group: String?,
        /** The OpenStreetMap name, kept so a shape can be traced back to its source. */
        val osmName: String,
        val ring: DoubleArray,
    ) {
        // DoubleArray gives this data class identity equals/hashCode, which is wrong for a
        // value type and a classic source of "why did my test fail" — compare the contents.
        override fun equals(other: Any?): Boolean =
            this === other || (other is Area && osmName == other.osmName && ring.contentEquals(other.ring))

        override fun hashCode(): Int = 31 * osmName.hashCode() + ring.contentHashCode()
    }

    /**
     * The area a position is in, or null when it is in none of them.
     *
     * A fix that is inside nothing but within [NEAR_TOLERANCE_METERS] of an edge counts as
     * that area. Lots abut along their tram aisles, and a car in the aisle — or a fix that
     * has drifted a few metres off one — is still unambiguously in that lot. Where two
     * areas both qualify, the one that names a section wins over one that only names a
     * park, and then the nearer one.
     */
    fun at(latitude: Double, longitude: Double): Area? {
        val containing = all.filter { Geo.ringContains(it.ring, latitude, longitude) }
        if (containing.isNotEmpty()) return containing.pick(latitude, longitude)
        return all
            .filter { Geo.distanceToRingMeters(it.ring, latitude, longitude) <= NEAR_TOLERANCE_METERS }
            .pick(latitude, longitude)
    }

    private fun List<Area>.pick(latitude: Double, longitude: Double): Area? =
        minWithOrNull(
            compareByDescending<Area> { it.lot != null }
                .thenBy { Geo.distanceToRingMeters(it.ring, latitude, longitude) },
        )

    /** Wide enough to cover a tram aisle and a drifting fix, tight enough that it cannot
     *  reach across a lot into its neighbour. */
    const val NEAR_TOLERANCE_METERS = 20.0

    val all: List<Area> = listOf(
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Aladdin",
            group = "Heroes",
            osmName = "Aladdin",
            ring = doubleArrayOf(28.402050, -81.579584, 28.403142, -81.579475, 28.403170, -81.581056, 28.403045, -81.581441, 28.402792, -81.581768, 28.402415, -81.581980, 28.402045, -81.582014),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Mulan",
            group = "Heroes",
            osmName = "Mulan",
            ring = doubleArrayOf(28.399261, -81.581587, 28.397783, -81.580600, 28.397549, -81.580276, 28.397296, -81.580647, 28.397002, -81.580817, 28.398060, -81.582592, 28.399258, -81.583608),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Peter Pan",
            group = "Heroes",
            osmName = "Peter Pan",
            ring = doubleArrayOf(28.401700, -81.580554, 28.401606, -81.580395, 28.401287, -81.580659, 28.400187, -81.580987, 28.399970, -81.580963, 28.399737, -81.580822, 28.399520, -81.580399, 28.399516, -81.581297, 28.400615, -81.581779, 28.401731, -81.581996),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Rapunzel",
            group = "Heroes",
            osmName = "Rapunzel",
            ring = doubleArrayOf(28.399263, -81.581132, 28.397655, -81.580058, 28.397658, -81.578576, 28.398949, -81.578575, 28.399268, -81.578599),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Simba",
            group = "Heroes",
            osmName = "Simba",
            ring = doubleArrayOf(28.399513, -81.583756, 28.400576, -81.584290, 28.401727, -81.584527, 28.401730, -81.582365, 28.400652, -81.582170, 28.399515, -81.581708),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Woody",
            group = "Heroes",
            osmName = "Woody",
            ring = doubleArrayOf(28.402044, -81.583084, 28.402410, -81.583541, 28.402421, -81.584348, 28.402187, -81.584301, 28.402185, -81.584439, 28.402189, -81.584494, 28.402390, -81.584495, 28.402406, -81.584439, 28.402420, -81.584519, 28.402798, -81.584338, 28.403132, -81.583952, 28.403276, -81.583583, 28.403319, -81.583090, 28.403320, -81.581875, 28.403258, -81.581794, 28.402691, -81.582260, 28.402045, -81.582381),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Cruella",
            group = "Villains",
            osmName = "Cruella",
            ring = doubleArrayOf(28.397943, -81.574317, 28.399275, -81.574281, 28.399274, -81.575317, 28.399273, -81.575920, 28.397940, -81.575431),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Hook",
            group = "Villains",
            osmName = "Hook",
            ring = doubleArrayOf(28.399524, -81.576414, 28.401738, -81.577227, 28.401734, -81.578098, 28.401666, -81.578233, 28.401575, -81.578281, 28.401254, -81.578090, 28.400877, -81.577992, 28.400350, -81.578064, 28.399835, -81.578535, 28.399520, -81.578601),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Jafar",
            group = "Villains",
            osmName = "Jafar",
            ring = doubleArrayOf(28.402053, -81.577348, 28.402680, -81.577641, 28.402696, -81.578792, 28.402764, -81.579026, 28.403056, -81.579359, 28.402050, -81.579498),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Scar",
            group = "Villains",
            osmName = "Scar",
            ring = doubleArrayOf(28.399527, -81.574313, 28.400210, -81.574519, 28.401740, -81.575163, 28.401738, -81.576842, 28.399525, -81.576015),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Ursula",
            group = "Villains",
            osmName = "Ursula",
            ring = doubleArrayOf(28.399267, -81.578499, 28.397656, -81.578502, 28.397664, -81.575593, 28.397898, -81.575789, 28.399270, -81.576305),
        ),
        Area(
            park = Park.MAGIC_KINGDOM,
            lot = "Zurg",
            group = "Villains",
            osmName = "Zurg",
            ring = doubleArrayOf(28.402055, -81.575316, 28.403310, -81.575840, 28.403294, -81.577129, 28.402580, -81.577150, 28.402052, -81.576960),
        ),
        Area(
            park = Park.EPCOT,
            lot = "Crush",
            group = "Earth",
            osmName = "Crush",
            ring = doubleArrayOf(28.381588, -81.549950, 28.381553, -81.551213, 28.381347, -81.552365, 28.381306, -81.552277, 28.381138, -81.552481, 28.380400, -81.552499, 28.379855, -81.552341, 28.379599, -81.552310, 28.379448, -81.552396, 28.379425, -81.552510, 28.379335, -81.552319, 28.379554, -81.550988, 28.379584, -81.549572, 28.381502, -81.549581, 28.381575, -81.549612, 28.381487, -81.549855),
        ),
        Area(
            park = Park.EPCOT,
            lot = "Dory",
            group = "Earth",
            osmName = "Dory",
            ring = doubleArrayOf(28.381480, -81.546757, 28.381608, -81.548182, 28.381600, -81.549280, 28.379592, -81.549237, 28.379592, -81.548293, 28.379478, -81.547141),
        ),
        Area(
            park = Park.EPCOT,
            lot = "Hei Hei",
            group = "Earth",
            osmName = "Heihei",
            ring = doubleArrayOf(28.379263, -81.549867, 28.379207, -81.549560, 28.377961, -81.549563, 28.377945, -81.550807, 28.377814, -81.551661, 28.377965, -81.551723, 28.379060, -81.552084, 28.379234, -81.551000),
        ),
        Area(
            park = Park.EPCOT,
            lot = "Moana",
            group = "Earth",
            osmName = "Moana",
            ring = doubleArrayOf(28.379251, -81.549188, 28.379148, -81.547212, 28.377595, -81.547592, 28.377630, -81.549118),
        ),
        Area(
            park = Park.EPCOT,
            lot = "Eve",
            group = "Space",
            osmName = "EVE",
            ring = doubleArrayOf(28.376811, -81.542969, 28.376567, -81.542687, 28.375968, -81.542667, 28.375151, -81.542510, 28.374224, -81.542708, 28.374163, -81.542785, 28.374552, -81.543383, 28.374492, -81.543440, 28.375307, -81.544574, 28.376054, -81.543830, 28.376791, -81.544939, 28.376259, -81.545355, 28.376651, -81.546101, 28.378206, -81.545190, 28.378045, -81.544885, 28.377808, -81.545007, 28.377650, -81.544963, 28.377463, -81.544611, 28.377267, -81.544550, 28.376699, -81.543723, 28.376637, -81.543745, 28.376454, -81.543503, 28.376430, -81.543325),
        ),
        Area(
            park = Park.EPCOT,
            lot = "Gamora",
            group = "Space",
            osmName = "Gamora",
            ring = doubleArrayOf(28.380057, -81.542635, 28.380491, -81.543172, 28.380883, -81.543967, 28.380606, -81.543827, 28.380306, -81.543850, 28.378808, -81.544787, 28.378331, -81.544105, 28.378119, -81.543984, 28.377065, -81.542816, 28.377349, -81.542502, 28.379350, -81.542328, 28.379754, -81.542391),
        ),
        Area(
            park = Park.EPCOT,
            lot = "Rocket",
            group = "Space",
            osmName = "Rocket",
            ring = doubleArrayOf(28.379606, -81.547114, 28.379478, -81.547141, 28.379131, -81.545787, 28.378867, -81.545125, 28.380550, -81.544126, 28.380702, -81.544122, 28.381102, -81.545178, 28.381480, -81.546757),
        ),
        Area(
            park = Park.EPCOT,
            lot = "Wall-E",
            group = "Space",
            osmName = "WALL-E",
            ring = doubleArrayOf(28.379099, -81.547148, 28.379159, -81.547136, 28.378975, -81.546367, 28.378598, -81.545341, 28.377402, -81.546079, 28.377154, -81.546222, 28.377534, -81.547483),
        ),
        Area(
            park = Park.HOLLYWOOD_STUDIOS,
            lot = "Buzz",
            group = null,
            osmName = "Buzz Lightyear Lot",
            ring = doubleArrayOf(28.355708, -81.554455, 28.355925, -81.553954, 28.355667, -81.553354, 28.355995, -81.553115, 28.356660, -81.552693, 28.357754, -81.552221, 28.358671, -81.552030, 28.359501, -81.552068, 28.359452, -81.552412, 28.359259, -81.552516, 28.358327, -81.554475),
        ),
        Area(
            park = Park.HOLLYWOOD_STUDIOS,
            lot = "Jessie",
            group = null,
            osmName = "Jessie Lot",
            ring = doubleArrayOf(28.359086, -81.554740, 28.361598, -81.553809, 28.361695, -81.553649, 28.361921, -81.552744, 28.361896, -81.552538, 28.361811, -81.552415, 28.361321, -81.552171, 28.360934, -81.552068, 28.359501, -81.552068, 28.359452, -81.552412, 28.359546, -81.552548, 28.359510, -81.552662, 28.358452, -81.554742),
        ),
        Area(
            park = Park.HOLLYWOOD_STUDIOS,
            lot = "Mickey",
            group = null,
            osmName = "Mickey Mouse Lot",
            ring = doubleArrayOf(28.354545, -81.556811, 28.355708, -81.554455, 28.358327, -81.554475, 28.357573, -81.556006, 28.357519, -81.555983, 28.357124, -81.556771, 28.357140, -81.556858, 28.356927, -81.556936, 28.354608, -81.556929),
        ),
        Area(
            park = Park.HOLLYWOOD_STUDIOS,
            lot = "Minnie",
            group = null,
            osmName = "Minnie Mouse Lot",
            ring = doubleArrayOf(28.358875, -81.556457, 28.359048, -81.555682, 28.359086, -81.554740, 28.358452, -81.554742, 28.357322, -81.557126, 28.357862, -81.557605, 28.358612, -81.557618),
        ),
        Area(
            park = Park.HOLLYWOOD_STUDIOS,
            lot = "Olaf",
            group = null,
            osmName = "Olaf Lot",
            ring = doubleArrayOf(28.354545, -81.556811, 28.355917, -81.554013, 28.355667, -81.553354, 28.354866, -81.554131, 28.353499, -81.555870, 28.353104, -81.556437, 28.352763, -81.557115, 28.354263, -81.558123, 28.354702, -81.557243),
        ),
        Area(
            park = Park.ANIMAL_KINGDOM,
            lot = "Butterfly",
            group = null,
            osmName = "Butterfly Lot",
            ring = doubleArrayOf(28.352929, -81.585803, 28.351258, -81.586429, 28.351092, -81.585820, 28.351127, -81.585495, 28.351344, -81.584893, 28.351131, -81.584097, 28.351219, -81.584100, 28.351664, -81.584282, 28.352708, -81.585289, 28.352869, -81.585485),
        ),
        Area(
            park = Park.ANIMAL_KINGDOM,
            lot = "Dinosaur",
            group = null,
            osmName = "Dinosaur Lot",
            ring = doubleArrayOf(28.349200, -81.588710, 28.349114, -81.587217, 28.351120, -81.587200, 28.351092, -81.587408, 28.350858, -81.588277, 28.350850, -81.588681),
        ),
        Area(
            park = Park.ANIMAL_KINGDOM,
            lot = "Giraffe",
            group = null,
            osmName = "Giraffe Lot",
            ring = doubleArrayOf(28.351120, -81.587200, 28.350808, -81.586206, 28.350752, -81.585782, 28.349237, -81.585760, 28.349114, -81.587217),
        ),
        Area(
            park = Park.ANIMAL_KINGDOM,
            lot = "Peacock",
            group = null,
            osmName = "Peacock Lot",
            ring = doubleArrayOf(28.351310, -81.587922, 28.351193, -81.588585, 28.351253, -81.588763, 28.351438, -81.588883, 28.353181, -81.588188, 28.352953, -81.587213, 28.352929, -81.585803, 28.351258, -81.586429, 28.351465, -81.587179, 28.351360, -81.587751, 28.351505, -81.587856, 28.351389, -81.587980),
        ),
        Area(
            park = Park.ANIMAL_KINGDOM,
            lot = "Unicorn",
            group = null,
            osmName = "Unicorn Lot",
            ring = doubleArrayOf(28.349831, -81.590301, 28.350866, -81.591264, 28.351763, -81.589839, 28.351596, -81.589731, 28.351028, -81.589106, 28.350851, -81.588698),
        ),
        Area(
            park = Park.ANIMAL_KINGDOM,
            lot = "Yeti",
            group = null,
            osmName = "Yeti Lot",
            ring = doubleArrayOf(28.350252, -81.584088, 28.351026, -81.584117, 28.351044, -81.584319, 28.350753, -81.585748, 28.349237, -81.585760, 28.349253, -81.585665, 28.349527, -81.584440, 28.349853, -81.584213),
        ),
        Area(
            park = Park.EPIC_UNIVERSE,
            lot = "Explorer",
            group = null,
            osmName = "Explorer",
            ring = doubleArrayOf(28.438239, -81.442612, 28.439041, -81.442609, 28.439040, -81.442807, 28.438981, -81.442823, 28.438980, -81.442988, 28.439038, -81.443000, 28.438993, -81.443524, 28.439036, -81.443720, 28.438811, -81.444032, 28.438299, -81.444032, 28.438234, -81.443966),
        ),
        Area(
            park = Park.EPIC_UNIVERSE,
            lot = null,
            group = null,
            osmName = "Parking North",
            ring = doubleArrayOf(28.438314, -81.439067, 28.440076, -81.439096, 28.440450, -81.439329, 28.440545, -81.439588, 28.440536, -81.441889, 28.440477, -81.442180, 28.440287, -81.442270, 28.440282, -81.442534, 28.439069, -81.442538, 28.438991, -81.442477, 28.438987, -81.442608, 28.438239, -81.442612, 28.438248, -81.439192),
        ),
        Area(
            park = Park.EPIC_UNIVERSE,
            lot = null,
            group = null,
            osmName = "Parking South",
            ring = doubleArrayOf(28.436064, -81.442327, 28.438032, -81.442370, 28.438130, -81.442316, 28.438145, -81.439223, 28.438059, -81.439048, 28.436428, -81.439083, 28.436458, -81.439334, 28.435974, -81.441022, 28.435970, -81.441983),
        ),
        Area(
            park = null,
            lot = null,
            group = "North Garage",
            osmName = "Structure North",
            ring = doubleArrayOf(28.474693, -81.461934, 28.477360, -81.461774, 28.477274, -81.459741, 28.474401, -81.459907, 28.474488, -81.461945),
        ),
        Area(
            park = null,
            lot = null,
            group = "South Garage",
            osmName = "Structure South",
            ring = doubleArrayOf(28.472111, -81.463246, 28.471236, -81.463643, 28.471230, -81.463465, 28.470479, -81.461780, 28.470557, -81.461736, 28.472944, -81.460337, 28.473754, -81.462157, 28.473567, -81.462337, 28.472210, -81.463128, 28.472247, -81.463218),
        ),        Area(
            park = Park.DISNEY_SPRINGS,
            lot = "Strawberry",
            group = "Surface lots",
            osmName = "Strawberry Parking Lot",
            ring = doubleArrayOf(28.374052, -81.523897, 28.372894, -81.523860, 28.372894, -81.524248, 28.372236, -81.524273, 28.372038, -81.524084, 28.372102, -81.522451, 28.372145, -81.522398, 28.372878, -81.522420, 28.373006, -81.522424, 28.373000, -81.522671, 28.373736, -81.522705, 28.373794, -81.522642, 28.374414, -81.523721, 28.374336, -81.523940),
        ),
        Area(
            park = Park.DISNEY_SPRINGS,
            lot = "Orange",
            group = "Garages",
            osmName = "Orange Garage",
            ring = doubleArrayOf(28.368059, -81.520382, 28.367947, -81.520061, 28.368840, -81.519661, 28.368866, -81.519730, 28.369638, -81.521956, 28.368745, -81.522357),
        ),
        Area(
            park = Park.DISNEY_SPRINGS,
            lot = "Lemon",
            group = "Surface lots",
            osmName = "Lemon Parking Lot",
            ring = doubleArrayOf(28.372319, -81.513779, 28.372026, -81.513937, 28.372056, -81.513868, 28.371384, -81.514012, 28.371226, -81.514292, 28.370969, -81.514157, 28.370918, -81.514040, 28.370960, -81.513888, 28.371079, -81.513687, 28.371333, -81.513512, 28.371852, -81.513427, 28.372049, -81.513458, 28.372064, -81.513604, 28.372287, -81.513525),
        ),
        Area(
            park = Park.DISNEY_SPRINGS,
            lot = "Lime",
            group = "Garages",
            osmName = "Lime Garage",
            ring = doubleArrayOf(28.369016, -81.515899, 28.369530, -81.516509, 28.370542, -81.515272, 28.370419, -81.515137, 28.370695, -81.514778, 28.370323, -81.514368),
        ),
        Area(
            park = Park.DISNEY_SPRINGS,
            lot = "Mango",
            group = "Surface lots",
            osmName = "Mango Parking Lot",
            ring = doubleArrayOf(28.368848, -81.522434, 28.369035, -81.522897, 28.369209, -81.522897, 28.369966, -81.522515, 28.369794, -81.522070, 28.369597, -81.522126, 28.369563, -81.522013, 28.368952, -81.522289),
        ),
        Area(
            park = Park.DISNEY_SPRINGS,
            lot = "Grapefruit",
            group = "Garages",
            osmName = "Grapefruit Garage",
            ring = doubleArrayOf(28.368193, -81.515632, 28.369384, -81.514203, 28.369511, -81.514053, 28.368896, -81.513356, 28.367500, -81.514900),
        ),
        Area(
            park = Park.DISNEY_SPRINGS,
            lot = "Watermelon",
            group = "Surface lots",
            osmName = "Watermelon Parking Lot",
            ring = doubleArrayOf(28.371327, -81.525496, 28.370955, -81.525449, 28.370171, -81.525078, 28.369741, -81.524717, 28.369718, -81.524619, 28.369915, -81.524358, 28.370622, -81.523569, 28.370945, -81.523835, 28.371385, -81.524008),
        ),
    )
}
