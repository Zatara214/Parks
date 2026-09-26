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
}
