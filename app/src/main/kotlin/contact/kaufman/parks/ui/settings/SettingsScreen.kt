package contact.kaufman.parks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import contact.kaufman.parks.data.prefs.TemperatureUnit
import contact.kaufman.parks.data.prefs.ThemeMode
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.Resort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "appearance") { SectionHeader("Appearance") }

            item(key = "dynamic") {
                ListItem(
                    headlineContent = { Text("Dynamic colour") },
                    supportingContent = { Text("Take the palette from your wallpaper") },
                    trailingContent = {
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    },
                )
            }

            item(key = "theme") {
                ChipRow(
                    label = "Theme",
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    optionLabel = { it.label },
                    onSelect = viewModel::setThemeMode,
                )
            }

            item(key = "units-header") { SectionHeader("Units") }

            item(key = "units") {
                ChipRow(
                    label = "Temperature",
                    options = TemperatureUnit.entries,
                    selected = settings.temperatureUnit,
                    optionLabel = { if (it == TemperatureUnit.FAHRENHEIT) "Fahrenheit" else "Celsius" },
                    onSelect = viewModel::setTemperatureUnit,
                )
            }

            item(key = "parks-header") { SectionHeader("Parks on the dashboard") }

            Resort.entries.forEach { resort ->
                item(key = "resort-${resort.name}") {
                    Text(
                        text = resort.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                Park.entries.filter { it.resort == resort }.forEach { park ->
                    item(key = "park-${park.name}") {
                        val visible = park !in settings.hiddenParks
                        ListItem(
                            headlineContent = { Text(park.displayName) },
                            trailingContent = {
                                Switch(
                                    checked = visible,
                                    onCheckedChange = { viewModel.setParkVisible(park, it) },
                                )
                            },
                        )
                    }
                }
            }

            item(key = "about-header") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("About")
            }

            item(key = "about") {
                ListItem(
                    headlineContent = { Text("Open-source licences") },
                    supportingContent = { Text("What Parks is built on, and where its data comes from") },
                    modifier = Modifier.clickable(onClick = onAboutClick),
                )
            }

            item(key = "credit") {
                Text(
                    text = "Park data from themeparks.wiki · Weather from Open-Meteo\n" +
                        "Crowd levels follow the method published by WDW Passport.\n\n" +
                        "Not affiliated with The Walt Disney Company or NBCUniversal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}
