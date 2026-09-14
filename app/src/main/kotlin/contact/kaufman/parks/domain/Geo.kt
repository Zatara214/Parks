package contact.kaufman.parks.domain

import kotlin.math.asin
import kotlin.math.cos
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
     * This compares against park **centres**, which is the crude part. Magic Kingdom and
     * EPCOT are miles apart and never confusable, but Universal Studios and Islands of
     * Adventure share a wall — their centres are about 500m apart, so a poor fix near the
     * boundary can land on the wrong one. Real polygons would settle it; see the
     * parking-geofence section of PLAN.md.
     */
    fun parkAt(
        latitude: Double,
        longitude: Double,
        withinMeters: Double = DEFAULT_PARK_RADIUS_METERS,
    ): Park? = Park.entries
        .map { it to distanceMeters(latitude, longitude, it.latitude, it.longitude) }
        .filter { (_, distance) -> distance <= withinMeters }
        .minByOrNull { (_, distance) -> distance }
        ?.first

    /** Generous enough to cover a whole park from its centre, tight enough that the
     *  parking lots and the interstate do not count as being in one. */
    const val DEFAULT_PARK_RADIUS_METERS = 1_200.0
}

/** Metres from [latitude]/[longitude] to this entity, or null if it has no location. */
fun ParkEntity.distanceMetersFrom(latitude: Double, longitude: Double): Double? {
    val lat = this.latitude ?: return null
    val lon = this.longitude ?: return null
    return Geo.distanceMeters(latitude, longitude, lat, lon)
}
