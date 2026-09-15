package contact.kaufman.parks.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.domain.ParkSnapshot

/**
 * The Today dashboard — every park at a glance, grouped by resort.
 *
 * This is the answer to "should I go, and where?", which is the question asked from the
 * couch in Celebration. Drilling into a park answers "what should I ride?".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onParkClick: (Park) -> Unit,
    onParkingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTripsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val parking by viewModel.activeParking.collectAsStateWithLifecycle()
    val visibleParks by viewModel.visibleParks.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()

    // Asked once, on first open. Granting it only ever saves a tap — every screen works
    // without it, so a refusal is final and never nagged about again.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) viewModel.detectPark()
    }
    LaunchedEffect(Unit) {
        if (!viewModel.hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
    // enterAlways, not exitUntilCollapsed. The latter only re-expands the bar once the
    // list is back at the very top — and at the top, PullToRefreshBox consumes the downward
    // drag to drive its indicator, so the app bar never sees the gesture that would grow it
    // back. The result was a large title that collapsed once on the first scroll and then
    // stayed collapsed for the rest of the session, including at the top of the list.
    // enterAlways expands on any upward scroll, so it never depends on reaching the top.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DashboardTopBar(
                scrollBehavior = scrollBehavior,
                onSettingsClick = onSettingsClick,
                onTripsClick = onTripsClick,
                weather = { ResortWeatherStrip(state.resortWeather, temperatureUnit) },
            )
        },
        floatingActionButton = { ParkingFab(parking, onParkingClick) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }

                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                else -> ParkList(
                    snapshots = state.snapshots.filter { it.park in visibleParks },
                    onParkClick = onParkClick,
                    youAreHere = state.youAreHere,
                )
            }
        }
    }
}

@Composable
private fun ParkList(
    snapshots: List<ParkSnapshot>,
    onParkClick: (Park) -> Unit,
    youAreHere: Park?,
) {
    val byResort = snapshots.groupBy { it.park.resort }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom for the last card to clear the floating button, which would
        // otherwise sit on top of whichever park happens to be listed last.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Ordering lives in dashboardSections() so the "don't list it twice" rule can be
        // tested; the card keeps its list key, so it animates up rather than blinking into
        // place when the fix lands.
        dashboardSections(snapshots, youAreHere).forEach { section ->
            item(key = "header-${section.key}") {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(section.snapshots, key = { it.park.name }) { snapshot ->
                ParkCard(
                    snapshot = snapshot,
                    onClick = { onParkClick(snapshot.park) },
                    modifier = Modifier.animateItem(),
                    youAreHere = section.isHere,
                )
            }
        }
    }
}
