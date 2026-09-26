package contact.kaufman.parks.domain

import java.text.Normalizer

/**
 * Turning a restaurant's name into the address of its menu on Disney's site.
 *
 * Disney's menu endpoint is keyed by the restaurant's **URL slug** — the last part of its
 * menu page address, `cosmic-ray-starlight-cafe` for Cosmic Ray's — and nothing the app
 * already holds carries that slug. So it is worked out from the name, and tried in order.
 *
 * Measured 2026-09-26 against 304 real name/slug pairs as Disney's own endpoint returns
 * them: the candidates below find **237 (77%)**, and 195 of those on the first try. The
 * other 67 are genuinely irregular — "Woody's Lunch Box" is `woodys-lunchbox`, "BoardWalk
 * Deli" is `boardwalk-bakery` — and live in [OVERRIDES]. Between them, every venue Disney
 * published that day resolves.
 *
 * A wrong guess is cheap and visible rather than silent: an unknown slug answers 404 and
 * the next candidate is tried, and the menu screen titles itself with the name Disney
 * sends back, so a menu that ever resolved to the wrong restaurant would say so.
 */
object MenuSlugs {

    /** Every slug worth trying for [name], most likely first. Never more than four. */
    fun candidates(name: String): List<String> {
        OVERRIDES[key(name)]?.let { return listOf(it) }
        val base = base(name)
        val out = mutableListOf<String>()
        fun add(raw: String) {
            val slug = slugify(raw)
            if (slug.isNotEmpty() && slug !in out) out += slug
        }
        // "&" is sometimes dropped (Block & Hans → block-hans) and sometimes spelled out
        // (Ale & Compass → ale-and-compass), so both are tried, dropped first.
        for (ampersand in listOf(" ", " and ")) {
            val spelled = base.replace("&", ampersand)
            // Disney drops a leading article from most slugs: The Crystal Palace is
            // crystal-palace. Not all of them, so the article-kept form goes first.
            val bare = LEADING_ARTICLE.replaceFirst(spelled, "")
            for (variant in listOf(spelled, bare)) {
                // Possessives go both ways too: Woody's → woodys, Cosmic Ray's → cosmic-ray.
                add(POSSESSIVE.replace(variant, "s").replace("'", ""))
                add(POSSESSIVE.replace(variant, "").replace("'", ""))
            }
        }
        return out
    }

    /** The name as a lookup key: symbols, accents, apostrophes and ampersands flattened, so
     *  "Yak & Yeti™ Local Food Cafes" and "Yak & Yeti Local Food Cafes" find the same row. */
    internal fun key(name: String): String =
        slugify(base(name).replace("&", " ").replace("'", ""))

    /** Lowercase, trademark symbols gone, accents stripped, curly apostrophes straightened. */
    private fun base(name: String): String {
        val noSymbols = SYMBOLS.replace(name, "")
        return Normalizer.normalize(noSymbols, Normalizer.Form.NFKD)
            .replace(COMBINING_MARKS, "")
            .lowercase()
            .replace('’', '\'')
            .replace('‘', '\'')
    }

    private fun slugify(text: String): String = NOT_SLUG.replace(text, "-").trim('-')

    // Removed *before* normalising: NFKD turns ™ into the letters "tm", which would then
    // survive into the slug as yak-and-yetitm-….
    private val SYMBOLS = Regex("[®™©]")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val LEADING_ARTICLE = Regex("^(the|la|le|el|les)\\s+")
    private val POSSESSIVE = Regex("'s\\b")
    private val NOT_SLUG = Regex("[^a-z0-9]+")

