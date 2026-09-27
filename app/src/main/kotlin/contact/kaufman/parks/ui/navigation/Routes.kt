package contact.kaufman.parks.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 keys. Serializable so the back stack survives process death. */
@Serializable
data object DashboardKey : NavKey

@Serializable
data class ParkDetailKey(val parkId: String) : NavKey

@Serializable
data class ParkingKey(val parkId: String?) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object TripsKey : NavKey

@Serializable
data object CalendarKey : NavKey

/**
 * One restaurant's menu. The name travels with the id because it is what the app works
 * Disney's menu address out from — see `MenuSlugs`.
 */
@Serializable
data class MenuKey(val restaurantId: String, val name: String) : NavKey
