package contact.kaufman.parks.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.ui.dashboard.DashboardScreen
import contact.kaufman.parks.ui.park.ParkDetailScreen
import contact.kaufman.parks.ui.parking.ParkingScreen
import contact.kaufman.parks.ui.settings.AboutScreen
import contact.kaufman.parks.ui.settings.SettingsScreen
import contact.kaufman.parks.ui.trips.TripsScreen

private const val DURATION = 320

@Composable
fun ParksNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(DashboardKey)

    fun push(key: NavKey) {
        // Navigation 3 will happily stack a second copy when a card is tapped twice
        // before the first push settles. Dropping a push onto the key already on top is
        // the cheap fix, and it is the whole of the bug.
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            slideInHorizontally(tween(DURATION)) { it / 6 } + fadeIn(tween(DURATION)) togetherWith
                fadeOut(tween(DURATION))
        },
        popTransitionSpec = {
            fadeIn(tween(DURATION)) togetherWith
                slideOutHorizontally(tween(DURATION)) { it / 6 } + fadeOut(tween(DURATION))
        },
        // The back *gesture* is driven by this spec, not popTransitionSpec. Leaving it
        // unset makes Navigation pick its own shrink-away preview and then play the plain
        // pop on commit, so one gesture visibly runs two unrelated animations.
        predictivePopTransitionSpec = {
            fadeIn(tween(DURATION)) togetherWith
                scaleOut(tween(DURATION), targetScale = 0.92f) + fadeOut(tween(DURATION))
        },
        entryProvider = entryProvider {
            entry<DashboardKey> {
                DashboardScreen(
                    onParkClick = { park -> push(ParkDetailKey(park.id)) },
                    onParkingClick = { push(ParkingKey(null)) },
                    onSettingsClick = { push(SettingsKey) },
                    onTripsClick = { push(TripsKey) },
                )
            }
            entry<ParkDetailKey> { key ->
                val park = Park.fromId(key.parkId)
                if (park == null) {
                    // An id that no longer resolves means the catalog moved on; drop the
                    // entry rather than showing a blank screen.
                    backStack.removeLastOrNull()
                } else {
                    ParkDetailScreen(
                        park = park,
                        onBack = { backStack.removeLastOrNull() },
                        onParkingClick = { push(ParkingKey(park.id)) },
                    )
                }
            }
            entry<ParkingKey> { key ->
                ParkingScreen(
                    initialPark = key.parkId?.let(Park::fromId),
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<TripsKey> {
                TripsScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<SettingsKey> {
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onAboutClick = { push(AboutKey) },
                )
            }
            entry<AboutKey> {
                AboutScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
