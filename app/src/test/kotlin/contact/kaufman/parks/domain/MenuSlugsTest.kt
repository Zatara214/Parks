package contact.kaufman.parks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Names to Disney's menu addresses. Every pair here is real: the name as Disney's endpoint
 * returns it, and the slug that endpoint answered to, read 2026-09-26.
 */
class MenuSlugsTest {

    private fun assertFinds(name: String, slug: String) =
        assertTrue("$name should try $slug, tried ${MenuSlugs.candidates(name)}", slug in MenuSlugs.candidates(name))

    @Test
    fun `the plain case is the first guess`() {
        assertEquals("pecos-bill-tall-tale-inn-and-cafe",
            MenuSlugs.candidates("Pecos Bill Tall Tale Inn and Cafe").first())
    }

    @Test
    fun `a possessive can go either way`() {
        assertFinds("Cosmic Ray's Starlight Café", "cosmic-ray-starlight-cafe")   // drops the 's
        assertFinds("Casey's Corner", "caseys-corner")                            // keeps the s
    }

    @Test
    fun `accents and curly apostrophes are flattened`() {
        assertFinds("Satu’li Canteen", "satuli-canteen")
        assertFinds("Cosmic Ray’s Starlight Café", "cosmic-ray-starlight-cafe")
    }

    @Test
    fun `a leading article is dropped when Disney drops it`() {
        assertFinds("The Crystal Palace", "crystal-palace")
        assertFinds("La Cantina de San Angel", "cantina-de-san-angel")
    }

    @Test
    fun `an ampersand is tried both dropped and spelled out`() {
        assertFinds("Block & Hans", "block-hans")
    }

    /**
     * The irregular ones, which no rule reaches. Woody's Lunch Box is the reason the table
     * exists: a quick-service staple whose slug spells "lunchbox" as one word.
     */
    @Test
    fun `irregular slugs come from the override table`() {
        assertEquals(listOf("woodys-lunchbox"), MenuSlugs.candidates("Woody's Lunch Box"))
        assertEquals(listOf("boardwalk-bakery"), MenuSlugs.candidates("BoardWalk Deli"))
        assertEquals(listOf("yak-and-yeti-local-foods-cafe"), MenuSlugs.candidates("Yak & Yeti™ Local Food Cafes"))
    }

    /** themeparks.wiki and Disney may not dress a name identically; the table must not care. */
    @Test
    fun `the override lookup ignores trademark marks and apostrophe style`() {
        assertEquals(listOf("yak-and-yeti-local-foods-cafe"), MenuSlugs.candidates("Yak & Yeti Local Food Cafes"))
        assertEquals(listOf("woodys-lunchbox"), MenuSlugs.candidates("Woody’s Lunch Box"))
    }

    /** Every guess is a request, made while someone waits at the counter. */
    @Test
    fun `never more than four guesses`() {
        listOf("The Plaza Restaurant", "Maria & Enzo's Ristorante", "Les Halles Boulangerie-Patisserie")
            .forEach { assertTrue(MenuSlugs.candidates(it).size <= 4) }
    }

    /** ™ has to go before normalising, or NFKD turns it into the letters "tm". */
    @Test
    fun `a trademark sign never leaks into a slug as letters`() {
        assertTrue(MenuSlugs.candidates("Joffrey's™ Coffee").none { "tm" in it.split('-') || it.contains("coffeetm") })
    }
}
