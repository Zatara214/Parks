package contact.kaufman.parks.ui.parking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkingLots
import contact.kaufman.parks.domain.Resort
import contact.kaufman.parks.ui.components.toParkClockTime
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Record where the car is.
 *
 * Everything selectable is a fused button group, because every step here is a
 * pick-exactly-one: which park, which section, which level. There is no free-text section
 * field — the posted names are all that exist, and typing one was only ever a worse way to
 * say the same thing. Anything genuinely unusual goes in the note.
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rowRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(initialPark) { viewModel.setPark(initialPark) }
    // One fix, when the screen opens. Consistent with the rest of the app: location is
    // asked for by a screen that needs it and never followed in the background.
    LaunchedEffect(Unit) { viewModel.detectSpot() }

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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Without this the keyboard draws straight over the row field under
                // edge-to-edge — `adjustResize` alone does not resize a Compose window.
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

            item(key = "location-assist") {
                LocationAssist(form = form, onRetry = viewModel::detectSpot)
            }

            item(key = "park-picker") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Park")
                    Resort.entries.forEach { resort ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = resort.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            // Two per row: four full park names will not fit across a
                            // phone, and shortening them is what made this awkward before.
                            Park.entries.filter { it.resort == resort }.chunked(2).forEach { pair ->
                                FusedToggleRow(
                                    options = pair,
                                    selected = form.park,
                                    label = { it.pickerLabel() },
                                    onSelect = viewModel::setPark,
                                )
                            }
                        }
                    }
                }
            }

            val groups = viewModel.lotGroups(form.park)
            if (groups.isNotEmpty()) {
                item(key = "lot-picker") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Section")
                        groups.forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                group.name?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                group.lots.balancedRows().forEach { row ->
                                    FusedToggleRow(
                                        options = row,
                                        selected = form.lot.takeIf { it.isNotBlank() },
                                        label = { it },
                                        onSelect = viewModel::setLot,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (viewModel.hasLevels(form.park)) {
                item(key = "level-picker") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("Level")
                        FusedToggleRow(
                            options = ParkingLots.GARAGE_LEVELS,
                            selected = form.level.takeIf { it.isNotBlank() },
                            label = { it },
                            onSelect = viewModel::setLevel,
                        )
                    }
                }
            }

            item(key = "row") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Row")
                    OutlinedTextField(
                        value = form.row,
                        onValueChange = viewModel::setRow,
                        placeholder = { Text("201") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(rowRequester)
                            // Scroll the field clear of the keyboard as it opens, rather
                            // than leaving it behind the IME.
                            .onFocusChanged { focus ->
                                if (focus.isFocused) scope.launch { rowRequester.bringIntoView() }
                            },
                    )

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
                }
            }

            item(key = "note") {
                OutlinedTextField(
                    value = form.note,
                    onValueChange = viewModel::setNote,
                    label = { Text("Note (optional)") },
                    placeholder = { Text("Near the tram stop") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "save") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(
                        visible = form.canSave,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Text(
                            text = "Saving: ${listOf(form.lot, form.signRow()).filter { it.isNotBlank() }.joinToString(" ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Button(
                        onClick = {
                            // A focused text field keeps painting its own IME buffer, so
                            // clearing the form underneath leaves the old row visible over
                            // empty state. Ending the session makes the field re-read.
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
                item(key = "history-header") { SectionLabel("Earlier") }
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
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

/** The resort header already says which resort, so the park name does not repeat it. */
private fun Park.pickerLabel(): String = when (this) {
    Park.UNIVERSAL_STUDIOS_FLORIDA -> "Universal Studios"
    else -> displayName
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

/**
 * What the GPS fix managed to work out, and nothing more.
 *
 * This says what was filled in rather than filling it in silently. A prefilled picker with
 * no explanation looks like the app remembering the last visit, and the difference matters
 * when it is wrong — knowing it came from a fix is what tells you to check it.
 *
 * It stays absent until there is something to report, so the common case (a Disney lot,
 * correctly identified) is one quiet line rather than a panel.
 */
@Composable
private fun LocationAssist(form: ParkingFormState, onRetry: () -> Unit) {
    val area = form.detected
    val message = when {
        form.locating -> "Checking where you are…"
        area?.lot != null -> "From your location: ${area.lot}"
        // Both Universal garages serve both parks, so this is as far as a fix can honestly
        // go. Naming the garage is still most of the walk back.
        area?.group != null -> "You're in the ${area.group} — which park?"
        area?.park != null -> "You're at ${area.park.displayName}"
        form.detectionMissed -> "Couldn't tell which lot you're in"
        else -> return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.MyLocation,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (form.detectionMissed) {
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}
