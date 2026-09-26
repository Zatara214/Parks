package contact.kaufman.parks.data.menu

import contact.kaufman.parks.domain.MealPeriod
import contact.kaufman.parks.domain.MenuItem
import contact.kaufman.parks.domain.MenuSection
import contact.kaufman.parks.domain.Menus
import contact.kaufman.parks.domain.RestaurantMenu
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Wire types for Disney's menu endpoint, `/dining/dinemenu/api/menu?searchTerm=<slug>`.
 *
 * **Undocumented.** This is what Disney's own menu pages load; nobody promised it to
 * anyone. Every field is optional and anything unknown is ignored, so an added field
 * changes nothing and a removed one degrades to a blank rather than a crash. The shape was
 * read from a scraper that has swept it every three hours since at least September 2026:
 * meal periods, each holding groups, each holding items with a list of prices.
 */
@Serializable
data class DisneyMenuResponse(
    val name: String? = null,
    val mealPeriods: List<MealPeriodDto> = emptyList(),
)

@Serializable
data class MealPeriodDto(
    val label: String? = null,
    val name: String? = null,
    val groups: List<MenuGroupDto> = emptyList(),
)

@Serializable
data class MenuGroupDto(
    val name: String? = null,
    /** Kept loose on purpose. Nothing says what shape this is, and a string today that
     *  becomes an object tomorrow must not take the whole menu down with it. */
    val type: JsonElement? = null,
    val items: List<MenuItemDto> = emptyList(),
)

@Serializable
data class MenuItemDto(
    val title: String? = null,
    val description: String? = null,
    val prices: List<MenuPriceDto> = emptyList(),
)

@Serializable
data class MenuPriceDto(
    /** Dollars, before tax. The only price field the app reads. */
    val withoutTax: Double? = null,
)

/**
 * The response as the app's own model, or null when there is nothing on it to show.
 *
 * Allergy-friendly groups are left out. Disney publishes them as a second copy of the same
 * dishes, so including them roughly doubles a quick-service menu with repeats; the official
 * app is the better place for that question anyway, since it can say what a kitchen will
 * actually accommodate.
 */
fun DisneyMenuResponse.toMenu(fallbackName: String, fetchedAt: Instant): RestaurantMenu? {
    val periods = mealPeriods.mapNotNull { period ->
        val sections = period.groups
            .filterNot { it.isAllergyFriendly() }
            .mapNotNull { group ->
                val items = group.items.mapNotNull { item ->
                    val title = item.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    MenuItem(
                        name = title,
                        description = Menus.cleanDescription(item.description),
                        price = Menus.priceLabel(item.prices.mapNotNull { it.withoutTax }),
                    )
                }
                items.takeIf { it.isNotEmpty() }?.let { MenuSection(group.name?.trim().orEmpty(), it) }
            }
        val label = (period.label ?: period.name)?.trim()?.takeIf { it.isNotEmpty() } ?: "Menu"
        sections.takeIf { it.isNotEmpty() }?.let { MealPeriod(label, it) }
    }
    if (periods.isEmpty()) return null
    return RestaurantMenu(
        restaurantName = name?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackName,
        periods = periods,
        fetchedAt = fetchedAt,
    )
}

private fun MenuGroupDto.isAllergyFriendly(): Boolean {
    val typeText = (type as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    return typeText.contains("allergy", ignoreCase = true) ||
        name.orEmpty().contains("allergy", ignoreCase = true)
}
