package contact.kaufman.parks.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import contact.kaufman.parks.domain.Resort

/**
 * Hand-off to the resorts' own apps.
 *
 * Parks deliberately does not attempt anything that needs a real account — mobile order,
 * ticket scanning, virtual queues, dining reservations. Those need a logged-in session
 * against Disney's or Universal's own systems, and reimplementing them would mean asking
 * for credentials this app has no business holding.
 *
 * So the rule is: read here, act there.
 *
 * Only the launcher entry point is used. Both apps almost certainly have internal deep
 * links to specific screens, but none are documented, and an undocumented scheme that
 * silently stops working is worse than one extra tap.
 */
object OfficialApps {

    /** Verified against the Play Store listings, 2026-09-14. */
    fun packageName(resort: Resort): String = when (resort) {
        Resort.WALT_DISNEY_WORLD -> "com.disney.wdw.android"
        Resort.UNIVERSAL_ORLANDO -> "com.universalstudios.orlandoresort"
    }

    fun appName(resort: Resort): String = when (resort) {
        Resort.WALT_DISNEY_WORLD -> "My Disney Experience"
        Resort.UNIVERSAL_ORLANDO -> "Universal Orlando"
    }

    fun isInstalled(context: Context, resort: Resort): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName(resort)) != null

    /**
     * Opens the official app, or its Play Store listing when it is not installed.
     *
     * The manifest declares both packages under `<queries>`; without that, Android 11+
     * reports every app as missing and this would always fall through to the store.
     */
    fun open(context: Context, resort: Resort) {
        val pkg = packageName(resort)
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }

        val store = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(store) }.onFailure {
            // No Play Store (or no browser) is possible on a stripped device; say so
            // rather than failing silently on a tap.
            val web: Uri = "https://play.google.com/store/apps/details?id=$pkg".toUri()
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                Toast.makeText(context, "${appName(resort)} isn't installed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

