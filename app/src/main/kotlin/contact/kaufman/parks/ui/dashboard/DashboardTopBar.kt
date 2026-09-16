package contact.kaufman.parks.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.ui.components.rememberParkToday
import contact.kaufman.parks.ui.components.toParkDateHeading

/**
 * Uses the expressive `LargeFlexibleTopAppBar` rather than the classic large bar: it
 * carries a subtitle slot, which is exactly where today's date belongs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onSettingsClick: () -> Unit,
    onTripsClick: () -> Unit,
    parking: ParkingRecordEntity?,
    weather: @Composable () -> Unit,
) {
    // Reactive: reading the clock inline left the date stuck on yesterday until the bar
    // happened to be rebuilt by navigation. See rememberParkToday.
    val today = rememberParkToday()

    LargeFlexibleTopAppBar(
        title = {
            // Right-aligned so a long spot never pushes "Today" around, and so the two
            // read as separate facts rather than one run-on line.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today")
                Spacer(Modifier.weight(1f))
                parking?.let { ParkedSpotLabel(it) }
            }
        },
        subtitle = {
            Column {
                Text(today.toParkDateHeading())
                weather()
            }
        },
        actions = {
            // Trip history is a feature rather than a setting, so it gets its own action
            // instead of being buried a level down next to the licence list.
            IconButton(onClick = onTripsClick) {
                Icon(Icons.Default.History, contentDescription = "Trips")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Where the car is, in the title bar.
 *
 * Zak asked for this after a night at Disney Springs: with a spot recorded, that is the
 * thing he wants visible without tapping anything, and the bottom-right corner is not
 * where anyone looks for it.
 *
 * Deliberately just the lot and the row. The park is already answered by the card pinned
 * under "Where you are", and repeating it here is what made this too long to right-align
 * in the first place.
 */
@Composable
private fun ParkedSpotLabel(record: ParkingRecordEntity) {
    val spot = listOf(record.lot, record.row).filter(String::isNotBlank).joinToString(" ")
    if (spot.isBlank()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.widthIn(max = 220.dp),
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = "Parked at",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = spot,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            // A free-text row can be as long as someone types. Truncating keeps "Today"
            // where it is instead of letting the label shove it off the left edge.
            overflow = TextOverflow.Ellipsis,
        )
    }
}
