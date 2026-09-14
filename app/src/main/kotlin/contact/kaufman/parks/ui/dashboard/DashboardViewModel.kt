package contact.kaufman.parks.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.data.repo.ParkingRepository
import contact.kaufman.parks.data.location.LocationProvider
import contact.kaufman.parks.data.prefs.SettingsStore
import contact.kaufman.parks.data.prefs.TemperatureUnit
import contact.kaufman.parks.data.repo.ParksRepository
import contact.kaufman.parks.domain.Geo
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkSnapshot
import contact.kaufman.parks.domain.ParkWeather
import contact.kaufman.parks.domain.Resort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val resortWeather: ParkWeather? = null,
    /** The park you appear to be standing in, if location is available and you are. */
    val youAreHere: Park? = null,
    val snapshots: List<ParkSnapshot> = Park.entries.map { ParkSnapshot(it) },
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Set only when every park failed — a single park's error lives on its own card. */
    val error: String? = null,
)

private const val TAG = "DashboardViewModel"

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val parks: ParksRepository,
    private val parkingRepository: ParkingRepository,
    private val location: LocationProvider,
    settings: SettingsStore,
) : ViewModel() {

    /** Which parks the dashboard shows. Hidden parks are still fetched — a refresh is
     *  one batched call either way, and unhiding one should not mean waiting for it. */
    val temperatureUnit: StateFlow<TemperatureUnit> = settings.settings
        .map { it.temperatureUnit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemperatureUnit.FAHRENHEIT)

    val visibleParks: StateFlow<List<Park>> = settings.settings
        .map { it.visibleParks() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Park.entries)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    val activeParking: StateFlow<ParkingRecordEntity?> = parkingRepository.active()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Yesterday's spot should not still be pinned this morning.
        viewModelScope.launch { parkingRepository.expireStale() }
        load(force = false)
        loadResortWeather()
        detectPark()
    }

    fun hasLocationPermission(): Boolean = location.hasPermission()

    /**
     * Works out which park you are in, once, on open.
     *
     * Silent when permission is missing or location is off — this is a convenience that
     * saves a tap, never something to nag about.
     */
    fun detectPark() {
        viewModelScope.launch {
            val fix = location.current()
            if (fix == null) {
                Log.d(TAG, "no location fix (permission off, location off, or no provider)")
                return@launch
            }
            val park = Geo.parkAt(fix.latitude, fix.longitude)
            // Worth keeping: the difference between "no fix" and "a fix, but nowhere near
            // a park" is the first thing to check when this feature seems dead, and the
            // two look identical from the UI.
            Log.d(TAG, "fix ${fix.latitude},${fix.longitude} (${fix.provider}) -> ${park?.displayName ?: "no park"}")
            _state.value = _state.value.copy(youAreHere = park)
            // The header should report the weather where you actually are. Standing at
            // Islands of Adventure while it reads Walt Disney World is just wrong.
            if (park != null && park.resort != Resort.WALT_DISNEY_WORLD) {
                loadResortWeather(park.resort)
            }
        }
    }

    /**
     * Resort weather for the header. Fetched once when the dashboard appears and then
     * served from the repository's 15-minute cache — deliberately not a live feed, since
     * Zak has a dedicated weather app for that.
     */
    private fun loadResortWeather(resort: Resort = Resort.WALT_DISNEY_WORLD) {
        viewModelScope.launch {
            val weather = parks.resortWeather(resort) ?: return@launch
            _state.value = _state.value.copy(resortWeather = weather)
        }
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = _state.value.snapshots.none { it.fetchedAt != null },
                isRefreshing = force,
            )
            val snapshots = parks.refreshAll(force = force)
            _state.value = _state.value.copy(
                snapshots = snapshots,
                isLoading = false,
                isRefreshing = false,
                error = if (snapshots.all { it.fetchedAt == null }) {
                    snapshots.firstNotNullOfOrNull { it.error } ?: "Couldn't reach themeparks.wiki"
                } else {
                    null
                },
            )
        }
    }
}
