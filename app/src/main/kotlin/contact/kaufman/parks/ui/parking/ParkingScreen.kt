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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkingLots
import contact.kaufman.parks.ui.components.toParkClockTime
import kotlin.time.Instant

/**
 * Record where the car is.
 *
 * Lot names come from [ParkingLots] so a spot is two taps rather than a spelling test,
 * but every field stays editable: a table of section names goes stale the moment a resort
 * renames a lot, and a stale table must never block recording where you actually parked.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val recentRows by viewModel.recentRows.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

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
                    active?.let { record -> ActiveSpotCard(record, onClear = viewModel::clearActive) }
                }
            }

            item(key = "park-picker") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Park", style = MaterialTheme.typography.titleSmall)
                    // Full names: "EP" is unreadable when you are tired and holding a churro.
                    Park.entries.forEach { park ->
                        FilterChip(
                            selected = form.park == park,
                            onClick = { viewModel.setPark(park) },
                            label = { Text(park.displayName) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            val groups = viewModel.lotGroups(form.park)
            if (groups.isNotEmpty()) {
                item(key = "lot-picker") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Section", style = MaterialTheme.typography.titleSmall)
                        groups.forEach { group ->
                            group.name?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.lots.forEach { lot ->
                                    FilterChip(
                                        selected = form.lot.equals(lot, ignoreCase = true),
                                        onClick = { viewModel.setLot(lot) },
                                        label = { Text(lot) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "form") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.lot,
                        onValueChange = viewModel::setLot,
                        label = { Text("Section") },
                        placeholder = { Text("Or type one") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (viewModel.hasLevels(form.park)) {
                        Text(
                            text = "Level",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ParkingLots.GARAGE_LEVELS.forEach { level ->
                                FilterChip(
                                    selected = form.level == level,
                                    onClick = { viewModel.setLevel(level) },
                                    label = { Text(level) },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = form.row,
                        onValueChange = viewModel::setRow,
                        label = { Text("Row") },
                        placeholder = { Text("201") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Disney publishes lot names but not row ranges, so there is no honest
                    // fixed list to offer. Remembering what was actually used makes the
                    // repeat case one tap without inventing rows that may not exist.
                    if (recentRows.isNotEmpty()) {
                        Text(
                            text = "Rows you've used in ${form.lot}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentRows.forEach { row ->
                                FilterChip(
                                    selected = form.signRow() == row,
                                    onClick = { viewModel.setRow(row) },
                                    label = { Text(row) },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = form.note,
                        onValueChange = viewModel::setNote,
                        label = { Text("Note (optional)") },
                        placeholder = { Text("Near the tram stop") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (form.canSave) {
                        Text(
                            text = "Saving: ${form.lot.ifBlank { "—" }} ${form.signRow()}".trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Button(
                        onClick = {
                            // A focused text field keeps painting its own IME buffer, so
                            // clearing the form underneath leaves the old row visible over
                            // empty state — it looks entered, and saving again records
                            // nothing. Ending the input session makes the field re-read.
                            focusManager.clearFocus()
                            keyboard?.hide()
                            viewModel.save()
                        },
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
private fun ActiveSpotCard(record: ParkingRecordEntity, onClear: () -> Unit) {
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

private fun ParkingRecordEntity.describeSpot(): String {
    val place = listOf(lot, row).filter { it.isNotBlank() }.joinToString(" ")
    val park = Park.fromId(parkId)?.displayName
    return listOfNotNull(place.takeIf { it.isNotBlank() }, park?.let { "($it)" }).joinToString(" ")
}

private fun ParkingRecordEntity.describeWhen(): String =
    Instant.fromEpochSeconds(parkedAtEpochSeconds).toParkClockTime()
