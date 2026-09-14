package contact.kaufman.parks.ui.dashboard

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Uses the expressive `LargeFlexibleTopAppBar` rather than the classic large bar: it
 * carries a subtitle slot, which is exactly where today's date belongs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(scrollBehavior: TopAppBarScrollBehavior) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.of("America/New_York")).date

    LargeFlexibleTopAppBar(
        title = { Text("Today") },
        subtitle = {
            Text("${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.day}")
        },
        scrollBehavior = scrollBehavior,
    )
}
