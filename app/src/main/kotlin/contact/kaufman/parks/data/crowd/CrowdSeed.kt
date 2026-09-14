package contact.kaufman.parks.data.crowd

import contact.kaufman.parks.domain.Park

/**
 * The starting calibration for the crowd model.
 *
 * The model needs to know what a *normal* posted wait looks like for a given ride on a
 * given day, so it can say "busier than usual" rather than just "45 minutes". That
 * normally takes years of history. This table is the stand-in until the app has banked
 * enough of its own observations to speak for itself — see [contact.kaufman.parks.data.crowd.CrowdBaselines],
 * which overrides these numbers ride-by-ride as real data arrives.
 *
 * Expected wait = [KeyAttraction.baselineMinutes] x [monthFactor] x [dayOfWeekFactor].
 * Splitting it that way means one popularity number per ride and one seasonality curve
 * for all of them, instead of 12 hand-tuned numbers per ride pretending to a precision
 * nobody has.
 *
 * These are hand-set from well-known Orlando seasonality and headliner behaviour. They
 * are deliberately round numbers: they are a starting point, not a measurement.
 */
object CrowdSeed {

    /**
     * A ride the model watches.
     *
     * Following WDW Passport's method, this is a curated set of *established* headliners
     * and reliable mid-tier attractions — not every ride in the park. Brand-new rides are
     * excluded on purpose: their waits are high and flat regardless of how busy the park
     * actually is, so they tell you nothing and drag every day upward.
     */
    data class KeyAttraction(
        val id: String,
        val name: String,
        val baselineMinutes: Int,
    )

    /** Relative to the annual mean. September is the quietest month in Orlando; the
     *  spring-break and Christmas peaks are the two that really hurt. */
    private val MONTH_FACTORS = floatArrayOf(
        0.78f, // January  — post-holiday lull
        0.92f, // February — Presidents' week, Mardi Gras
        1.22f, // March    — spring break
        1.15f, // April    — Easter spike, then a drop
        0.95f, // May
        1.18f, // June     — summer ramp
        1.20f, // July     — peak summer
        0.98f, // August   — tapers as school returns
        0.72f, // September— quietest of the year
        0.90f, // October  — HHN / Not-So-Scary, Jersey week
        0.95f, // November — Thanksgiving spike
        1.15f, // December — holiday peak in the last two weeks
    )

    /** Monday = 1 .. Sunday = 7, matching java.time.DayOfWeek.value. */
    private val DAY_OF_WEEK_FACTORS = floatArrayOf(
        1.00f, // Monday
        0.94f, // Tuesday
        0.95f, // Wednesday
        0.98f, // Thursday
        1.05f, // Friday
        1.15f, // Saturday
        1.08f, // Sunday
    )

    /** [month] is 1-12. */
    fun monthFactor(month: Int): Float = MONTH_FACTORS[(month - 1).coerceIn(0, 11)]

    /** [dayOfWeek] is 1 (Monday) - 7 (Sunday). */
    fun dayOfWeekFactor(dayOfWeek: Int): Float =
        DAY_OF_WEEK_FACTORS[(dayOfWeek - 1).coerceIn(0, 6)]

    fun keyAttractions(park: Park): List<KeyAttraction> = when (park) {
        Park.MAGIC_KINGDOM -> MAGIC_KINGDOM
        Park.EPCOT -> EPCOT
        Park.HOLLYWOOD_STUDIOS -> HOLLYWOOD_STUDIOS
        Park.ANIMAL_KINGDOM -> ANIMAL_KINGDOM
        Park.UNIVERSAL_STUDIOS_FLORIDA -> UNIVERSAL_STUDIOS_FLORIDA
        Park.ISLANDS_OF_ADVENTURE -> ISLANDS_OF_ADVENTURE
        Park.EPIC_UNIVERSE -> EPIC_UNIVERSE
    }

