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
