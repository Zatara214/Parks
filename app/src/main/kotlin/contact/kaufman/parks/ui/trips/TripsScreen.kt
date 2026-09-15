package contact.kaufman.parks.ui.trips

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import contact.kaufman.parks.domain.ParkVisit
import contact.kaufman.parks.domain.TripDay
import contact.kaufman.parks.ui.components.toParkClockTime
import contact.kaufman.parks.ui.components.toParkDateHeading
import contact.kaufman.parks.ui.components.toTripLength

/**
 * Days you have been to a park, as far as the app can tell.
 *
 * Built only from fixes the app already took while you were using it, so it is a record of
 * where you were and roughly when — not of what you rode. The empty state says as much,
 * because a history screen that is simply blank looks broken rather than unused.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LoadingIndicator()
            }

            state.days.isEmpty() -> EmptyTrips(Modifier.padding(padding))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.days, key = { it.date.toString() }) { day ->
                    TripDayCard(day = day, onForget = { viewModel.forget(day) })
                }
            }
        }
    }
}

@Composable
private fun EmptyTrips(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No trips yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            // Says what fills it, because this screen cannot fill itself. Parks takes no
            // location in the background, so a day only appears if the app was open.
            text = "Days show up here once you've opened Parks while you're at a park. " +
                "Nothing is recorded in the background, so a visit you spent with your " +
                "phone in your pocket won't appear.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TripDayCard(day: TripDay, onForget: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = day.date.toParkDateHeading(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (day.isParkHop) {
                        Text(
                            text = "${day.visits.size} parks",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                TextButton(onClick = onForget) { Text("Forget") }
            }

            day.visits.forEachIndexed { index, visit ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                VisitRow(visit)
            }

            day.parking?.let { spot ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = listOf(spot.lot, spot.row).filter { it.isNotBlank() }.joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VisitRow(visit: ParkVisit) {
    Column {
        Text(text = visit.park.displayName, style = MaterialTheme.typography.bodyLarge)
        Text(
            // A span only when there is one worth showing. Two sightings a minute apart
            // describe a moment, not a duration, and "9:12 AM – 9:13 AM · 0m" would be a
            // worse answer than just the time.
            text = if (visit.hasMeaningfulDuration) {
                "${visit.firstSeen.toParkClockTime()} – ${visit.lastSeen.toParkClockTime()}" +
                    " · ${visit.duration.toTripLength()}"
            } else {
                "Seen at ${visit.firstSeen.toParkClockTime()}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
