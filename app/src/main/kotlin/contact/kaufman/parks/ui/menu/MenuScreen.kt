package contact.kaufman.parks.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import contact.kaufman.parks.domain.MenuItem
import contact.kaufman.parks.domain.Resort
import contact.kaufman.parks.domain.RestaurantMenu
import contact.kaufman.parks.ui.components.OfficialApps
import contact.kaufman.parks.ui.components.ParkTimeZone
import contact.kaufman.parks.ui.components.toParkClockTime
import contact.kaufman.parks.ui.navigation.MenuKey
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One restaurant's menu and prices, as Disney last published them to this phone.
 *
 * The screen says where the numbers came from and how old they are, because it cannot
 * promise they are current: Disney reprices, and a copy checked yesterday is yesterday's.
 * Ordering is not here and cannot be — it needs a Disney account — so the top bar hands
 * off to the official app, which is where mobile order actually lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    key: MenuKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MenuViewModel = hiltViewModel<MenuViewModel, MenuViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val menu = state.menu

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = menu?.restaurantName ?: key.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { OfficialApps.open(context, Resort.WALT_DISNEY_WORLD) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Order in ${OfficialApps.appName(Resort.WALT_DISNEY_WORLD)}",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                menu != null -> MenuList(
                    menu = menu,
                    selectedPeriod = state.selectedPeriod,
                    error = state.error,
                    onSelectPeriod = viewModel::selectPeriod,
                )
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                state.noMenu -> Message(
                    title = "No menu online for this one",
                    body = "Disney doesn't publish a menu for it that this app can find. " +
                        "Kiosks and carts often have none.",
                    onOpenApp = { OfficialApps.open(context, Resort.WALT_DISNEY_WORLD) },
                )
                else -> Message(
                    title = "Couldn't load the menu",
                    body = (state.error ?: "Something went wrong.") + " Pull down to try again.",
                    onOpenApp = { OfficialApps.open(context, Resort.WALT_DISNEY_WORLD) },
                )
            }
        }
    }
}

@Composable
private fun MenuList(
    menu: RestaurantMenu,
    selectedPeriod: Int,
    error: String?,
    onSelectPeriod: (Int) -> Unit,
) {
    val period = menu.periods.getOrNull(selectedPeriod) ?: menu.periods.first()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // One meal needs no chooser; a row with a single chip in it reads as a control
        // that does nothing.
        if (menu.periods.size > 1) {
            item(key = "periods") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    itemsIndexed(menu.periods) { index, meal ->
                        FilterChip(
                            selected = meal === period,
                            onClick = { onSelectPeriod(index) },
                            label = { Text(meal.name) },
                        )
                    }
                }
            }
        }

        // A menu on screen with a failed check behind it is still worth reading, so the
        // failure is a line above it rather than a replacement for it.
        if (error != null) {
            item(key = "stale") {
                Text(
                    text = "$error Showing the copy ${checkedPhrase(menu.fetchedAt)}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        // Keyed by position, not by name: Disney repeats item and group names within a
        // meal, and a duplicate key is a crash in a lazy list.
        period.sections.forEachIndexed { sectionIndex, section ->
            if (section.name.isNotEmpty()) {
                item(key = "s$sectionIndex") {
                    Text(
                        text = section.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            section.items.forEachIndexed { itemIndex, item ->
                item(key = "s$sectionIndex-i$itemIndex") { ItemRow(item) }
            }
        }

        item(key = "source") {
            Text(
                text = "Prices from Disney, before tax. Checked ${checkedPhrase(menu.fetchedAt)}. " +
                    "Pull down to check for changes.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun ItemRow(item: MenuItem) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = if (item.description == null) 10.dp else 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            // No price is left blank. "$0.00" or "Free" would be a claim Disney never made.
            item.price?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        item.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
}

/** Scrollable even when it is one message, or pull-to-refresh has nothing to pull. */
@Composable
private fun Message(title: String, body: String, onOpenApp: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(title, style = MaterialTheme.typography.titleMedium) }
        item {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            FilledTonalButton(onClick = onOpenApp) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open ${OfficialApps.appName(Resort.WALT_DISNEY_WORLD)}")
            }
        }
    }
}

/** "today at 4:02 PM", "yesterday at 9:15 AM", "on Sep 21" — in park time. */
private fun checkedPhrase(at: Instant): String {
    val today = Clock.System.now().toLocalDateTime(ParkTimeZone).date
    val day = at.toLocalDateTime(ParkTimeZone).date
    return when (day) {
        today -> "today at ${at.toParkClockTime()}"
        today.minus(1, DateTimeUnit.DAY) -> "yesterday at ${at.toParkClockTime()}"
        else -> "on ${day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${day.day}"
    }
}
