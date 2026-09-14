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
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val parking by viewModel.activeParking.collectAsStateWithLifecycle()
    val visibleParks by viewModel.visibleParks.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DashboardTopBar(
                scrollBehavior = scrollBehavior,
                onSettingsClick = onSettingsClick,
                weather = { ResortWeatherStrip(state.resortWeather, temperatureUnit) },
            )
        },
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
                    parking = parking,
                    onParkClick = onParkClick,
                    onParkingClick = onParkingClick,
                )
            }
        }
    }
}

@Composable
private fun ParkList(
    snapshots: List<ParkSnapshot>,
    parking: ParkingRecordEntity?,
    onParkClick: (Park) -> Unit,
    onParkingClick: () -> Unit,
) {
    val byResort = snapshots.groupBy { it.park.resort }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "parking") {
            ParkingPin(parking, onParkingClick, Modifier.animateItem())
        }

        byResort.forEach { (resort, parks) ->
            item(key = "header-${resort.name}") {
                Text(
                    text = resort.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(parks, key = { it.park.name }) { snapshot ->
                ParkCard(
                    snapshot = snapshot,
                    onClick = { onParkClick(snapshot.park) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}
