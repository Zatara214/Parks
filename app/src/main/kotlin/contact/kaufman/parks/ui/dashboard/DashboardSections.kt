package contact.kaufman.parks.ui.dashboard

import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkSnapshot
import contact.kaufman.parks.domain.Resort

/** One headed group of park cards on the dashboard. */
data class DashboardSection(
    val key: String,
    val title: String,
    val snapshots: List<ParkSnapshot>,
    val isHere: Boolean = false,
)

/**
 * Orders the dashboard.
 *
 * The park you are standing in is lifted into its own section at the top — if you are at
 * EPCOT, EPCOT is what you opened the app to look at — and then **removed from its resort
 * group**, because listing it twice would just read as a bug. A resort left with nothing
 * drops its header rather than showing an empty heading.
 */
fun dashboardSections(
    snapshots: List<ParkSnapshot>,
    youAreHere: Park?,
): List<DashboardSection> = buildList {
    val here = snapshots.firstOrNull { it.park == youAreHere }
    if (here != null) {
        add(
            DashboardSection(
                key = "here",
                title = "Where you are",
                snapshots = listOf(here),
                isHere = true,
            )
        )
    }

    Resort.entries.forEach { resort ->
        val rest = snapshots.filter { it.park.resort == resort && it.park != youAreHere }
        if (rest.isNotEmpty()) {
            add(DashboardSection(key = resort.name, title = resort.displayName, snapshots = rest))
        }
    }
}
