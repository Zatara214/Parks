package contact.kaufman.parks.ui.park

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import contact.kaufman.parks.domain.OperatingStatus
import contact.kaufman.parks.domain.ParkEntity
import contact.kaufman.parks.domain.Queue
import contact.kaufman.parks.ui.components.toParkClockTime
import contact.kaufman.parks.ui.theme.waitColor

/**
 * One ride. The posted standby wait is the whole point, so it gets the left-hand block
 * and everything else is secondary text underneath the name.
 */
@Composable
fun RideRow(entity: ParkEntity, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(entity.id) { mutableStateOf(false) }
    // Only Disney publishes a forecast, so at Universal the row is not a control at all
    // and must not pretend to be one.
    val canExpand = entity.forecast.count { it.waitMinutes != null } >= 2

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (canExpand) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WaitBadge(entity)

            Column(Modifier.weight(1f)) {
                Text(
                    text = entity.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = entity.secondaryLine()
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (canExpand) {
                val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide forecast" else "Show forecast",
                    modifier = Modifier.size(20.dp).rotate(turn),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && canExpand,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            RideForecast(entity)
        }
    }
}

@Composable
private fun WaitBadge(entity: ParkEntity) {
    val minutes = entity.standbyMinutes

    when {
        entity.status == OperatingStatus.DOWN -> StatusBadge("Down", MaterialTheme.colorScheme.error)
        entity.status == OperatingStatus.REFURBISHMENT -> StatusBadge("Refurb", MaterialTheme.colorScheme.tertiary)
        !entity.isOperating -> StatusBadge("Closed", MaterialTheme.colorScheme.outline)
        minutes == null -> StatusBadge("Open", MaterialTheme.colorScheme.secondary)
        else -> {
            val color = waitColor(minutes)
            val onColor = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.82f) else Color.White
            // Animating the number keeps a refresh from looking like a glitch when a
            // wait jumps 20 minutes between polls.
            val animated by animateFloatAsState(minutes.toFloat(), label = "wait")
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = animated.toInt().toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onColor,
                    )
                    Text(
                        text = "m",
                        style = MaterialTheme.typography.labelSmall,
                        color = onColor.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Surface(
        modifier = Modifier.size(width = 56.dp, height = 44.dp),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.16f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/**
 * The extra queues, in the order they matter. Universal Express and single rider are the
 * two that actually change what you do next; the Lightning Lane return window matters
 * only when it is actually available.
 */
private fun ParkEntity.secondaryLine(): String? {
    val parts = buildList {
        queues.forEach { queue ->
            when (queue) {
                is Queue.SingleRider -> queue.waitMinutes?.let { add("Single rider $it min") }
                is Queue.PaidStandby -> queue.waitMinutes?.let { add("Express $it min") }
                is Queue.ReturnTime -> queue.start?.let { add("Lightning Lane ${it.toParkClockTime()}") }
                is Queue.PaidReturnTime -> {
                    val price = queue.price
                    queue.start?.let { add("Paid LL ${it.toParkClockTime()}${price?.let { p -> " · $p" }.orEmpty()}") }
                }
                is Queue.BoardingGroup -> {
                    val start = queue.currentStart
                    val end = queue.currentEnd
                    if (start != null && end != null) add("Boarding groups $start–$end")
                }
                is Queue.Standby -> Unit // already the badge
            }
        }
        showtimes.mapNotNull { it.start }.take(4).map { it.toParkClockTime() }
            .takeIf { it.isNotEmpty() }
            ?.let { add(it.joinToString(" · ")) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
