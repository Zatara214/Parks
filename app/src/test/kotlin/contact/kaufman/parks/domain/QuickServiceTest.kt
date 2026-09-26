package contact.kaufman.parks.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One restaurant, spelled the way three different sources spell it, must match itself —
 * and two different restaurants must never match each other.
 */
class QuickServiceTest {

    private val index = QuickServiceIndex(
        listOf("Pecos Bill Tall Tale Inn and Cafe", "Satu'li Canteen", "Yak & Yeti Local Food Cafes", "The Lunching Pad"),
    )

    @Test
    fun `an ampersand matches the word and`() {
        assertTrue(index.contains("Pecos Bill Tall Tale Inn & Cafe"))
    }

    @Test
    fun `apostrophe style and trademark marks do not matter`() {
        assertTrue(index.contains("Satu’li Canteen"))
        assertTrue(index.contains("Yak & Yeti™ Local Food Cafes"))
    }

    @Test
    fun `a leading The does not matter`() {
        assertTrue(index.contains("Lunching Pad"))
    }

    @Test
    fun `case and accents do not matter`() {
        assertTrue(index.contains("SATU'LI CANTEEN"))
        assertTrue(QuickServiceIndex(listOf("Cosmic Ray's Starlight Café")).contains("Cosmic Ray's Starlight Cafe"))
    }

    /** No fuzzy matching: a near miss that labels the wrong restaurant is worse than none. */
    @Test
    fun `a different restaurant with a similar name does not match`() {
        assertFalse(index.contains("Pecos Bill Tall Tale Inn"))
        assertFalse(index.contains("Yak & Yeti Restaurant"))
        assertFalse(index.contains("Yak & Yeti Quality Beverages"))
    }

    @Test
    fun `an empty list designates nothing`() {
        val empty = QuickServiceIndex(emptyList())
        assertTrue(empty.isEmpty)
        assertFalse(empty.contains("Casey's Corner"))
    }

    // The real list, reconciled 2026-09-26.

    private val list = QuickService.WALT_DISNEY_WORLD

    @Test
    fun `the quick-service staples are designated`() {
        listOf("Woody's Lunch Box", "Casey's Corner", "Cosmic Ray's Starlight Cafe", "Satu’li Canteen",
            "Pecos Bill Tall Tale Inn & Cafe", "Sunshine Seasons", "Regal Eagle Smokehouse: Craft Drafts & Barbecue")
            .forEach { assertTrue("$it should be quick service", list.contains(it)) }
    }

    /** Renamed since the 2024 list, and found under Disney's current name. */
    @Test
    fun `restaurants are found under their current names`() {
        assertTrue(list.contains("Tangierine Café: Flavors of the Medina"))
        assertTrue(list.contains("Turtle Shack Poolside Snacks"))
        assertTrue(list.contains("Sleepy Hollow"))
    }

    /** Refreshment Port closed 2026-01-12 and reopened 2026-07-01 as La Poutinerie. */
    @Test
    fun `a closed restaurant is replaced by what opened in its place`() {
        assertFalse(list.contains("Refreshment Port"))
        assertTrue(list.contains("La Poutinerie"))
    }

    /** On the source list, but table service at lunch and dinner. */
    @Test
    fun `Sanaa is not labelled quick service`() {
        assertFalse(list.contains("Sanaa"))
    }

    @Test
    fun `table service is not quick service`() {
        listOf("Be Our Guest Restaurant", "Space 220 Restaurant", "Le Cellier Steakhouse", "Tiffins")
            .forEach { assertFalse("$it is table service", list.contains(it)) }
    }

    /** The list is Disney's; a Universal restaurant is never designated by it. */
    @Test
    fun `only Walt Disney World restaurants are designated`() {
        fun restaurant(name: String, park: Park) =
            ParkEntity(id = name, name = name, kind = EntityKind.RESTAURANT, park = park, status = OperatingStatus.OPERATING)
        assertTrue(QuickService.isQuickService(restaurant("Woody's Lunch Box", Park.HOLLYWOOD_STUDIOS)))
        assertTrue(QuickService.isQuickService(restaurant("Chicken Guy!", Park.DISNEY_SPRINGS)))
        assertFalse(QuickService.isQuickService(restaurant("Woody's Lunch Box", Park.ISLANDS_OF_ADVENTURE)))
    }
}