    private val MAGIC_KINGDOM = listOf(
        KeyAttraction("9d4d5229-7142-44b6-b4fb-528920969a2c", "Seven Dwarfs Mine Train", 75),
        KeyAttraction("5a43d1a7-ad53-4d25-abfe-25625f0da304", "TRON Lightcycle / Run", 65),
        KeyAttraction("b2260923-9315-40fd-9c6b-44dd811dbe64", "Space Mountain", 50),
        KeyAttraction("86a41273-5f15-4b54-93b6-829f140e5161", "Peter Pan's Flight", 50),
        KeyAttraction("de3309ca-97d5-4211-bffe-739fed47e92f", "Big Thunder Mountain Railroad", 40),
        KeyAttraction("796b0a25-c51e-456e-9bb8-50a324e301b3", "Jungle Cruise", 40),
        KeyAttraction("2551a77d-023f-4ab1-9a19-8afec0190f39", "Haunted Mansion", 35),
        KeyAttraction("72c7343a-f7fb-4f66-95df-c91016de7338", "Buzz Lightyear's Space Ranger Spin", 30),
        KeyAttraction("352feb94-e52e-45eb-9c92-e4b44c6b1a9d", "Pirates of the Caribbean", 30),
        KeyAttraction("0d94ad60-72f0-4551-83a6-ebaecdd89737", "The Many Adventures of Winnie the Pooh", 30),
        KeyAttraction("f5aad2d4-a419-4384-bd9a-42f86385c750", "\"it's a small world\"", 20),
    )

    private val EPCOT = listOf(
        KeyAttraction("e3549451-b284-453d-9c31-e3b1207abd79", "Guardians of the Galaxy: Cosmic Rewind", 55),
        KeyAttraction("1e735ffb-4868-47f1-b2cd-2ac1156cd5f0", "Remy's Ratatouille Adventure", 50),
        KeyAttraction("8d7ccdb1-a22b-4e26-8dc8-65b1938ed5f0", "Frozen Ever After", 50),
        KeyAttraction("37ae57c5-feaf-4e47-8f27-4b385be200f0", "Test Track", 45),
        KeyAttraction("81b15dfd-cf6a-466f-be59-3dd65d2a2807", "Soarin' Across America", 35),
        KeyAttraction("5b6475ad-4e9a-4793-b841-501aa382c9c0", "Mission: SPACE", 20),
        KeyAttraction("480fde8f-fe58-4bfb-b3ab-052a39d4db7c", "Spaceship Earth", 20),
        KeyAttraction("fb076275-0570-4d62-b2a9-4d6515130fa3", "The Seas with Nemo & Friends", 15),
        KeyAttraction("8f353879-d6ac-4211-9352-4029efb47c18", "Living with the Land", 15),
        KeyAttraction("75449e85-c410-4cef-a368-9d2ea5d52b58", "Journey Into Imagination With Figment", 12),
    )

    private val HOLLYWOOD_STUDIOS = listOf(
        KeyAttraction("1a2e70d9-50d5-4140-b69e-799e950f7d18", "Star Wars: Rise of the Resistance", 75),
        KeyAttraction("399aa0a1-98e2-4d2b-b297-2b451e9665e1", "Slinky Dog Dash", 65),
        KeyAttraction("6e118e37-5002-408d-9d88-0b5d9cdb5d14", "Mickey & Minnie's Runaway Railway", 45),
        KeyAttraction("6f6998e8-a629-412c-b964-2cb06af8e26b", "The Twilight Zone Tower of Terror", 45),
        KeyAttraction("e516f303-e82d-4fd3-8fbf-8e6ab624cf89", "Rock 'n' Roller Coaster", 45),
        KeyAttraction("34c4916b-989b-4ff1-a7e3-a6a846a3484f", "Millennium Falcon: Smugglers Run", 35),
        KeyAttraction("20b5daa8-e1ea-436f-830c-2d7d18d929b5", "Toy Story Mania!", 35),
        KeyAttraction("d56506e2-6ad3-443a-8065-fea37987248d", "Alien Swirling Saucers", 30),
        KeyAttraction("3b290419-8ca2-44bc-a710-a6c83fca76ec", "Star Tours - The Adventures Continue", 25),
    )

    private val ANIMAL_KINGDOM = listOf(
        KeyAttraction("24cf863c-b6ba-4826-a056-0b698989cbf7", "Avatar Flight of Passage", 70),
        KeyAttraction("7a5af3b7-9bc1-4962-92d0-3ea9c9ce35f0", "Na'vi River Journey", 50),
        KeyAttraction("32e01181-9a5f-4936-8a77-0dace1de836c", "Kilimanjaro Safaris", 40),
        KeyAttraction("64a6915f-a835-4226-ba5c-8389fc4cade3", "Expedition Everest", 35),
        KeyAttraction("d58d9262-ec95-4161-80a0-07ca43b2f5f3", "Kali River Rapids", 30),
    )

