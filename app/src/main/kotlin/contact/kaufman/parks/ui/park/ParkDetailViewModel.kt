package contact.kaufman.parks.ui.park

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.repo.ParksRepository
import contact.kaufman.parks.domain.EntityKind
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkEntity
import contact.kaufman.parks.domain.ParkSnapshot
import contact.kaufman.parks.domain.ParkWeather
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}

data class ParkDetailUiState(
    val snapshot: ParkSnapshot? = null,
    val tab: ParkTab = ParkTab.RIDES,
    val sort: RideSort = RideSort.WAIT,
    val hideClosed: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val weather: ParkWeather? = null,
    val isLoadingWeather: Boolean = false,
)

@HiltViewModel(assistedFactory = ParkDetailViewModel.Factory::class)
class ParkDetailViewModel @AssistedInject constructor(
    @Assisted private val park: Park,
    private val repository: ParksRepository,
) : ViewModel() {

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
    }

    fun refresh() = load(force = true)

    fun selectTab(tab: ParkTab) = _state.update { it.copy(tab = tab) }

    fun selectSort(sort: RideSort) = _state.update { it.copy(sort = sort) }

    fun toggleHideClosed() = _state.update { it.copy(hideClosed = !it.hideClosed) }

    /**
     * Weather is a tap, not a subscription. Zak has a dedicated weather app; this exists
     * for "is it about to rain on me in EPCOT", so it is never fetched automatically.
     */
    fun loadWeather() {
        if (_state.value.isLoadingWeather) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingWeather = true) }
            val weather = repository.weather(park)
            _state.update { it.copy(weather = weather, isLoadingWeather = false) }
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

    return when {
        tab != ParkTab.RIDES -> filtered.sortedBy { it.name }
        sort == RideSort.NAME -> filtered.sortedBy { it.name }
        // Longest wait first, and rides with no posted wait sink to the bottom rather
        // than being treated as a zero-minute wait.
        else -> filtered.sortedByDescending { it.standbyMinutes ?: -1 }
    }
}
