package contact.kaufman.parks.ui.park

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.location.LocationProvider
import contact.kaufman.parks.data.prefs.SettingsStore
import contact.kaufman.parks.data.prefs.TemperatureUnit
import contact.kaufman.parks.data.repo.ParksRepository
import contact.kaufman.parks.data.repo.TripRepository
import contact.kaufman.parks.domain.EntityKind
import contact.kaufman.parks.domain.Geo
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkEntity
import contact.kaufman.parks.domain.ParkLands
import contact.kaufman.parks.domain.ParkSnapshot
import contact.kaufman.parks.domain.ParkWeather
import contact.kaufman.parks.domain.distanceMetersFrom
import contact.kaufman.parks.domain.land
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ParkTab(val label: String) {
    RIDES("Rides"),
    SHOWS("Shows"),
    DINING("Dining"),
}

enum class RideSort(val label: String) {
    WAIT("Wait"),
    NAME("A–Z"),
    NEARBY("Nearby"),
}

/**
 * A fix taken **inside** the park being viewed.
 *
 * The type exists to make that precondition hard to lose. Ride distances are only
 * meaningful from inside the park; measured from home they would order every ride by a
 * gradient pointing at the front gate, which looks like a working feature and is not one.
 */
data class InParkFix(val latitude: Double, val longitude: Double)

data class ParkDetailUiState(
    val snapshot: ParkSnapshot? = null,
    val tab: ParkTab = ParkTab.RIDES,
    val sort: RideSort = RideSort.WAIT,
    val hideClosed: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val weather: ParkWeather? = null,
    val isLoadingWeather: Boolean = false,
    val fix: InParkFix? = null,
    /** null means "every land" — the default, and the only option where none are mapped. */
    val land: String? = null,
) {
    /**
     * Sorting by distance is only offered once there is a fix inside this park, so the
     * control never appears in a state where it cannot do anything. Nothing here asks for
     * permission: if location is off, the chip simply is not there.
     */
    fun availableSorts(): List<RideSort> =
        RideSort.entries.filter { it != RideSort.NEARBY || fix != null }

    /**
     * The lands to offer, or empty where none are mapped.
     *
     * Empty means the filter row is not drawn at all. Islands of Adventure taught the
     * lesson in reverse — it looked unmapped until relations were read properly — but the
     * case is real for any park OSM has not covered, and an empty row of chips is worse
     * than no row.
     */
    fun availableLands(): List<String> =
        snapshot?.park?.let(ParkLands::landsIn).orEmpty()
}

@HiltViewModel(assistedFactory = ParkDetailViewModel.Factory::class)
class ParkDetailViewModel @AssistedInject constructor(
    @Assisted private val park: Park,
    private val repository: ParksRepository,
    private val location: LocationProvider,
    private val trips: TripRepository,
    settings: SettingsStore,
) : ViewModel() {

    val temperatureUnit: StateFlow<TemperatureUnit> = settings.settings
        .map { it.temperatureUnit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemperatureUnit.FAHRENHEIT)

    @AssistedFactory
    interface Factory {
        fun create(park: Park): ParkDetailViewModel
    }

    private val _state = MutableStateFlow(ParkDetailUiState())
    val state: StateFlow<ParkDetailUiState> = _state.asStateFlow()

    init {
        // Show whatever the dashboard already fetched immediately, then refresh behind it.
        repository.cached(park)?.let { cached ->
            _state.update { it.copy(snapshot = cached, weather = cached.weather, isLoading = false) }
        }
        load(force = false)
        // Opening a park screen is the intent to see its weather; making it a second tap
        // was needless. The repository throttles the actual fetch.
        loadWeather()
        locate()
    }

    fun refresh() = load(force = true)

    suspend fun waitHistory(attractionId: String) = repository.waitHistory(attractionId)

    fun selectTab(tab: ParkTab) = _state.update { it.copy(tab = tab) }

    fun selectSort(sort: RideSort) = _state.update { it.copy(sort = sort) }

    /** Passing the land already selected clears it, so a second tap means "all lands". */
    fun selectLand(land: String?) = _state.update {
        it.copy(land = if (it.land == land) null else land)
    }

    fun toggleHideClosed() = _state.update { it.copy(hideClosed = !it.hideClosed) }

    /**
     * Weather is a tap, not a subscription. Zak has a dedicated weather app; this exists
     * for "is it about to rain on me in EPCOT", so it is never fetched automatically.
     */
    fun loadWeather() {
        if (_state.value.isLoadingWeather) return
        if (_state.value.weather != null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingWeather = true) }
            val weather = repository.weather(park)
            _state.update { it.copy(weather = weather, isLoadingWeather = false) }
        }
    }

    /**
     * One fix, on open, used only to decide whether "Nearby" can be offered and to measure
     * from. Discarded unless it lands inside this park.
     *
     * `Geo.parkAt` does the deciding, so the radius and the nearest-centre rule stay in one
     * tested place. Its one soft spot applies here too: Universal Studios and Islands of
     * Adventure sit under a kilometre apart, so a poor fix near their shared wall can pick
     * the sibling and this screen will quietly not offer the sort. Not offering it is the
     * right failure — the alternative is distances measured from the wrong park.
     */
    private fun locate() {
        if (!location.hasPermission()) return
        viewModelScope.launch {
            val fix = location.current()?.takeIf { Geo.parkAt(it.latitude, it.longitude) == park }
            _state.update { it.copy(fix = fix?.let { f -> InParkFix(f.latitude, f.longitude) }) }
            // Opening a park's screen while standing in it is evidence of a visit, and
            // often the only evidence on a day spent inside one park without ever
            // returning to the dashboard.
            if (fix != null) trips.noteSighting(park)
        }
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = force) }
            val snapshot = repository.refresh(park, force = force)
            _state.update {
                it.copy(
                    snapshot = snapshot,
                    weather = snapshot.weather ?: it.weather,
                    isLoading = false,
                    isRefreshing = false,
                )
            }
        }
    }
}

/** The list the Rides tab actually renders, after sorting and filtering. */
fun ParkDetailUiState.visibleEntities(): List<ParkEntity> {
    val all = snapshot?.entities.orEmpty()
    val kind = when (tab) {
        ParkTab.RIDES -> EntityKind.ATTRACTION
        ParkTab.SHOWS -> EntityKind.SHOW
        ParkTab.DINING -> EntityKind.RESTAURANT
    }
    val filtered = all.filter { it.kind == kind }
        .filter { !hideClosed || it.isOperating }
        // Land is a ride-list idea. Shows and restaurants are short enough lists to read
        // whole, and their coordinates are patchier.
        .filter { land == null || tab != ParkTab.RIDES || it.land() == land }

    return when {
        tab != ParkTab.RIDES -> filtered.sortedBy { it.name }
        sort == RideSort.NAME -> filtered.sortedBy { it.name }
        // Guarded on the fix rather than the sort alone: the chip disappears if the fix is
        // lost, and falling back to the wait order beats rendering an arbitrary one.
        sort == RideSort.NEARBY && fix != null -> filtered.sortedBy {
            // Entities with no coordinates sink to the bottom, the same way a ride with no
            // posted wait does, rather than pretending to be nought feet away.
            it.distanceMetersFrom(fix.latitude, fix.longitude) ?: Double.MAX_VALUE
        }
        // Longest wait first, and rides with no posted wait sink to the bottom rather
        // than being treated as a zero-minute wait.
        else -> filtered.sortedByDescending { it.standbyMinutes ?: -1 }
    }
}
