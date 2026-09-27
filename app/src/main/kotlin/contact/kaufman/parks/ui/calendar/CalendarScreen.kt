package contact.kaufman.parks.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import contact.kaufman.parks.data.prefs.TemperatureUnit
import contact.kaufman.parks.domain.CalendarDay
import contact.kaufman.parks.domain.DayOutlook
import contact.kaufman.parks.domain.ParkDay
import contact.kaufman.parks.domain.ParkDayStatus
import contact.kaufman.parks.ui.components.formatHoursRange
import contact.kaufman.parks.ui.components.formatTemperature
import contact.kaufman.parks.ui.components.toCalendarHeading
import contact.kaufman.parks.ui.components.weatherIcon

/**
 * The next ten days: each park's hours and extra sessions, with the day's weather beside
 * them. A place to plan the week rather than to check right now — the dashboard does that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.resorts.size > 1) {
                    item(key = "resorts") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.resorts.forEach { resort ->
                                FilterChip(
                                    selected = resort == state.resort,
                                    onClick = { viewModel.selectResort(resort) },
                                    label = { Text(resort.displayName) },
                                )
                            }
                        }
                    }
                }

                if (state.isLoading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                } else {
                    val today = state.today
                    items(state.days, key = { it.date.toString() }) { day ->
                        DayCard(
                            day = day,
                            heading = today?.let { day.date.toCalendarHeading(it) } ?: day.date.toString(),
                            unit = unit,
                        )
                    }
                    item(key = "sources") {
                        Text(
                            text = "Hours from themeparks.wiki, as each park publishes them. Weather from " +
                                "Open-Meteo — past about a week it is an outlook, not a forecast.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCard(day: CalendarDay, heading: String, unit: TemperatureUnit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                day.weather?.let { WeatherSummary(it, unit) }
            }
            day.parks.forEach { ParkLine(it) }
        }
    }
}

/** High / low, the day's shape, and the rain chance — the three numbers that decide a day. */
@Composable
private fun WeatherSummary(outlook: DayOutlook, unit: TemperatureUnit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = weatherIcon(outlook.weatherCode),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${formatTemperature(outlook.highF, unit)} / ${formatTemperature(outlook.lowF, unit)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        outlook.rainChancePercent?.let { chance ->
            Icon(
                imageVector = Icons.Default.WaterDrop,
                contentDescription = "Chance of rain",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("$chance%", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ParkLine(day: ParkDay) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = day.park.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Text(
                text = when (day.status) {
                    ParkDayStatus.OPEN -> formatHoursRange(day.hours?.opening, day.hours?.closing)
                    ParkDayStatus.CLOSED -> "Closed"
                    // Not "Closed": the park simply has not published this far ahead yet.
                    ParkDayStatus.NOT_YET_PUBLISHED -> "Not published yet"
                    ParkDayStatus.UNAVAILABLE -> "Couldn't load"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (day.status == ParkDayStatus.OPEN) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        day.extras.forEach { extra ->
            Text(
                text = "${extra.label} · ${formatHoursRange(extra.opening, extra.closing)}",
                style = MaterialTheme.typography.labelMedium,
                // Ticketed nights stand out: they are the ones a regular ticket or an annual
                // pass does not get you into, and the ones that close a park early.
                color = if (extra.isTicketed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
