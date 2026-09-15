package contact.kaufman.parks.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
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
    weather: @Composable () -> Unit,
) {
    // Reactive: reading the clock inline left the date stuck on yesterday until the bar
    // happened to be rebuilt by navigation. See rememberParkToday.
    val today = rememberParkToday()

    LargeFlexibleTopAppBar(
        title = { Text("Today") },
        subtitle = {
            Column {
                Text(today.toParkDateHeading())
                weather()
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
