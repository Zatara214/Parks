package contact.kaufman.parks.ui.parking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.data.repo.ParkingRepository
import contact.kaufman.parks.domain.Park
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParkingFormState(
    val park: Park? = null,
    val lot: String = "",
    val row: String = "",
    val note: String = "",
    val saved: Boolean = false,
) {
    /** A spot with neither a lot nor a row records nothing useful. */
    val canSave: Boolean get() = park != null && (lot.isNotBlank() || row.isNotBlank())
}

@HiltViewModel
class ParkingViewModel @Inject constructor(
    private val repository: ParkingRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(ParkingFormState())
    val form: StateFlow<ParkingFormState> = _form.asStateFlow()

    val active: StateFlow<ParkingRecordEntity?> = repository.active()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<ParkingRecordEntity>> = repository.history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setPark(park: Park?) = _form.update { it.copy(park = park ?: it.park) }

    fun setLot(value: String) = _form.update { it.copy(lot = value, saved = false) }

    fun setRow(value: String) = _form.update { it.copy(row = value, saved = false) }

    fun setNote(value: String) = _form.update { it.copy(note = value, saved = false) }

    fun save() {
        val form = _form.value
        val park = form.park ?: return
        if (!form.canSave) return
        viewModelScope.launch {
            repository.park(park = park, lot = form.lot, row = form.row, note = form.note)
            _form.update { it.copy(lot = "", row = "", note = "", saved = true) }
        }
    }

    /** Leaving the park. The record stays in history; it just stops being the current one. */
    fun clearActive() {
        viewModelScope.launch { repository.clearActive() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
