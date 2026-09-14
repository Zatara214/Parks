package contact.kaufman.parks.data.crowd

import contact.kaufman.parks.domain.Park
import kotlin.math.roundToInt

/**
 * Turns posted wait times into a 1-10 crowd level.
 *
 * This reproduces the method WDW Passport publishes, because it is the method that
 * actually works on public data: Disney and Universal never release attendance, so
 * posted waits are the only honest signal available.
 *
 *  1. Watch a curated set of *established* key attractions per park ([CrowdSeed]).
 *  2. Average each ride's posted standby wait across the midday window, skipping
 *     downtime and rides reporting no wait at all.
 *  3. Rank each *ride* 1-10 against what that ride normally does on this kind of day.
 *  4. Park level = the mean of its ride ranks.
 *
 * Step 4 is the part that matters. Averaging the *ranks* rather than the *waits* stops
 * one runaway headliner — a Rise of the Resistance having a bad morning — from dragging
 * the whole park up two levels on its own.
 */
object CrowdModel {

    /** The window WDW Passport samples. Waits before and after this are distorted by
     *  rope-drop surges and end-of-day queue cutoffs. */
    const val WINDOW_START_HOUR = 10
    const val WINDOW_END_HOUR = 17

    /** How many observed days it takes before recorded history replaces a seeded
     *  baseline for a ride. Low enough to start helping quickly, high enough that one
     *  freak day cannot redefine "normal". */
    const val MIN_OBSERVATIONS_TO_TRUST = 8

    /**
     * A ride's contribution to the park level.
     *
     * @param observedMinutes today's mean posted wait across the midday window
     * @param expectedMinutes what this ride normally posts on a day like today
     * @param fromRecordedHistory true once [expectedMinutes] came from what the app
     *   actually measured rather than from the shipped seed
     */
    data class AttractionCrowd(
        val attractionId: String,
        val name: String,
        val observedMinutes: Float,
        val expectedMinutes: Float,
        val level: Float,
        val fromRecordedHistory: Boolean,
    ) {
        /** >1 means busier than normal for this ride. */
        val ratio: Float get() = if (expectedMinutes <= 0f) 1f else observedMinutes / expectedMinutes
    }

    data class ParkCrowd(
        val park: Park,
        val level: Float,
        val attractions: List<AttractionCrowd>,
        /** Fraction of this park's level that rests on recorded rather than seeded
         *  baselines. Surfaced in the UI so a young reading can say so. */
        val confidence: Float,
    ) {
        val rounded: Int get() = level.roundToInt().coerceIn(1, 10)

        /** How today compares to a normal day, averaged over the watched rides. */
        val ratio: Float
            get() = if (attractions.isEmpty()) 1f else attractions.map { it.ratio }.average().toFloat()
    }

    /**
     * Maps observed/expected to a 1-10 level.
     *
     * The anchors are deliberately asymmetric. A park can be twice as busy as normal,
     * but it cannot be less than zero busy, so the low end compresses and the high end
     * has room to run. 1.0 lands on 5 — a normal day is a 5, by definition.
     */
    private val RATIO_ANCHORS = floatArrayOf(0.50f, 0.62f, 0.75f, 0.87f, 1.00f, 1.12f, 1.26f, 1.42f, 1.62f, 1.90f)

    fun levelForRatio(ratio: Float): Float {
        if (ratio <= RATIO_ANCHORS.first()) return 1f
        if (ratio >= RATIO_ANCHORS.last()) return 10f
        for (i in 0 until RATIO_ANCHORS.size - 1) {
            val lo = RATIO_ANCHORS[i]
            val hi = RATIO_ANCHORS[i + 1]
            if (ratio in lo..hi) {
                val t = (ratio - lo) / (hi - lo)
                return (i + 1) + t
            }
        }
        return 5f
    }

    /**
     * @param observedByAttraction today's midday mean wait per attraction id. Rides the
     *   park never opened, or that posted no wait all day, must simply be absent.
     * @param recordedBaselines observed-history baselines keyed by attraction id, from
     *   [CrowdBaselines]. Anything missing falls back to the shipped seed.
     */
    fun parkCrowd(
        park: Park,
        observedByAttraction: Map<String, Float>,
        recordedBaselines: Map<String, Float> = emptyMap(),
        month: Int,
        dayOfWeek: Int,
    ): ParkCrowd? {
        val seasonality = CrowdSeed.monthFactor(month) * CrowdSeed.dayOfWeekFactor(dayOfWeek)

        val attractions = CrowdSeed.keyAttractions(park).mapNotNull { key ->
            val observed = observedByAttraction[key.id] ?: return@mapNotNull null
            val recorded = recordedBaselines[key.id]
            // A recorded baseline is already measured on real days, so it carries its own
            // seasonality — applying the curve again would double-count it.
            val expected = recorded ?: (key.baselineMinutes * seasonality)
            if (expected <= 0f) return@mapNotNull null

            AttractionCrowd(
                attractionId = key.id,
                name = key.name,
                observedMinutes = observed,
                expectedMinutes = expected,
                level = levelForRatio(observed / expected),
                fromRecordedHistory = recorded != null,
            )
        }

        // One ride is noise, not a crowd level. Three is the floor worth publishing.
        if (attractions.size < 3) return null

        return ParkCrowd(
            park = park,
            level = attractions.map { it.level }.average().toFloat(),
            attractions = attractions,
            confidence = attractions.count { it.fromRecordedHistory }.toFloat() / attractions.size,
        )
    }

    /** Plain-language label for a level, used on the dashboard pill. */
    fun describe(level: Int): String = when (level.coerceIn(1, 10)) {
        1 -> "Ghost town"
        2 -> "Very light"
        3 -> "Light"
        4 -> "Below average"
        5 -> "Average"
        6 -> "Above average"
        7 -> "Busy"
        8 -> "Very busy"
        9 -> "Packed"
        else -> "Avoid"
    }

    /** The "vs. usual" line — the thing a seeded baseline buys you on day one. */
    fun describeVersusUsual(ratio: Float): String = when {
        ratio < 0.75f -> "Much quieter than usual"
        ratio < 0.92f -> "Quieter than usual"
        ratio <= 1.08f -> "About usual"
        ratio <= 1.25f -> "Busier than usual"
        else -> "Much busier than usual"
    }
}
