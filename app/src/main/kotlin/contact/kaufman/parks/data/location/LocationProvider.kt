package contact.kaufman.parks.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * A single location fix, on request.
 *
 * Uses the platform `LocationManager` rather than Play Services' fused client: it needs no
 * Google dependency, and the whole point of this app is to not be the kind of software
 * that quietly phones home.
 *
 * There is no continuous updates API here on purpose. Nothing in Parks needs to follow you
 * around — it needs to answer "which park is this?" when you open the app, and then stop.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean = PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The current position, or null when permission is missing, location is switched off,
     * or no provider can produce a fix.
     *
     * Returning null rather than throwing is deliberate: every caller's fallback is simply
     * "carry on without it", and location is a convenience here, never a requirement.
     */
    /**
     * The current position, or null when permission is missing, location is switched off,
     * no provider answers, or nothing recent enough to trust is available.
     *
     * Providers are tried in order and the **first sufficiently fresh** fix wins. Taking
     * the first fix offered is not good enough: `getCurrentLocation` is allowed to hand
     * back a cached one, and a location from hours ago is exactly the case that matters —
     * drive from home to EPCOT, open the app, and a cached home fix would confidently
     * report the wrong park, or none. Falling through to a slower provider for a current
     * answer is the right trade.
     *
     * Returning null rather than throwing is deliberate: every caller's fallback is simply
     * "carry on without it", and location is a convenience here, never a requirement.
     */
    suspend fun current(): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService<LocationManager>() ?: return null

        for (provider in PROVIDER_PREFERENCE) {
            val enabled = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            if (!enabled) continue

            val fix = requestFix(manager, provider) ?: continue
            val ageMillis = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
            if (ageMillis <= MAX_FIX_AGE_MILLIS) return fix
            Log.d(TAG, "discarding $provider fix ${ageMillis / 1000}s old")
        }
        return null
    }

    private suspend fun requestFix(manager: LocationManager, provider: String): Location? =
        // getCurrentLocation is documented to always call back, but "eventually" is not a
        // guarantee worth betting a coroutine on: a cold GPS indoors simply never answers.
        // Without this the caller waits forever and the feature looks dead rather than
        // unavailable.
        runCatching {
            withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(
                        provider,
                        signal,
                        context.mainExecutor,
                    ) { location -> continuation.resume(location) }
                }
            }
        }.getOrNull()

    private companion object {
        const val TAG = "LocationProvider"

        /** Long enough for a warm fix, short enough that nothing waits on a dead provider.
         *  Applied per provider, so a full fall-through is bounded by this times three. */
        const val FIX_TIMEOUT_MILLIS = 5_000L

        /** Old enough to cover standing still in a queue, young enough that it cannot be
         *  from a different park — or a different city. */
        const val MAX_FIX_AGE_MILLIS = 5 * 60 * 1_000L

        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        /** Fused first — it is the accurate one indoors and in a queue surrounded by
         *  steelwork — then the raw providers as a fallback. */
        val PROVIDER_PREFERENCE = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
    }
}
