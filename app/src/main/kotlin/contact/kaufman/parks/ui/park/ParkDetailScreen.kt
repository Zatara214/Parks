package contact.kaufman.parks.ui.park

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.Resort
import contact.kaufman.parks.ui.components.OfficialApps
import contact.kaufman.parks.ui.components.CrowdPill
import contact.kaufman.parks.ui.components.formatHoursRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkDetailScreen(
    park: Park,
    onBack: () -> Unit,
    onParkingClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ParkDetailViewModel = hiltViewModel<ParkDetailViewModel, ParkDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(park) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(park.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { OfficialApps.open(context, park.resort) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open ${OfficialApps.appName(park.resort)}",
                        )
                    }
                    IconButton(onClick = onParkingClick) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = "Parking")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@PullToRefreshBox
            }

            val entities = state.visibleEntities()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "summary") { ParkSummary(state) }

                item(key = "weather") {
                    WeatherCard(
                        weather = state.weather,
                        isLoading = state.isLoadingWeather,
                        unit = temperatureUnit,
                        onLoad = viewModel::loadWeather,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                item(key = "tabs") {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ParkTab.entries.forEach { tab ->
                            FilterChip(
                                selected = state.tab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }

                item(key = "filters") {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.hideClosed,
                            onClick = viewModel::toggleHideClosed,
                            label = { Text("Open only") },
                        )
                        if (state.tab == ParkTab.RIDES) {
                            RideSort.entries.forEach { sort ->
                                FilterChip(
                                    selected = state.sort == sort,
                                    onClick = { viewModel.selectSort(sort) },
                                    label = { Text(sort.label) },
                                )
                            }
                        }
                    }
                }

                if (state.tab == ParkTab.DINING) {
                    item(key = "dining-handoff") {
                        HandOffCard(
                            resort = park.resort,
                            onOpen = { OfficialApps.open(context, park.resort) },
                        )
                    }
                }

                if (entities.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = emptyMessage(state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                } else {
                    items(entities, key = { it.id }) { entity ->
                        RideRow(
                            entity = entity,
                            modifier = Modifier.animateItem(),
                            loadHistory = viewModel::waitHistory,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParkSummary(state: ParkDetailUiState) {
    val snapshot = state.snapshot ?: return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            val hours = snapshot.todayRegularHours
            Text(
                text = if (hours != null) formatHoursRange(hours.opening, hours.closing) else "Closed today",
                style = MaterialTheme.typography.titleMedium,
            )
            snapshot.hours.filter { it.isTicketedEvent }.forEach { event ->
                Text(
                    text = "${event.description ?: "Ticketed event"} · ${formatHoursRange(event.opening, event.closing)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            snapshot.crowd?.let { crowd ->
                Text(
                    text = buildString {
                        append(crowd.versusUsual)
                        if (crowd.isProvisional) append(" · so far today")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        snapshot.crowd?.let { CrowdPill(it, showLabel = false) }
    }
}

/** Distinguishes "nothing is open" from "this park has no dining data", which for
 *  Universal is a permanent upstream gap rather than a temporary state. */
private fun emptyMessage(state: ParkDetailUiState): String = when {
    state.tab == ParkTab.DINING && state.snapshot?.park?.resort?.name?.startsWith("UNIVERSAL") == true ->
        "Universal doesn't publish live dining status."
    state.hideClosed -> "Nothing open right now. Turn off \"Open only\" to see everything."
    else -> "No data for this park yet."
}

/**
 * Says plainly that ordering happens in the resort's own app, and gets you there.
 *
 * Mobile order needs a logged-in session against Disney's or Universal's systems; Parks
 * has no business holding those credentials. Naming the limit is better than leaving
 * someone hunting for a button that was never going to exist.
 */
@Composable
private fun HandOffCard(resort: Resort, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Ordering and reservations",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Mobile order and dining reservations need your ${OfficialApps.appName(resort)} account.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = onOpen) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Open ${OfficialApps.appName(resort)}")
            }
        }
    }
}
