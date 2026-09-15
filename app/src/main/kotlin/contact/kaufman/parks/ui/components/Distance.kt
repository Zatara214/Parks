package contact.kaufman.parks.ui.components

import kotlin.math.roundToInt

/**
 * How far away something is, for someone on foot in a theme park.
 *
 * Feet and miles, matching the US English used everywhere else in the app rather than
 * introducing a second unit setting next to the temperature one. Everything this is used
 * for is inside one park, where the honest answer is nearly always in feet.
 *
 * Rounded to 10 feet. The GPS fix underneath is good to 15-30 feet at best, so the last
 * digit is already noise — but rounding coarser than that makes a sorted list look wrong,
 * with several rides sharing one distance while sitting in an obvious order.
 */
fun formatWalkingDistance(meters: Double): String {
    val feet = meters * FEET_PER_METER
    return if (feet < FEET_PER_MILE / 5) {
        // At least "10 ft": a fix is never accurate enough to justify saying "3 ft", and
        // "0 ft" reads like a bug rather than like standing at the entrance.
        val rounded = (feet / 10).roundToInt() * 10
        "${rounded.coerceAtLeast(10)} ft"
    } else {
        val miles = feet / FEET_PER_MILE
        "${(miles * 10).roundToInt() / 10.0} mi"
    }
}

private const val FEET_PER_METER = 3.280839895
private const val FEET_PER_MILE = 5280.0
