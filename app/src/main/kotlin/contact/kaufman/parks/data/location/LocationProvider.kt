package contact.kaufman.parks.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
    suspend fun current(): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService<LocationManager>() ?: return null

        val provider = PROVIDER_PREFERENCE.firstOrNull { candidate ->
            runCatching { manager.isProviderEnabled(candidate) }.getOrDefault(false)
        } ?: return null

        // getCurrentLocation is documented to always call back, but "eventually" is not a
        // guarantee worth betting a coroutine on: a cold GPS indoors, or an emulator whose
        // location backend has wedged, simply never answers. Without this the caller waits
        // forever and the feature looks dead rather than unavailable.
        return runCatching {
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
    }

    private companion object {
        /** Long enough for a warm fix, short enough that nothing waits on a dead provider. */
        const val FIX_TIMEOUT_MILLIS = 8_000L

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
