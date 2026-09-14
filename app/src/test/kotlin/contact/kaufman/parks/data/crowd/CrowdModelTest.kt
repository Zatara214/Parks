package contact.kaufman.parks.data.crowd

import contact.kaufman.parks.domain.Park
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrowdModelTest {

    /** A park posting exactly its seeded baseline is, by definition, a normal day. */
    @Test
    fun `baseline waits produce an average day`() {
        val month = 6
        val dayOfWeek = 1
        val seasonality = CrowdSeed.monthFactor(month) * CrowdSeed.dayOfWeekFactor(dayOfWeek)
        val observed = CrowdSeed.keyAttractions(Park.MAGIC_KINGDOM)
            .associate { it.id to it.baselineMinutes * seasonality }

        val crowd = CrowdModel.parkCrowd(Park.MAGIC_KINGDOM, observed, month = month, dayOfWeek = dayOfWeek)

        assertNotNull(crowd)
        assertEquals(5, crowd!!.rounded)
        assertEquals("Average", CrowdModel.describe(crowd.rounded))
    }

    @Test
    fun `doubling every wait pushes the park to the top of the scale`() {
        val month = 9
        val dayOfWeek = 3
        val seasonality = CrowdSeed.monthFactor(month) * CrowdSeed.dayOfWeekFactor(dayOfWeek)
        val observed = CrowdSeed.keyAttractions(Park.EPCOT)
            .associate { it.id to it.baselineMinutes * seasonality * 2f }

        val crowd = CrowdModel.parkCrowd(Park.EPCOT, observed, month = month, dayOfWeek = dayOfWeek)!!

        assertEquals(10, crowd.rounded)
        assertEquals("Much busier than usual", CrowdModel.describeVersusUsual(crowd.ratio))
    }

    /**
     * The reason the model averages ranks rather than waits. One headliner melting down
     * must not move the park more than a notch — otherwise every Rise of the Resistance
     * breakdown reads as a packed park.
     */
    @Test
    fun `one runaway headliner barely moves the park level`() {
        val park = Park.HOLLYWOOD_STUDIOS
        val month = 5
        val dayOfWeek = 2
        val seasonality = CrowdSeed.monthFactor(month) * CrowdSeed.dayOfWeekFactor(dayOfWeek)
        val keys = CrowdSeed.keyAttractions(park)

        val normal = keys.associate { it.id to it.baselineMinutes * seasonality }
        val withOutlier = normal.toMutableMap().apply {
            val headliner = keys.first()
            put(headliner.id, headliner.baselineMinutes * seasonality * 4f)
        }

        val base = CrowdModel.parkCrowd(park, normal, month = month, dayOfWeek = dayOfWeek)!!
        val spiked = CrowdModel.parkCrowd(park, withOutlier, month = month, dayOfWeek = dayOfWeek)!!

        val movement = spiked.level - base.level
        assertTrue("one ride moved the park by $movement levels", movement < 1.0f)
        assertTrue("the spike should still register", movement > 0f)
    }

    @Test
    fun `a recorded baseline overrides the seed and is marked as measured`() {
        val park = Park.ANIMAL_KINGDOM
        val key = CrowdSeed.keyAttractions(park).first()
        val observed = CrowdSeed.keyAttractions(park).associate { it.id to 30f }
        val recorded = mapOf(key.id to 30f) // measured normal == what we see today

        val crowd = CrowdModel.parkCrowd(park, observed, recorded, month = 1, dayOfWeek = 1)!!
        val entry = crowd.attractions.first { it.attractionId == key.id }

        assertTrue(entry.fromRecordedHistory)
        assertEquals(30f, entry.expectedMinutes, 0.01f)
        assertEquals(5f, entry.level, 0.01f)
        assertTrue(crowd.confidence > 0f && crowd.confidence < 1f)
    }

    /** Two open rides is not a crowd level, it is a rumour. */
    @Test
    fun `too few open rides yields no reading`() {
        val park = Park.EPIC_UNIVERSE
        val observed = CrowdSeed.keyAttractions(park).take(2).associate { it.id to 40f }

        assertNull(CrowdModel.parkCrowd(park, observed, month = 3, dayOfWeek = 6))
    }

    @Test
    fun `seasonality makes an identical wait mean different things in different months`() {
        val park = Park.MAGIC_KINGDOM
        val observed = CrowdSeed.keyAttractions(park).associate { it.id to it.baselineMinutes.toFloat() }

        // September is the quietest month, so the same posted waits mean a busier day.
        val september = CrowdModel.parkCrowd(park, observed, month = 9, dayOfWeek = 3)!!
        val july = CrowdModel.parkCrowd(park, observed, month = 7, dayOfWeek = 3)!!

        assertTrue(
            "identical waits should read busier in September than in July",
            september.level > july.level,
        )
    }

    @Test
    fun `level curve is monotonic and clamped`() {
        assertEquals(1f, CrowdModel.levelForRatio(0.1f), 0.001f)
        assertEquals(10f, CrowdModel.levelForRatio(5f), 0.001f)
        var previous = 0f
        var ratio = 0.4f
        while (ratio < 2.2f) {
            val level = CrowdModel.levelForRatio(ratio)
            assertTrue("level went backwards at ratio $ratio", level >= previous)
            previous = level
            ratio += 0.02f
        }
    }
}
