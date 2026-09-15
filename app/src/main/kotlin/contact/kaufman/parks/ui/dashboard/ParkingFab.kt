package contact.kaufman.parks.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import contact.kaufman.parks.data.db.ParkingRecordEntity

/**
 * Record the car, or read back where it is.
 *
 * This replaced a full-width card pinned above the parks. The card had to occupy the top of
 * the list even when it had nothing to say — an empty "Record your parking spot" prompt
 * pushing every park down a notch, every day, for a thing done once a visit. A button is
 * the right shape for an action, and it costs no vertical space.
 *
 * It stays **extended in both states** rather than shrinking to an icon when empty. The
 * icon alone is a car, which could as easily mean directions or traffic; the word is what
 * makes it unambiguous, and this is not a screen with so many actions that one label is
 * clutter.
 *
 * When a spot is recorded the label becomes the spot itself, so the dashboard still answers
 * "where did I leave the car?" at a glance. The park name is dropped — it does not fit, and
 * it is the least surprising part of the answer when you are standing in the park. The
 * parking screen still shows it in full.
 */
@Composable
fun ParkingFab(record: ParkingRecordEntity?, onClick: () -> Unit) {
    val spot = record?.let { listOf(it.lot, it.row).filter(String::isNotBlank).joinToString(" ") }

    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
        text = {
            Text(
                // A record with neither lot nor row cannot be saved, so `spot` is only ever
                // blank for a row-only entry someone typed oddly — fall back rather than
                // render an empty button.
                text = spot?.takeIf { it.isNotBlank() } ?: "Record parking",
            )
        },
    )
}
