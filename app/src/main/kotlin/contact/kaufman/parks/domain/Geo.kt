package contact.kaufman.parks.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distances between points on the ground.
 *
 * Everything here works on a sphere. Over the few miles that separate Orlando's parks that
 * is accurate to well under a metre, which is far finer than the GPS fix feeding it.
 */
object Geo {

    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun distanceMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Double {
        val dLat = Math.toRadians(toLatitude - fromLatitude)
        val dLon = Math.toRadians(toLongitude - fromLongitude)
        val lat1 = Math.toRadians(fromLatitude)
        val lat2 = Math.toRadians(toLatitude)
        // Haversine rather than the law of cosines: it keeps its precision for the short
        // hops between neighbouring rides, where the cosine form loses digits.
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a).coerceIn(0.0, 1.0))
    }

    /**
     * Which park a position is in, or null when it is none of them.
     *
     * Answered from real boundaries — see [ParkBoundaries]. This used to compare against
     * park **centres** inside a 1,200m radius, which sounded reasonable and was not:
     * measured against the mapped footprints it put only 80.7% of Universal Studios in the
     * right park, and reported 100% of CityWalk as being inside a park. Universal Studios
     * and Islands of Adventure share a wall, so no arrangement of centres can separate
     * them, and CityWalk sits between the two with no centre of its own to lose to.
     *
     * Null now means "not in a park" and includes CityWalk, which is a real answer rather
     * than a gap. Use `ParkBoundaries.at` if you need to tell those two cases apart.
     */
    fun parkAt(latitude: Double, longitude: Double): Park? =
        ParkBoundaries.parkAt(latitude, longitude)

    /**
     * Is a position inside a polygon?
     *
     * [ring] is a flat `lat, lon, lat, lon, …` array, implicitly closed — the last point
     * joins back to the first. Flat rather than a list of points because these are shipped
     * constants and there are a few hundred of them; a `List<Pair<Double, Double>>` would
     * box every single coordinate for no gain.
     *
     * Ray casting, counting crossings of a ray heading east. Over a car park this can treat
     * latitude and longitude as a flat grid: the shapes are a few hundred metres across and
     * nowhere near a pole or the dateline, so the error is far below the GPS fix feeding it.
     */
    fun ringContains(ring: DoubleArray, latitude: Double, longitude: Double): Boolean {
        var inside = false
        var j = ring.size - 2
        for (i in ring.indices step 2) {
            val lat1 = ring[i]
            val lon1 = ring[i + 1]
            val lat2 = ring[j]
            val lon2 = ring[j + 1]
            // Only edges that straddle the ray's latitude can cross it. The half-open
            // comparison is what stops a vertex exactly on the ray counting twice.
            if ((lat1 > latitude) != (lat2 > latitude)) {
                val crossingLon = (lon2 - lon1) * (latitude - lat1) / (lat2 - lat1) + lon1
                if (longitude < crossingLon) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Metres from a position to the nearest edge of [ring], zero-ish when it sits on one. */
    fun distanceToRingMeters(ring: DoubleArray, latitude: Double, longitude: Double): Double {
        // One local metres-per-degree scale for the whole ring. These are small shapes, so
        // a single scale factor is plenty and keeps this to arithmetic.
        val lonScale = METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(latitude))
        val x = longitude * lonScale
        val y = latitude * METERS_PER_DEGREE_LATITUDE
        var best = Double.MAX_VALUE
        var j = ring.size - 2
        for (i in ring.indices step 2) {
            val ax = ring[i + 1] * lonScale
            val ay = ring[i] * METERS_PER_DEGREE_LATITUDE
            val bx = ring[j + 1] * lonScale
            val by = ring[j] * METERS_PER_DEGREE_LATITUDE
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            // Clamped projection onto the segment, so the nearest point is never off its end.
            val t = if (lengthSquared == 0.0) 0.0 else
                (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val distance = hypot(x - (ax + t * dx), y - (ay + t * dy))
            if (distance < best) best = distance
            j = i
        }
        return best
    }

    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

    /**
     * The midpoint of a resort's parks.
     *
     * Computed rather than hardcoded so it follows the park coordinates if one is ever
     * corrected. Used as the single point a resort's weather is read from — see
     * `ParksRepository.weather`.
     */
    fun resortCenter(resort: Resort): Pair<Double, Double> {
        val parks = Park.entries.filter { it.resort == resort }
        return parks.map { it.latitude }.average() to parks.map { it.longitude }.average()
    }
}

/** Metres from [latitude]/[longitude] to this entity, or null if it has no location. */
fun ParkEntity.distanceMetersFrom(latitude: Double, longitude: Double): Double? {
    val lat = this.latitude ?: return null
    val lon = this.longitude ?: return null
    return Geo.distanceMeters(latitude, longitude, lat, lon)
}

/**
 * Which land this entity stands in, or null when it is in none of them.
 *
 * Null is common and expected — 7% of rides across the resorts, and most of Animal
 * Kingdom's Discovery Island. It means "no land", never "unknown park".
 */
fun ParkEntity.land(): String? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return ParkLands.landAt(park, lat, lon)
}
