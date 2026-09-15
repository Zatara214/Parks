package contact.kaufman.parks.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.repo.TripRepository
import contact.kaufman.parks.domain.TripDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripsUiState(
    val days: List<TripDay> = emptyList(),
    /** Until the first read finishes, an empty list and "nothing here" look the same. */
    val isLoading: Boolean = true,
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val repository: TripRepository,
) : ViewModel() {

    val state: StateFlow<TripsUiState> = repository.days()
        .map { TripsUiState(days = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    fun forget(day: TripDay) {
        viewModelScope.launch { repository.forget(day) }
    }
}
