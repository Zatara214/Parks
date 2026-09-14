package contact.kaufman.parks.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.data.repo.ParkingRepository
import contact.kaufman.parks.data.prefs.SettingsStore
import contact.kaufman.parks.data.repo.ParksRepository
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkSnapshot
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
    val snapshots: List<ParkSnapshot> = Park.entries.map { ParkSnapshot(it) },
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Set only when every park failed — a single park's error lives on its own card. */
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val parks: ParksRepository,
    private val parkingRepository: ParkingRepository,
    settings: SettingsStore,
) : ViewModel() {

    /** Which parks the dashboard shows. Hidden parks are still fetched — a refresh is
     *  one batched call either way, and unhiding one should not mean waiting for it. */
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
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = _state.value.snapshots.none { it.fetchedAt != null },
                isRefreshing = force,
            )
            val snapshots = parks.refreshAll(force = force)
            _state.value = DashboardUiState(
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
