package contact.kaufman.parks.domain

import java.util.Locale
import kotlin.time.Instant

/**
 * A restaurant's menu, as Disney published it the last time the app asked.
 *
 * **Walt Disney World only**, Disney Springs included. Universal publishes menus as web
 * pages with no data behind them, so its restaurants have no menu to open.
 *
 * Fetched on demand when a restaurant is opened, never in the background, and kept on the
 * phone so it still opens in a park with no signal. Nothing is redistributed: what is cached
 * is the one response this phone received, the same thing a browser cache would hold.
 */
data class RestaurantMenu(
    /** The name Disney sent back, not the one the app asked with — so a menu that ever
     *  resolved to the wrong restaurant says so in its own title. */
    val restaurantName: String,
    val periods: List<MealPeriod>,
    val fetchedAt: Instant,
) {
    val itemCount: Int get() = periods.sumOf { p -> p.sections.sumOf { it.items.size } }
}

/** "Breakfast", "Lunch And Dinner", "Special Ticketed Event" — Disney's own labels. */
data class MealPeriod(val name: String, val sections: List<MenuSection>)

/** "Entrées", "Featured Offerings", "Beverages". */
data class MenuSection(val name: String, val items: List<MenuItem>)

data class MenuItem(
    val name: String,
    val description: String?,
    /** "$14.99", or "$4.29 – $6.49" where Disney lists more than one size. Null when Disney
     *  gives no price, which is left blank rather than drawn as $0.00. */
    val price: String?,
)

object Menus {

    /**
     * Disney's pre-tax prices as a label.
     *
     * Formatting only: the numbers are Disney's, and nothing here adds tax, rounds, or
     * does sums. Several prices for one item are sizes, shown as a range.
     */
    fun priceLabel(prices: List<Double>): String? {
        val valid = prices.filter { it.isFinite() && it >= 0.0 }
        if (valid.isEmpty()) return null
        val low = valid.min()
        val high = valid.max()
        return if (low == high) dollars(low) else "${dollars(low)} – ${dollars(high)}"
    }

    private fun dollars(amount: Double) = String.format(Locale.US, "$%.2f", amount)

    /**
     * Disney's descriptions arrive as HTML fragments — `<p>`, `<br>`, `&amp;`. Tags are
     * dropped, the handful of entities that actually appear are decoded, and whitespace is
     * collapsed. A description that is nothing once cleaned becomes null, so the screen
     * does not draw an empty line under the item.
     */
    fun cleanDescription(html: String?): String? {
        if (html == null) return null
        val text = TAGS.replace(html, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .let { NUMERIC_ENTITY.replace(it) { m -> m.groupValues[1].toIntOrNull()?.let(::codePoint) ?: m.value } }
        return WHITESPACE.replace(text, " ").trim().takeIf { it.isNotEmpty() }
    }

    private fun codePoint(value: Int): String =
        runCatching { String(Character.toChars(value)) }.getOrDefault("")

    /**
     * Which meal to open on, from the hour in park time.
     *
     * Disney's labels are free text and often combined ("Lunch And Dinner"), so this looks
     * for the word for the current meal inside each label and takes the first that has it.
     * Anything it cannot place opens on the first period, which is how Disney orders them.
     */
    fun defaultPeriodIndex(periodNames: List<String>, hour: Int): Int {
        val wanted = when {
            hour < 11 -> "breakfast"
            hour < 16 -> "lunch"
            else -> "dinner"
        }
        val index = periodNames.indexOfFirst { it.contains(wanted, ignoreCase = true) }
        return if (index >= 0) index else 0
    }

    private val TAGS = Regex("<[^>]*>")
    private val NUMERIC_ENTITY = Regex("&#(\\d+);")
    private val WHITESPACE = Regex("\\s+")
}
