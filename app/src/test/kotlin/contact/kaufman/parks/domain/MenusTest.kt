package contact.kaufman.parks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenusTest {

    @Test
    fun `one price reads as dollars and cents`() {
        assertEquals("$14.99", Menus.priceLabel(listOf(14.99)))
        assertEquals("$5.00", Menus.priceLabel(listOf(5.0)))
    }

    @Test
    fun `sizes read as a range, low to high`() {
        assertEquals("$4.29 – $6.49", Menus.priceLabel(listOf(6.49, 4.29)))
    }

    /** No price is a blank, never a claim that something is free. */
    @Test
    fun `no price is null, not zero`() {
        assertNull(Menus.priceLabel(emptyList()))
        assertNull(Menus.priceLabel(listOf(Double.NaN)))
    }

    @Test
    fun `descriptions lose their HTML`() {
        assertEquals(
            "Truffle aioli & French onion jam on a brioche bun",
            Menus.cleanDescription("<p>Truffle aioli &amp; French onion jam<br/>on a brioche bun</p>"),
        )
        assertEquals("Café", Menus.cleanDescription("Caf&#233;"))
    }

    @Test
    fun `an empty description is null, so no blank line is drawn`() {
        assertNull(Menus.cleanDescription("<p> </p>"))
        assertNull(Menus.cleanDescription(null))
    }

    @Test
    fun `the menu opens on the meal for the hour`() {
        val periods = listOf("Breakfast", "Lunch And Dinner", "Special Ticketed Event")
        assertEquals(0, Menus.defaultPeriodIndex(periods, hour = 8))
        assertEquals(1, Menus.defaultPeriodIndex(periods, hour = 12))
        // "Lunch And Dinner" is also the dinner menu.
        assertEquals(1, Menus.defaultPeriodIndex(periods, hour = 19))
    }

    @Test
    fun `a meal it cannot place opens on the first period`() {
        assertEquals(0, Menus.defaultPeriodIndex(listOf("Snacks", "Beverages"), hour = 12))
        assertEquals(0, Menus.defaultPeriodIndex(listOf("Lunch And Dinner"), hour = 7))
    }
}