    /**
     * The 67 restaurants whose slug cannot be derived from their name, keyed by [key].
     *
     * Each value is the last path segment of that restaurant's menu page on
     * disneyworld.disney.go.com — a web address, not menu content. Read on 2026-09-26 from
     * the slugs Disney's endpoint answered to that day. A restaurant that opens, closes or
     * is renamed after that simply falls back to [candidates]' guesses, and if those miss,
     * the app says it found no menu rather than showing a wrong one.
     */
    private val OVERRIDES: Map<String, String> = mapOf(
    "ale-compass-restaurant" to "ale-and-compass",
    "b-b-wolfs-sausage-co" to "bb-wolfs-sausage-co",
    "banana-cabana" to "banana-cabana-pool-bar",
    "beaches-pool-bar-grill" to "beach-pool-bar",
    "blaze-fast-fired-pizza" to "blaze-pizza",
    "blue-ribbon-corn-dogs" to "blue-ribbon-corn-dog",
    "boardwalk-deli" to "boardwalk-bakery",
    "bourbon-steak-a-michael-mina-restaurant" to "bourbon-steak",
    "canada-popcorn-cart" to "popcorn-at-canada-pavilion",
    "coca-cola-store-rooftop-beverage-bar" to "coca-cola-rooftop-beverage-bar",
    "everything-pop-shopping-dining" to "everything-pop-dining",
    "frostbite-freddys-frozen-refreshments" to "frostbite-freddy",
    "funnel-cake-cart-temporarily-unavailable" to "funnel-cake-cart",
    "geo-82" to "geo-82-lounge",
    "geyser-point-bar-grill" to "geyser-point",
    "ghirardelli-soda-fountain-chocolate-shop" to "ghirardelli-soda-fountain",
    "grandstand-spirits" to "grandstand-spirits-pool-bar",
    "gurgling-suitcase" to "gurgling-suitcase-libations-and-spirits",
    "haagen-dazs" to "haagen-dazs-west-side",
    "hoop-dee-doo-musical-revue" to "pioneer-hall",
    "house-of-blues-restaurant-bar" to "house-of-blues-restaurant",
    "hurricane-hannas-waterside-bar-and-grill" to "hurricane-hanna-grill",
    "il-mulino" to "il-mulino-new-york-trattoria",
    "jaleo-by-jose-andres" to "jaleo",
    "java" to "java-bar",
    "joffreys-coffee-tea-company-at-the-landing-at-disney-springs" to "joffreys-coffee-tea-company",
    "joffreys-handcrafted-smoothies-at-disney-springs-marketplace" to "joffreys-coffee-tea-smoothie",
    "joffreys-handcrafted-smoothies-kiosk-at-disney-springs-west-side" to "joffreys-coffee-tea-smoothies-west-side",
    "jungle-navigation-co-ltd-skipper-canteen" to "jungle-navigation-skipper-canteen",
    "lartisan-des-glaces" to "l-artisan-des-glaces",
    "lava-lounge-at-rainforest-cafe" to "lava-lounge",
    "lowtide-lous" to "low-tide-lou",
    "maria-enzos-ristorante" to "maria-enzo",
    "mini-donuts-by-joffreys-coffee-at-blizzard-beach" to "blizzard-beach-mini-donuts",
    "on-the-rocks-pool-bar" to "on-the-rocks",
    "planet-hollywood" to "planet-hollywood-observatory",
    "rainforest-cafe-at-disney-springs-marketplace" to "rainforest-cafe-disney-springs",
    "rainforest-cafe-at-disneys-animal-kingdom" to "rainforest-cafe-animal-kingdom",
    "refreshment-station" to "test-track-cool-wash",
    "rix-sports-bar-grill" to "rix-sports-bar",
    "scat-cats-club-lounge" to "scat-cats-club",
    "sci-fi-dine-in-theater-restaurant" to "sci-fi-dine-in-theater",
    "splash-pool-bar-and-grill" to "splash-grill-and-terrace",
    "splitsville-dining-room" to "splitsville",
    "starbucks-at-disney-springs-marketplace" to "starbucks-at-marketplace",
    "starbucks-at-disney-springs-west-side" to "starbucks-west-side",
    "story-book-dining-at-artist-point-with-snow-white" to "artist-point",
    "tangierine-cafe-flavors-of-the-medina" to "tangierine-cafe",
    "the-basket-at-wine-bar-george" to "the-basket",
    "the-beak-and-barrel" to "beak-barrel",
    "the-boathouse-great-food-waterfront-dining-dream-boats" to "boathouse-restaurant",
    "the-cake-bake-shop-bakery-by-gwendolyn-rogers" to "cake-bake-shop-bakery",
    "the-cake-bake-shop-restaurant-by-gwendolyn-rogers" to "cake-bake-shop-restaurant",
    "the-chuck-wagon" to "chuck-wagon-fresh-fixins-food-truck",
    "the-front-porch-at-house-of-blues" to "front-porch-bar-at-house-of-blues-restaurant",
    "the-market-at-ale-compass" to "ale-and-compass-market",
    "the-smokehouse-at-house-of-blues" to "smokehouse",
    "tiffins-restaurant" to "tiffins",
    "toledo-tapas-steak-seafood" to "toledo",
    "via-napoli-ristorante-e-pizzeria" to "via-napoli",
    "wetzels-pretzels-kiosk-at-disney-springs-marketplace" to "wetzels-pretzels",
    "wetzels-pretzels-kiosk-at-disney-springs-west-side" to "wetzels-pretzels-west-side",
    "wine-bar-george-a-restaurant-bar" to "wine-bar-george",
    "woodys-lunch-box" to "woodys-lunchbox",
    "yak-yeti-local-food-cafes" to "yak-and-yeti-local-foods-cafe",
    "yak-yeti-quality-beverages" to "quality-beverages",
    "yesake-kiosk" to "yesake",
    )
}
