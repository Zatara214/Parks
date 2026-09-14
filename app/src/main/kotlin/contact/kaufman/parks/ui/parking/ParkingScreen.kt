package contact.kaufman.parks.ui.parking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.ui.components.toParkClockTime
import kotlin.time.Instant

/**
 * Record where the car is.
 *
 * Lot and row are free text on purpose. Disney rows are "Heroes 12", Universal's are
 * "Jaws, Level 4", and Epic Universe is different again — forcing a schema on that would
 * make it slower to enter than taking a photo, which defeats the point.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingScreen(
    initialPark: Park?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ParkingViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    LaunchedEffect(initialPark) { viewModel.setPark(initialPark) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Parking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "active") {
                AnimatedVisibility(
                    visible = active != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.94f),
                    exit = fadeOut() + scaleOut(targetScale = 0.94f),
                ) {
                    active?.let { record ->
                        ActiveSpotCard(record, onClear = viewModel::clearActive)
                    }
                }
            }

            // The whole grid is one item: as separate items each row would inherit the
            // list's 12dp spacing and the chips would drift apart.
            item(key = "park-picker") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Park", style = MaterialTheme.typography.titleSmall)
                    Park.entries.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { park ->
                                FilterChip(
                                    selected = form.park == park,
                                    onClick = { viewModel.setPark(park) },
                                    label = { Text(park.shortName) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            item(key = "form") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.lot,
                        onValueChange = viewModel::setLot,
                        label = { Text("Lot or area") },
                        placeholder = { Text("Heroes, Jaws, Terminal C…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.row,
                        onValueChange = viewModel::setRow,
                        label = { Text("Row or level") },
                        placeholder = { Text("12, Level 4…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = viewModel::setNote,
                        label = { Text("Note (optional)") },
                        placeholder = { Text("Near the tram stop") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::save,
                        enabled = form.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (active != null) "Replace parking spot" else "Save parking spot")
                    }
                }
            }

            val past = history.filterNot { it.isActive }
            if (past.isNotEmpty()) {
                item(key = "history-header") {
                    Text("Earlier", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }
                items(past, key = { it.id }) { record ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(record.describeSpot(), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = record.describeWhen(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { viewModel.delete(record.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSpotCard(
    record: contact.kaufman.parks.data.db.ParkingRecordEntity,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("You parked at", style = MaterialTheme.typography.labelMedium)
            Text(
                text = record.describeSpot(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(record.describeWhen(), style = MaterialTheme.typography.labelMedium)
            if (record.note.isNotBlank()) {
                Text(record.note, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 4.dp)) {
                Text("I've left the park")
            }
        }
    }
}

private fun contact.kaufman.parks.data.db.ParkingRecordEntity.describeSpot(): String {
    val place = listOf(lot, row).filter { it.isNotBlank() }.joinToString(" ")
    val park = Park.fromId(parkId)?.shortName
    return listOfNotNull(place.takeIf { it.isNotBlank() }, park?.let { "($it)" }).joinToString(" ")
}

private fun contact.kaufman.parks.data.db.ParkingRecordEntity.describeWhen(): String =
    Instant.fromEpochSeconds(parkedAtEpochSeconds).toParkClockTime()