    private val UNIVERSAL_STUDIOS_FLORIDA = listOf(
        KeyAttraction("70ac72a3-9675-4c41-a1b1-e4801072927a", "Harry Potter and the Escape from Gringotts", 50),
        KeyAttraction("7288f24a-396e-4eeb-bb3b-4a90e65269f2", "Despicable Me Minion Mayhem", 40),
        KeyAttraction("ec25d9a7-b4d4-4ebf-a6c4-c18389351764", "Revenge of the Mummy", 35),
        KeyAttraction("750939c5-a69e-408a-8d55-66c272fa265e", "TRANSFORMERS: The Ride-3D", 35),
        KeyAttraction("7e70bc9e-7dce-4dd2-8823-57b8d6ec7570", "The Simpsons Ride", 30),
        KeyAttraction("91cae293-64f8-48b6-88ec-02dcfcdd1f91", "MEN IN BLACK Alien Attack!", 30),
        KeyAttraction("f0750e5e-7629-4c53-99d2-e0924a8afeed", "Hogwarts Express - King's Cross", 25),
        KeyAttraction("1e16afdd-15e3-4e4a-b3af-8aeebd7534f8", "E.T. Adventure", 25),
        KeyAttraction("25d47d04-a917-405a-9904-9be2b499b2dd", "Villain-Con Minion Blast", 25),
    )

    private val ISLANDS_OF_ADVENTURE = listOf(
        KeyAttraction("578bbd12-1975-4ec3-9879-ea641c780342", "Hagrid's Magical Creatures Motorbike Adventure", 70),
        KeyAttraction("61079a31-4165-4fb0-b36f-c01c5971f80a", "Jurassic World VelociCoaster", 55),
        KeyAttraction("6af80308-647d-4d8b-bcf6-37517a93bdbc", "Harry Potter and the Forbidden Journey", 40),
        KeyAttraction("fa743143-281b-4b5b-b87b-d49fcb006772", "The Incredible Hulk Coaster", 35),
        KeyAttraction("6be23178-7d00-4884-9e88-76787da1df86", "The Amazing Adventures of Spider-Man", 30),
        KeyAttraction("db5b2165-15c2-4e51-8bd1-611e9c351866", "Jurassic Park River Adventure", 30),
        KeyAttraction("370ba4d1-f199-4dc2-be6d-6bb09b442891", "Skull Island: Reign of Kong", 30),
        KeyAttraction("905d7888-b866-4e74-90d1-07fc6ef6706f", "Dudley Do-Right's Ripsaw Falls", 25),
        KeyAttraction("144450b9-4574-46be-abdf-4b1ca8974d9d", "Hogwarts Express - Hogsmeade", 25),
        KeyAttraction("23b613e0-ae83-455b-9163-231bdbd5c427", "Flight of the Hippogriff", 25),
    )

    /**
     * Epic Universe opened in May 2025, so every baseline here is provisional — the park
     * has not settled into a normal year yet. Expect these to be the first numbers the
     * recorded-history override replaces.
     */
    private val EPIC_UNIVERSE = listOf(
        KeyAttraction("dbc4f0d8-fdef-4dfc-a1c2-33917f742f40", "Harry Potter and the Battle at the Ministry", 70),
        KeyAttraction("43df71bf-aa7c-46c0-925c-46f69d8bf23f", "Mario Kart: Bowser's Challenge", 65),
        KeyAttraction("dd8c015d-511f-47d4-b98b-18ce15735588", "Mine-Cart Madness", 55),
        KeyAttraction("447033ce-ee1f-4cca-bb12-47d22583ac12", "Stardust Racers", 55),
        KeyAttraction("1fda5e1f-8712-4165-a81d-ad74eef3e8ee", "Monsters Unchained: The Frankenstein Experiment", 45),
        KeyAttraction("c6b1b8cf-55ef-416c-b00d-e469993617b0", "Hiccup's Wing Gliders", 45),
        KeyAttraction("eaca831d-bcbb-4a1e-9bf0-6ea97ccc88e0", "Curse of the Werewolf", 40),
        KeyAttraction("00feb57b-4fcc-48bc-9490-c9af71f30c1c", "Yoshi's Adventure", 35),
        KeyAttraction("76caa8d0-f54b-4601-9d57-a7f1ddc02af4", "Dragon Racer's Rally", 30),
        KeyAttraction("07143999-bacd-475f-a00b-8cc476204aff", "Constellation Carousel", 25),
    )
}
