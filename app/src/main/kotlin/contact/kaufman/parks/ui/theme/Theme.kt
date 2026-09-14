package contact.kaufman.parks.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import contact.kaufman.parks.data.prefs.ThemeMode

private val LightScheme = lightColorScheme(
    primary = ParksBlue,
    secondary = ParksGold,
    tertiary = ParksCoral,
)

private val DarkScheme = darkColorScheme(
    primary = ParksGold,
    secondary = ParksCoral,
    tertiary = ParksBlue,
)

@Composable
fun ParksTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** minSdk is 31, so dynamic color is always available — the flag exists for the
     *  settings toggle and for screenshot tests that need a stable palette. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        // The expressive scheme is the spring-based one: overshoot on entry, settle on
        // exit. It is the difference between the app feeling alive and feeling like a
        // web page, and it is the reason this project runs the alpha Compose line.
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
