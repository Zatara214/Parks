package contact.kaufman.parks.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
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
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.domain.Park

/**
 * The parked-car reminder, pinned above every park.
 *
 * When nothing is recorded this is a quiet prompt rather than a blank space — the moment
 * you need it is on the way in, not on the way out.
 */
@Composable
fun ParkingPin(
    record: ParkingRecordEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (record != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = if (record != null) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                if (record != null) {
                    Text("Parked at", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = record.spotLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text("Record your parking spot", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun ParkingRecordEntity.spotLabel(): String {
    val place = listOf(lot, row).filter { it.isNotBlank() }.joinToString(" ")
    val park = Park.fromId(parkId)?.shortName
    return listOfNotNull(place.takeIf { it.isNotBlank() }, park?.let { "· $it" }).joinToString(" ")
}
