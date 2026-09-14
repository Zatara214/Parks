package contact.kaufman.parks.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import contact.kaufman.parks.domain.ParkSnapshot
import contact.kaufman.parks.ui.components.CrowdPill
import contact.kaufman.parks.ui.components.formatHoursRange
import contact.kaufman.parks.ui.theme.waitColor

/**
 * One park on the dashboard: hours, how busy it is, and the worst wait right now.
 *
 * Everything below the title is allowed to be missing — a park that has not opened yet
 * has no crowd level and no waits, and that is a normal state, not an error.
 */
@Composable
fun ParkCard(
    snapshot: ParkSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = snapshot.park.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = snapshot.park.resort.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                snapshot.crowd?.let { CrowdPill(it) }
            }

            val hours = snapshot.todayRegularHours
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (hours != null) {
                        formatHoursRange(hours.opening, hours.closing)
                    } else {
                        "Closed today"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Hard-ticket nights are the single most confusing thing about park hours —
            // the park "closes" at 6 and is then open until midnight for a separate
            // ticket. Calling it out prevents a wasted drive.
            snapshot.hours.filter { it.isTicketedEvent }.forEach { event ->
                Text(
                    text = "${event.description ?: "Ticketed event"} · ${formatHoursRange(event.opening, event.closing)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            AnimatedVisibility(
                visible = snapshot.crowd != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                snapshot.crowd?.let { crowd ->
                    Text(
                        text = buildString {
                            append(crowd.versusUsual)
                            when {
                                // Says plainly that the day hasn't been watched yet,
                                // rather than implying a full-day measurement.
                                crowd.isProvisional -> append(" · so far today")
                                crowd.confidence < 1f -> append(" · estimated")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val longest = snapshot.longestWait
            val median = snapshot.medianWait
            if (longest != null && median != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    WaitStat("Longest", longest.standbyMinutes!!, longest.name, Modifier.weight(1f))
                    WaitStat("Typical", median, "${snapshot.openAttractions.size} rides open", Modifier.weight(1f))
                }
            }

            snapshot.error?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaitStat(label: String, minutes: Int, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$minutes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = waitColor(minutes),
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = "min",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
