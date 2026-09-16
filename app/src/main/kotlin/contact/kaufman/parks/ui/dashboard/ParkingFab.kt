package contact.kaufman.parks.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

/**
 * Record the car, or open the spot you already recorded.
 *
 * **Icon only, in both states.** An earlier version labelled itself — "Record parking", or
 * the spot once there was one — which put the answer in the bottom-right corner of the
 * screen, floating over a park card. The spot belongs in the title bar where the eye
 * already goes for "what is going on today"; see [DashboardTopBar]. That leaves this as
 * purely the action, and an action with one obvious icon does not need a word next to it.
 */
@Composable
fun ParkingFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Icon(Icons.Default.DirectionsCar, contentDescription = "Parking")
    }
}
